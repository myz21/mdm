package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.dd.plist.NSData;
import com.dd.plist.NSDictionary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.arcyintel.arcops.apple_mdm.domains.*;
import com.arcyintel.arcops.apple_mdm.event.publisher.AccountEventPublisher;
import com.arcyintel.arcops.apple_mdm.event.publisher.DeviceEventPublisher;
import com.arcyintel.arcops.apple_mdm.repositories.*;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandSenderService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AppleCheckinService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AppleEnrollmentWebAuthService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AccountResolutionContext;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AccountResolverRegistry;
import com.arcyintel.arcops.commons.constants.apple.Os;
import com.arcyintel.arcops.commons.events.account.AccountCreatedEvent;
import com.arcyintel.arcops.commons.events.device.DeviceDisenrolledEvent;
import com.arcyintel.arcops.commons.events.device.DeviceEnrolledEvent;
import com.arcyintel.arcops.commons.exceptions.BusinessException;
import com.arcyintel.arcops.commons.license.LicenseContext;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.arcyintel.arcops.commons.constants.apple.CommandSpecificConfigurations.DECLARATIVE_MANAGEMENT;
import static com.arcyintel.arcops.commons.constants.policy.PolicyTypes.PAYLOAD;

@Service
@RequiredArgsConstructor
@Primary
public class AppleCheckinServiceImpl implements AppleCheckinService {
    private static final Logger logger = LoggerFactory.getLogger(AppleCheckinServiceImpl.class);

    private final AppleDeviceRepository appleDeviceRepository;
    private final AbmDeviceRepository abmDeviceRepository;
    private final AppleAccountRepository appleAccountRepository;
    private final DeviceEventPublisher deviceEventPublisher;
    private final AppleCommandSenderService commandSenderService;
    private final AppleEnrollmentWebAuthService enrollmentWebAuthService;
    private final AccountResolverRegistry accountResolverRegistry;
    private final AppleIdentityRepository appleIdentityRepository;
    private final AccountEventPublisher accountEventPublisher;
    private final EnrollmentHistoryRepository enrollmentHistoryRepository;
    private final DeviceAuthHistoryRepository deviceAuthHistoryRepository;
    private final AppleDeviceInformationRepository appleDeviceInformationRepository;
    private final AppleCommandRepository appleCommandRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final org.springframework.beans.factory.ObjectProvider<LicenseContext> licenseContextProvider;
    private final DeviceCleanupService deviceCleanupService;

    @Transactional
    public AppleDevice authenticate(NSDictionary dict, String orgMagic) {

        logger.info("Received an Authenticate request from an Apple device. Request details: {}", dict);

        // Extract identifiers - User Enrollment uses EnrollmentID instead of UDID
        String udid = getString(dict, "UDID");
        String enrollmentId = getString(dict, "EnrollmentID");
        String enrollmentUserId = getString(dict, "EnrollmentUserID");

        // Determine if this is a User Enrollment (BYOD) device
        boolean isUserEnrollment = enrollmentId != null && !enrollmentId.isBlank();

        if (isUserEnrollment) {
            logger.info("User Enrollment detected. EnrollmentID: {}, EnrollmentUserID: {}", enrollmentId, enrollmentUserId);
        }

        // For User Enrollment, EnrollmentID is used; for regular enrollment, UDID is used
        String deviceIdentifier = isUserEnrollment ? enrollmentId : udid;

        if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
            logger.error("Device identifier (UDID or EnrollmentID) is missing in the Authenticate request. Request details: {}", dict);
            return null;
        }
        logger.debug("Device identifier: {} (isUserEnrollment: {})", deviceIdentifier, isUserEnrollment);

        // Resolve orgMagic early (consumes the token from Redis — one-time use)
        UUID resolvedIdentityId = null;
        if (orgMagic != null && !orgMagic.isBlank()) {
            logger.info("orgMagic found in CheckInURL query param: {}", orgMagic);
            resolvedIdentityId = enrollmentWebAuthService.resolveEnrollmentToken(orgMagic);
            if (resolvedIdentityId != null) {
                logger.info("orgMagic resolved to identityId: {}", resolvedIdentityId);
            } else {
                logger.warn("orgMagic could not be resolved (expired or already used)");
            }
        }

        // Extract user information (available in User Enrollment)
        String userShortName = getString(dict, "UserShortName");
        String userLongName = getString(dict, "UserLongName");

        String buildVersion = getString(dict, "BuildVersion");
        String osVersion = getString(dict, "OSVersion");
        String productName = getString(dict, "ProductName");

        // SerialNumber is NOT available in User Enrollment (privacy feature)
        String serialNumber = isUserEnrollment ? null : getString(dict, "SerialNumber");

        // Determine enrollment type
        EnrollmentType enrollmentType = determineEnrollmentType(isUserEnrollment, serialNumber);

        // Try to find existing device
        AppleDevice existingDevice;

        if (isUserEnrollment) {
            // First try by EnrollmentID (same enrollment session)
            existingDevice = findDeviceByEnrollmentId(enrollmentId);

            // BYOD dedup: EnrollmentID changes on re-enrollment.
            // Use resolved identity → account → devices to find the same physical device.
            if (existingDevice == null && resolvedIdentityId != null) {
                existingDevice = findExistingByodDeviceForIdentity(resolvedIdentityId);
                if (existingDevice != null) {
                    logger.info("BYOD dedup: found existing device (id={}) via identity for re-enrollment", existingDevice.getId());
                }
            }
        } else {
            existingDevice = appleDeviceRepository.findByUdid(udid).orElse(null);
        }

        if (existingDevice != null) {
            logger.info("Existing device found (id={}). Re-enrolling in place (preserving UUID).", existingDevice.getId());

            // 1. Archive the old enrollment state to history
            archiveEnrollment(existingDevice, "RE_ENROLLMENT");

            // 2. Clean up Redis command queue and in-flight entries
            redisTemplate.delete("apple:command:queue:" + deviceIdentifier);
            redisTemplate.delete("apple:command:inflight:" + deviceIdentifier);
            logger.info("Redis command queue and in-flight entries cleaned up for re-enrolling device '{}'.", deviceIdentifier);

            // 3. Update existing device in place — preserve UUID, no duplicate
            existingDevice.setUdid(isUserEnrollment ? null : udid);
            existingDevice.setEnrollmentId(isUserEnrollment ? enrollmentId : null);
            existingDevice.setEnrollmentUserId(isUserEnrollment ? enrollmentUserId : null);
            existingDevice.setEnrollmentType(enrollmentType);
            existingDevice.setIsUserEnrollment(isUserEnrollment);
            existingDevice.setBuildVersion(buildVersion);
            existingDevice.setOsVersion(osVersion);
            existingDevice.setProductName(productName);
            existingDevice.setSerialNumber(serialNumber);
            existingDevice.setStatus("ACTIVE");

            appleDeviceRepository.save(existingDevice);
            logger.info("Device (id={}) re-enrolled in place.", existingDevice.getId());

            linkAccountToDevice(resolvedIdentityId, existingDevice, enrollmentUserId, userShortName, userLongName, isUserEnrollment);

            // Publish enrolled event with same device ID — back_core will update, not duplicate
            publishEnrolledEvent(existingDevice);
            return existingDevice;
        }

        logger.info("No existing device found. Creating fresh device record.");

        // License checks (only for new devices, not re-enrollments)
        LicenseContext lc = licenseContextProvider.getIfAvailable();
        if (lc != null && lc.isLoaded()) {
            // macOS platform gate — requires MACOS feature (Premium+)
            if (productName != null && isMacDevice(productName) && !lc.hasFeature(com.arcyintel.arcops.commons.license.Feature.MACOS)) {
                logger.error("ENROLLMENT BLOCKED: macOS management requires Premium license (device: {})", productName);
                throw new BusinessException("LICENSE_BLOCKED", "macOS device enrollment requires a Premium or higher license plan");
            }

            // BYOD / User Enrollment gate — requires BYOD feature (Plus+)
            if (isUserEnrollment && !lc.hasFeature(com.arcyintel.arcops.commons.license.Feature.BYOD)) {
                logger.error("ENROLLMENT BLOCKED: BYOD/User Enrollment requires Plus license");
                throw new BusinessException("LICENSE_BLOCKED", "BYOD enrollment requires a Plus or higher license plan");
            }

            // Device count limit check
            long activeDeviceCount = appleDeviceRepository.count();
            LicenseContext.WarningLevel level = lc.getDeviceWarningLevel((int) activeDeviceCount);
            if (level == LicenseContext.WarningLevel.BLOCKED) {
                logger.error("ENROLLMENT BLOCKED: device limit exceeded ({} devices, hard limit {})",
                        activeDeviceCount, lc.getHardDeviceLimit());
                throw new BusinessException("DEVICE_LIMIT_REACHED", "Maximum device enrollment limit reached. Contact your administrator.");
            }
            if (level == LicenseContext.WarningLevel.EXCEEDED || level == LicenseContext.WarningLevel.WARNING) {
                logger.warn("Enrollment proceeding but device limit approaching: {} devices (limit {})",
                        activeDeviceCount, lc.getMaxDevices());
            }
        }

        AppleDevice newDevice = AppleDevice.builder()
                .udid(isUserEnrollment ? null : udid)
                .enrollmentId(isUserEnrollment ? enrollmentId : null)
                .enrollmentUserId(isUserEnrollment ? enrollmentUserId : null)
                .enrollmentType(enrollmentType)
                .isUserEnrollment(isUserEnrollment)
                .buildVersion(buildVersion)
                .osVersion(osVersion)
                .productName(productName)
                .serialNumber(serialNumber)
                .build();
        newDevice.setStatus("ACTIVE");

        appleDeviceRepository.save(newDevice);
        logger.info("New Apple device saved (id={}).", newDevice.getId());

        linkAccountToDevice(resolvedIdentityId, newDevice, enrollmentUserId, userShortName, userLongName, isUserEnrollment);

        publishEnrolledEvent(newDevice);
        return newDevice;
    }

    /**
     * Finds a device by its EnrollmentID (used for User Enrollment).
     */
    private AppleDevice findDeviceByEnrollmentId(String enrollmentId) {
        if (enrollmentId == null || enrollmentId.isBlank()) {
            return null;
        }
        return appleDeviceRepository.findByEnrollmentId(enrollmentId).orElse(null);
    }

    /**
     * Finds an existing BYOD device linked to the same identity's account.
     * Used for dedup when the same physical device re-enrolls with a new EnrollmentID.
     */
    private AppleDevice findExistingByodDeviceForIdentity(UUID identityId) {
        Optional<AppleAccount> accountOpt = appleAccountRepository.findByIdentityId(identityId);
        if (accountOpt.isEmpty()) return null;

        return accountOpt.get().getDevices().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsUserEnrollment()))
                .filter(d -> !"DELETED".equals(d.getStatus()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Archives the current enrollment state of a device to enrollment_history.
     */
    private void archiveEnrollment(AppleDevice device, String reason) {
        try {
            UUID accountId = null;
            if (device.getAccounts() != null) {
                accountId = device.getAccounts().stream()
                        .findFirst()
                        .map(a -> a.getId())
                        .orElse(null);
            }

            LocalDateTime enrolledAt = null;
            if (device.getCreationDate() != null) {
                enrolledAt = device.getCreationDate().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }

            EnrollmentHistory history = EnrollmentHistory.builder()
                    .deviceId(device.getId())
                    .udid(device.getUdid())
                    .enrollmentId(device.getEnrollmentId())
                    .enrollmentUserId(device.getEnrollmentUserId())
                    .enrollmentType(device.getEnrollmentType() != null ? device.getEnrollmentType().name() : null)
                    .isUserEnrollment(device.getIsUserEnrollment())
                    .serialNumber(device.getSerialNumber())
                    .productName(device.getProductName())
                    .osVersion(device.getOsVersion())
                    .buildVersion(device.getBuildVersion())
                    .token(device.getToken())
                    .pushMagic(device.getPushMagic())
                    .status(device.getStatus())
                    .enrolledAt(enrolledAt)
                    .unenrolledAt(LocalDateTime.now())
                    .unenrollReason(reason)
                    .accountId(accountId)
                    .build();

            enrollmentHistoryRepository.save(history);
            logger.info("Enrollment archived for device '{}'. Reason: {}",
                    device.getUdid() != null ? device.getUdid() : device.getEnrollmentId(), reason);
        } catch (Exception e) {
            logger.error("Failed to archive enrollment: {}", e.getMessage(), e);
        }
    }

    /**
     * Determines the enrollment type based on the Authenticate message.
     * DEP devices are identified by checking if their serial number exists in the ABM device database.
     */
    /**
     * Checks if the ProductName indicates a Mac device (macOS/Mac hardware).
     * Apple ProductName values: "MacBookPro18,1", "Macmini9,1", "iMac21,1", "MacPro7,1", etc.
     */
    private boolean isMacDevice(String productName) {
        if (productName == null) return false;
        String lower = productName.toLowerCase();
        return lower.startsWith("mac") || lower.contains("imac");
    }

    private EnrollmentType determineEnrollmentType(boolean isUserEnrollment, String serialNumber) {
        if (isUserEnrollment) {
            return EnrollmentType.USER_ENROLLMENT;
        }

        // Check if the device's serial number exists in ABM device database (DEP enrollment)
        if (serialNumber != null && !serialNumber.isBlank()) {
            boolean isAbmDevice = abmDeviceRepository.findBySerialNumber(serialNumber).isPresent();
            if (isAbmDevice) {
                logger.info("Device with serial number '{}' found in ABM database. Enrollment type: DEP", serialNumber);
                return EnrollmentType.DEP;
            }
        }

        // Default to profile-based enrollment
        return EnrollmentType.PROFILE;
    }

    /**
     * Publishes a device enrolled event.
     */
    private void publishEnrolledEvent(AppleDevice device) {
        try {
            String platform = detectPlatform(device);
            logger.info("Publishing device enrolled event for device ID '{}' with platform '{}'.", device.getId(), platform);
            deviceEventPublisher.publishDeviceEnrolledEvent(new DeviceEnrolledEvent(device.getId(), new HashMap<>(), platform));
        } catch (Exception e) {
            logger.warn("Failed to publish device enrolled event: {}", e.getMessage());
        }
    }

    /**
     * Detects the platform from device properties or productName fallback.
     * DeviceProperties (AppleDeviceInformation) may not be available yet during
     * Authenticate, so we also check productName from the checkin plist.
     */
    private String detectPlatform(AppleDevice device) {
        // Primary: use deviceProperties.modelName if available (e.g. "MacBook Pro", "Apple Watch")
        if (device.getDeviceProperties() != null && device.getDeviceProperties().getModelName() != null) {
            String modelName = device.getDeviceProperties().getModelName();
            if (modelName.startsWith("Watch")) {
                return Os.WATCHOS;
            } else if (modelName.startsWith("Mac")) {
                return Os.MACOS;
            }
        }

        // Fallback: use productName from Authenticate plist (e.g. "Mac15,10", "Watch7,5", "iPhone16,1")
        if (device.getProductName() != null) {
            String productName = device.getProductName();
            if (productName.startsWith("Watch")) {
                return Os.WATCHOS;
            } else if (productName.startsWith("Mac") || productName.startsWith("VirtualMac")) {
                return Os.MACOS;
            }
        }

        return Os.IOS;
    }

    /**
     * Handles TokenUpdate request from the device.
     * User Enrollment devices send EnrollmentID instead of UDID.
     */
    @Transactional
    public AppleDevice tokenUpdate(NSDictionary dict) throws Exception {

        logger.info("Received a TokenUpdate request from an Apple device. Request details: {}", dict);
        delay(); // ensure device record exists

        String udid = getString(dict, "UDID");
        String enrollmentId = getString(dict, "EnrollmentID");

        // Resolve device: UDID for regular enrollment, EnrollmentID for User Enrollment
        String deviceIdentifier;
        Optional<AppleDevice> deviceOpt;

        if (udid != null) {
            deviceIdentifier = udid;
            deviceOpt = appleDeviceRepository.findByUdid(udid);
        } else if (enrollmentId != null) {
            deviceIdentifier = enrollmentId;
            deviceOpt = appleDeviceRepository.findByEnrollmentId(enrollmentId);
            logger.info("User Enrollment TokenUpdate: using EnrollmentID '{}'", enrollmentId);
        } else {
            logger.error("TokenUpdate request failed: neither UDID nor EnrollmentID present. Request details: {}", dict);
            return null;
        }

        if (deviceOpt.isEmpty()) {
            logger.error("TokenUpdate request failed: No device found with identifier '{}'.", deviceIdentifier);
            return null;
        }

        AppleDevice device = deviceOpt.get();
        logger.info("Device with identifier '{}' found in the database.", deviceIdentifier);

        // Check if this is a User Channel TokenUpdate (macOS sends a second TokenUpdate with UserID)
        String userId = getString(dict, "UserID");

        if (userId != null) {
            // User Channel TokenUpdate — save user channel info separately, do NOT overwrite device token
            logger.info("User channel TokenUpdate received for device '{}', UserID: '{}'. Skipping device token update.", deviceIdentifier, userId);

            String userLongName = getString(dict, "UserLongName");
            String userShortName = getString(dict, "UserShortName");
            String userPushMagic = getString(dict, "PushMagic");
            byte[] userToken = getBytes(dict, "Token");

            Map<String, Object> userChannel = new HashMap<>();
            userChannel.put("userId", userId);
            if (userLongName != null) userChannel.put("userLongName", userLongName);
            if (userShortName != null) userChannel.put("userShortName", userShortName);
            if (userToken != null) userChannel.put("token", Base64.getEncoder().encodeToString(userToken));
            if (userPushMagic != null) userChannel.put("pushMagic", userPushMagic);

            AppleDeviceInformation deviceInfo = appleDeviceInformationRepository.findByAppleDevice(device.getId())
                    .orElse(null);
            if (deviceInfo != null) {
                deviceInfo.setUserChannel(userChannel);
                appleDeviceInformationRepository.save(deviceInfo);
                logger.info("User channel info saved for device '{}'.", deviceIdentifier);
            } else {
                logger.warn("No DeviceInformation record found for device '{}'. User channel info could not be saved.", deviceIdentifier);
            }

            return device;
        }

        // Device Channel TokenUpdate — standard flow
        String pushMagic = getString(dict, "PushMagic");
        byte[] token = getBytes(dict, "Token");
        byte[] unlockToken = getBytes(dict, "UnlockToken");

        if (pushMagic != null) {
            logger.info("Updating PushMagic for device '{}'. New value: {}", deviceIdentifier, pushMagic);
            device.setPushMagic(pushMagic);
        } else {
            logger.warn("PushMagic is missing in the TokenUpdate request for device '{}'.", deviceIdentifier);
        }

        if (unlockToken != null) {
            String encodedUnlockToken = Base64.getEncoder().encodeToString(unlockToken);
            logger.info("Updating UnlockToken for device '{}'.", deviceIdentifier);
            device.setUnlockToken(encodedUnlockToken);
        } else {
            logger.debug("UnlockToken is not present in the TokenUpdate request for device '{}'.", deviceIdentifier);
        }

        if (token != null) {
            String encodedToken = Base64.getEncoder().encodeToString(token);
            logger.info("Updating Token for device '{}'.", deviceIdentifier);
            device.setToken(encodedToken);
        } else {
            logger.warn("Token is missing in the TokenUpdate request for device '{}'.", deviceIdentifier);
        }

        appleDeviceRepository.save(device);
        logger.info("Device '{}' successfully updated in the database.", deviceIdentifier);

        // Use the appropriate identifier for command queue (UDID or EnrollmentID)
        String commandIdentifier = device.getUdid() != null ? device.getUdid() : device.getEnrollmentId();
        commandSenderService.queryDeviceInformation(commandIdentifier, true);
        logger.info("Device information command sent to device '{}'.", deviceIdentifier);

        return device;
    }

    /**
     * Handles CheckOut request from the device.
     * User Enrollment devices send EnrollmentID instead of UDID.
     */
    @Transactional
    public AppleDevice checkOut(NSDictionary dict) {
        logger.info("Received a CheckOut request from an Apple device. Request details: {}", dict);

        String udid = getString(dict, "UDID");
        String enrollmentId = getString(dict, "EnrollmentID");

        // Resolve device: UDID for regular enrollment, EnrollmentID for User Enrollment
        String deviceIdentifier;
        Optional<AppleDevice> deviceOpt;

        if (udid != null) {
            deviceIdentifier = udid;
            deviceOpt = appleDeviceRepository.findByUdid(udid);
        } else if (enrollmentId != null) {
            deviceIdentifier = enrollmentId;
            deviceOpt = appleDeviceRepository.findByEnrollmentId(enrollmentId);
            logger.info("User Enrollment CheckOut: using EnrollmentID '{}'", enrollmentId);
        } else {
            logger.error("CheckOut request failed: neither UDID nor EnrollmentID present. Request details: {}", dict);
            return null;
        }

        if (deviceOpt.isEmpty()) {
            logger.error("CheckOut request failed: No device found with identifier '{}'.", deviceIdentifier);
            return null;
        }

        AppleDevice device = deviceOpt.get();
        logger.info("Device with identifier '{}' found. Archiving enrollment and removing device.", deviceIdentifier);

        // 1. Build platform snapshot BEFORE any mutation
        Map<String, Object> platformSnapshot = deviceCleanupService.buildDeviceSnapshot(device.getId(), device.getUdid());

        // 2. Archive enrollment to history
        archiveEnrollment(device, "CHECKOUT");

        // 3. Clean up all agent data tables (location, telemetry, presence, commands, apps, etc.)
        deviceCleanupService.cleanupDeviceData(device.getId(), device.getUdid());

        // 4. Null unique identifiers so they don't interfere with future enrollments
        device.setUdid(null);
        device.setEnrollmentId(null);
        device.setStatus("DELETED");
        appleDeviceRepository.saveAndFlush(device);
        logger.info("Device '{}' marked as DELETED.", deviceIdentifier);

        // 5. Publish event with platform snapshot for back_core to save
        deviceEventPublisher.publishDeviceDisenrolledEvent(
                new DeviceDisenrolledEvent(device.getId(), platformSnapshot));
        logger.info("Device disenrollment event published for device id '{}'.", device.getId());

        // 6. Clean up Redis command queue and in-flight entries
        redisTemplate.delete("apple:command:queue:" + deviceIdentifier);
        redisTemplate.delete("apple:command:inflight:" + deviceIdentifier);
        logger.info("Redis command queue and in-flight entries cleaned up for device '{}'.", deviceIdentifier);

        return device;
    }

    /**
     * Handles DeclarativeManagement check-in messages from the device.
     * <p>
     * Endpoint can be:
     * - "status"
     * - "declaration-items"
     * - "declaration/configuration/{id}"
     * - "declaration/asset/{id}"
     * - "declaration/activation/{id}"
     */
    @Transactional
    public String declarativeManagement(NSDictionary dict) throws Exception {
        logger.info("Received a DeclarativeManagement request from an Apple device. Request details: {}", dict);

        String udid = getString(dict, "UDID");
        String enrollmentId = getString(dict, "EnrollmentID");

        Optional<AppleDevice> deviceOpt;
        if (udid != null) {
            deviceOpt = appleDeviceRepository.findByUdid(udid);
        } else if (enrollmentId != null) {
            deviceOpt = appleDeviceRepository.findByEnrollmentId(enrollmentId);
        } else {
            logger.error("DeclarativeManagement request failed: neither UDID nor EnrollmentID present. Request details: {}", dict);
            return null;
        }

        if (deviceOpt.isEmpty()) {
            logger.error("DeclarativeManagement request failed: No device found with identifier '{}'.", udid != null ? udid : enrollmentId);
            return null;
        }
        AppleDevice device = deviceOpt.get();

        Object endpointObj = dict.objectForKey("Endpoint");
        if (endpointObj == null) {
            logger.error("DeclarativeManagement request failed: Endpoint is missing for device with UDID '{}'.", udid);
            return null;
        }
        String endpoint = endpointObj.toString();
        logger.info("DeclarativeManagement endpoint '{}' for device with UDID '{}'.", endpoint, udid);

        // ---- status ----
        if ("status".equals(endpoint)) {
            Object dataObj = dict.objectForKey("Data");
            if (dataObj instanceof NSData data) {
                try {
                    byte[] raw = data.bytes();
                    String jsonStatus = new String(raw, StandardCharsets.UTF_8);

                    logger.info("Updating declarative management status for device with UDID '{}'. Raw JSON: {}", udid, jsonStatus);

                    Map<String, Object> status = objectMapper.readValue(
                            jsonStatus,
                            new TypeReference<Map<String, Object>>() {
                            }
                    );

                    device.setDeclarativeStatus(status);
                    appleDeviceRepository.save(device);

                    logger.info("Declarative management status saved for device UDID='{}'.", udid);
                } catch (Exception e) {
                    logger.error("Failed to parse or update declarative management status for device with UDID '{}'.", udid, e);
                }
            } else {
                logger.warn("DeclarativeManagement status request has no valid Data payload for device with UDID '{}'.", udid);
            }

            // For status, return minimal body
            return "{}";
        }

        // From here on, build declarations from applied policy
        List<Map<String, Object>> dmConfigurations = getDeviceDeclarativeConfigurations(device);
        List<Map<String, Object>> dmAssets = getDeviceDeclarativeAssets(device);

        // ---- declaration-items ----
        if ("declaration-items".equals(endpoint)) {
            String response = buildDeclarationItemsResponse(dmConfigurations, dmAssets);
            logger.info("Returning declaration-items for device UDID='{}': {}", udid, response);
            return response;
        }

        // ---- declaration/configuration/{id} ----
        if (endpoint.startsWith("declaration/configuration/")) {
            final String prefix = "declaration/configuration/";
            String cfgId = endpoint.substring(prefix.length());
            if (cfgId.isBlank()) {
                logger.error("DeclarativeManagement configuration endpoint missing identifier for device with UDID '{}'.", udid);
                return null;
            }

            for (Map<String, Object> cfg : dmConfigurations) {
                Object identifierObj = cfg.get("Identifier");
                if (identifierObj != null && cfgId.equals(identifierObj.toString())) {
                    logger.info("Found declarative configuration '{}' for device with UDID '{}'.", cfgId, udid);
                    return objectMapper.writeValueAsString(cfg);
                }
            }

            logger.warn("No declarative configuration with identifier '{}' found for device with UDID '{}'.", cfgId, udid);
            return null;
        }

        // ---- declaration/asset/{id} ----
        if (endpoint.startsWith("declaration/asset/")) {
            final String prefix = "declaration/asset/";
            String assetId = endpoint.substring(prefix.length());
            if (assetId.isBlank()) {
                logger.error("DeclarativeManagement asset endpoint missing identifier for device with UDID '{}'.", udid);
                return null;
            }

            for (Map<String, Object> asset : dmAssets) {
                Object identifierObj = asset.get("Identifier");
                if (identifierObj != null && assetId.equals(identifierObj.toString())) {
                    logger.info("Found declarative asset '{}' for device with UDID '{}'.", assetId, udid);
                    return objectMapper.writeValueAsString(asset);
                }
            }

            logger.warn("No declarative asset with identifier '{}' found for device with UDID '{}'.", assetId, udid);
            return null;
        }

        // ---- declaration/activation/{id} ----
        if (endpoint.startsWith("declaration/activation/")) {
            final String prefix = "declaration/activation/";
            String activationId = endpoint.substring(prefix.length());
            if (activationId.isBlank()) {
                logger.error("DeclarativeManagement activation endpoint missing identifier for device with UDID '{}'.", udid);
                return null;
            }

            // default activation references all configuration identifiers
            List<String> cfgIdentifiers = dmConfigurations.stream()
                    .map(m -> {
                        Object id = m.get("Identifier");
                        return id == null ? null : id.toString();
                    })
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toList());

            Map<String, Object> activation = buildDefaultActivation(activationId, cfgIdentifiers, dmConfigurations);
            String response = objectMapper.writeValueAsString(activation);

            logger.info("Returning activation '{}' for device UDID='{}': {}", activationId, udid, response);
            return response;
        }

        logger.warn("Unsupported DeclarativeManagement endpoint '{}' for device with UDID '{}'.", endpoint, udid);
        return null;
    }

    /**
     * Returns iOS declarative configurations for this device from appliedPolicy:
     * <p>
     * appliedPolicy.ios.declarativeManagement.configurations[]
     * <p>
     * Example:
     * {
     * "ios": {
     * "declarativeManagement": {
     * "server_token": "...",
     * "configurations": [ { "Type": "...", "Identifier": "...", "ServerToken": "...", "Payload": {...} } ]
     * }
     * }
     * }
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getDeviceDeclarativeConfigurations(AppleDevice device) {

        Map<String, Object> appliedPolicyObj = device.getAppliedPolicy();
        if (appliedPolicyObj == null) {
            logger.debug("Device with UDID '{}' has no appliedPolicy map. appliedPolicyObj=null", device.getUdid());
            return new ArrayList<>();
        }

        Map<String, Object> payload = (Map<String, Object>) appliedPolicyObj.get(PAYLOAD);
        if (payload == null) {
            logger.debug("Device with UDID '{}' has no 'ios' section in appliedPolicy.", device.getUdid());
            return new ArrayList<>();
        }

        Object dmSectionObj = payload.get(DECLARATIVE_MANAGEMENT);
        if (dmSectionObj == null) {
            dmSectionObj = payload.get("declarativeManagement");
        }
        if (!(dmSectionObj instanceof Map<?, ?>)) {
            logger.debug("Device with UDID '{}' has no 'declarativeManagement' section in appliedPolicy.", device.getUdid());
            return new ArrayList<>();
        }

        Map<String, Object> dmSection = (Map<String, Object>) dmSectionObj;

        Object configsObj = dmSection.get("configurations");
        if (!(configsObj instanceof List<?>)) {
            logger.debug("Device with UDID '{}' has no 'configurations' list under declarativeManagement.", device.getUdid());
            return new ArrayList<>();
        }

        List<?> rawList = (List<?>) configsObj;
        List<Map<String, Object>> dmConfigurations = new ArrayList<>();

        for (Object item : rawList) {
            if (item instanceof Map<?, ?> itemMap) {
                Map<String, Object> cfg = new HashMap<>();
                for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                    Object key = entry.getKey();
                    if (key != null) {
                        cfg.put(key.toString(), entry.getValue());
                    }
                }
                dmConfigurations.add(cfg);
            } else {
                logger.warn("Unexpected configuration entry type '{}' in declarativeManagement.configurations for device UDID '{}'.",
                        item != null ? item.getClass() : "null", device.getUdid());
            }
        }

        logger.info("Resolved {} declarative configurations from appliedPolicy for device with UDID '{}'.",
                dmConfigurations.size(), device.getUdid());

        return dmConfigurations;
    }

    @Transactional
    public Map<String, Object> getDeclarativeAssetDocument(UUID deviceId, String assetIdentifier) {
        if (assetIdentifier == null || assetIdentifier.isBlank()) {
            throw new IllegalArgumentException("assetIdentifier is required");
        }

        AppleDevice device = appleDeviceRepository.findById(deviceId).orElseThrow(
                () -> new IllegalArgumentException("Device not found: " + deviceId)
        );

        Map<String, Object> appliedPolicyObj = device.getAppliedPolicy();
        if (appliedPolicyObj == null) {
            throw new IllegalArgumentException("Device has no appliedPolicy: " + deviceId);
        }

        Object payloadObj = appliedPolicyObj.get(PAYLOAD);
        if (!(payloadObj instanceof Map<?, ?> payloadRaw)) {
            throw new IllegalArgumentException("Device appliedPolicy has no payload: " + deviceId);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) payloadRaw;

        Object dmSectionObj = policy.get(DECLARATIVE_MANAGEMENT);
        if (dmSectionObj == null) {
            dmSectionObj = policy.get("declarativeManagement");
        }
        if (!(dmSectionObj instanceof Map<?, ?> dmRaw)) {
            throw new IllegalArgumentException("Device appliedPolicy has no declarativeManagement section: " + deviceId);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> dmSection = (Map<String, Object>) dmRaw;

        Object resolvedObj = dmSection.get("resolvedAssetData");
        if (!(resolvedObj instanceof Map<?, ?> resolvedRaw)) {
            throw new IllegalArgumentException("No resolvedAssetData found for device: " + deviceId);
        }

        Object credentialDocObj = resolvedRaw.get(assetIdentifier);
        if (!(credentialDocObj instanceof Map<?, ?> credentialDocRaw)) {
            throw new IllegalArgumentException("Asset not found on device resolvedAssetData: " + assetIdentifier);
        }
        Map<String, Object> payload = (Map<String, Object>) credentialDocRaw.get("Payload");

        if (payload == null) {
            throw new IllegalStateException("Resolved asset data is missing Type/Payload for identifier: " + assetIdentifier);
        }

        return payload;
    }

    /**
     * Returns iOS declarative assets for this device from appliedPolicy:
     * <p>
     * appliedPolicy.ios.declarativeManagement.assets[]
     * <p>
     * Each asset should look like:
     * {
     * "Type": "com.apple.credential.usernameandpassword",
     * "Identifier": "ASSET-...",
     * "ServerToken": "...",
     * "Payload": {...}
     * }
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getDeviceDeclarativeAssets(AppleDevice device) {

        Map<String, Object> appliedPolicyObj = device.getAppliedPolicy();
        if (appliedPolicyObj == null) {
            logger.debug("Device with UDID '{}' has no appliedPolicy map. appliedPolicyObj=null", device.getUdid());
            return new ArrayList<>();
        }

        Map<String, Object> payload = (Map<String, Object>) appliedPolicyObj.get(PAYLOAD);
        if (payload == null) {
            logger.debug("Device with UDID '{}' has no payload section in appliedPolicy.", device.getUdid());
            return new ArrayList<>();
        }

        Object dmSectionObj = payload.get(DECLARATIVE_MANAGEMENT);
        if (dmSectionObj == null) {
            dmSectionObj = payload.get("declarativeManagement");
        }
        if (!(dmSectionObj instanceof Map<?, ?>)) {
            logger.debug("Device with UDID '{}' has no 'declarativeManagement' section in appliedPolicy.", device.getUdid());
            return new ArrayList<>();
        }

        Map<String, Object> dmSection = (Map<String, Object>) dmSectionObj;

        Object assetsObj = dmSection.get("assets");
        if (!(assetsObj instanceof List<?>)) {
            logger.debug("Device with UDID '{}' has no 'assets' list under declarativeManagement.", device.getUdid());
            return new ArrayList<>();
        }

        List<?> rawList = (List<?>) assetsObj;
        List<Map<String, Object>> dmAssets = new ArrayList<>();

        for (Object item : rawList) {
            if (item instanceof Map<?, ?> itemMap) {
                Map<String, Object> asset = new HashMap<>();
                for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                    Object key = entry.getKey();
                    if (key != null) {
                        asset.put(key.toString(), entry.getValue());
                    }
                }
                dmAssets.add(asset);
            } else {
                logger.warn("Unexpected asset entry type '{}' in declarativeManagement.assets for device UDID '{}'.",
                        item != null ? item.getClass() : "null", device.getUdid());
            }
        }

        logger.info("Resolved {} declarative assets from appliedPolicy for device with UDID '{}'.",
                dmAssets.size(), device.getUdid());

        return dmAssets;
    }

    /**
     * Build declaration-items response as:
     * {
     * "Declarations": {
     * "Activations": [],
     * "Assets": [],
     * "Configurations": [],
     * "Management": []
     * },
     * "DeclarationsToken": "..."
     * }
     */
    private String buildDeclarationItemsResponse(List<Map<String, Object>> dmConfigurations,
                                                 List<Map<String, Object>> dmAssets) throws Exception {

        DeclarationItemsResponse response = new DeclarationItemsResponse();
        response.Declarations = new DeclarationItems();

        // ---- Assets ----
        if (dmAssets != null) {
            for (Map<String, Object> asset : dmAssets) {
                Object identifierObj = asset.get("Identifier");
                Object serverTokenObj = asset.get("ServerToken");
                if (identifierObj == null) continue;

                ManifestDeclaration md = new ManifestDeclaration();
                md.Identifier = identifierObj.toString();
                md.ServerToken = serverTokenObj != null ? serverTokenObj.toString() : null;

                response.Declarations.Assets.add(md);
            }
            response.Declarations.Assets.sort(Comparator.comparing(o -> o.Identifier));
        }

        // ---- Configurations ----
        String lastConfigServerToken = null;
        for (Map<String, Object> cfg : dmConfigurations) {
            Object identifierObj = cfg.get("Identifier");
            Object serverTokenObj = cfg.get("ServerToken");
            if (identifierObj == null) continue;

            ManifestDeclaration md = new ManifestDeclaration();
            md.Identifier = identifierObj.toString();
            md.ServerToken = serverTokenObj != null ? serverTokenObj.toString() : null;

            response.Declarations.Configurations.add(md);

            if (md.ServerToken != null) {
                lastConfigServerToken = md.ServerToken;
            }
        }
        response.Declarations.Configurations.sort(Comparator.comparing(o -> o.Identifier));

        // ---- Activation: act/default ----
        ManifestDeclaration activationDecl = new ManifestDeclaration();
        activationDecl.Identifier = "act/default";

        // If configurations empty -> clear state: activation token must be "0"
        if (response.Declarations.Configurations.isEmpty()) {
            activationDecl.ServerToken = "0";
        } else {
            // keep stable; use last config token (same approach you had)
            activationDecl.ServerToken = lastConfigServerToken;
        }

        response.Declarations.Activations.add(activationDecl);
        response.Declarations.Activations.sort(Comparator.comparing(o -> o.Identifier));

        // ---- DeclarationsToken ----
        // Deterministic over assets + configs + activations (+ management if you add later)
        StringBuilder material = new StringBuilder();

        if (response.Declarations.Assets.isEmpty()) {
            material.append("ASSETS:EMPTY|");
        } else {
            for (ManifestDeclaration md : response.Declarations.Assets) {
                material.append("S:").append(md.Identifier).append(":").append(md.ServerToken).append("|");
            }
        }

        if (response.Declarations.Configurations.isEmpty()) {
            material.append("CONFIGS:EMPTY|");
        } else {
            for (ManifestDeclaration md : response.Declarations.Configurations) {
                material.append("C:").append(md.Identifier).append(":").append(md.ServerToken).append("|");
            }
        }

        for (ManifestDeclaration md : response.Declarations.Activations) {
            material.append("A:").append(md.Identifier).append(":").append(md.ServerToken).append("|");
        }

        // (Management currently empty, but left in schema)
        response.DeclarationsToken = sha256(material.toString());

        return objectMapper.writeValueAsString(response);
    }

    /**
     * Builds ActivationSimple declaration with StandardConfigurations.
     * If cfgIdentifiers empty => return activation that effectively clears.
     */
    private Map<String, Object> buildDefaultActivation(String activationId,
                                                       List<String> cfgIdentifiers,
                                                       List<Map<String, Object>> dmConfigurations) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("StandardConfigurations", cfgIdentifiers == null ? List.of() : cfgIdentifiers);

        Map<String, Object> activation = new HashMap<>();
        activation.put("Identifier", activationId);
        activation.put("Type", "com.apple.activation.simple");
        activation.put("Payload", payload);

        // If clearing (no configs) => token "0"
        if (cfgIdentifiers == null || cfgIdentifiers.isEmpty()) {
            activation.put("ServerToken", "0");
            return activation;
        }

        // Else: use last non-null ServerToken from configs as token
        String serverToken = null;
        for (Map<String, Object> cfg : dmConfigurations) {
            Object st = cfg.get("ServerToken");
            if (st != null) serverToken = st.toString();
        }
        activation.put("ServerToken", serverToken);

        return activation;
    }

    private String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Links an account to a device. Uses resolved identity if available,
     * otherwise falls back to enrollmentUserId for User Enrollment.
     */
    private void linkAccountToDevice(UUID resolvedIdentityId, AppleDevice device,
                                      String enrollmentUserId, String userShortName,
                                      String userLongName, boolean isUserEnrollment) {
        // If we have a resolved identity from orgMagic, use it directly
        if (resolvedIdentityId != null) {
            linkAccountWithIdentity(resolvedIdentityId, device);
            return;
        }

        // Fallback: For User Enrollment, try to resolve account using EnrollmentUserID
        if (isUserEnrollment && enrollmentUserId != null && !enrollmentUserId.isBlank()) {
            try {
                AccountResolutionContext context = AccountResolutionContext.builder()
                        .identifier(enrollmentUserId)
                        .managedAppleId(enrollmentUserId)
                        .enrollmentUserId(enrollmentUserId)
                        .shortName(userShortName)
                        .fullName(userLongName)
                        .identitySource(AccountResolutionContext.IdentitySource.MANAGED_APPLE_ID)
                        .autoCreate(true)
                        .build();

                Optional<AppleAccount> accountOpt = accountResolverRegistry.resolve(context);

                if (accountOpt.isEmpty() && context.isAutoCreate()) {
                    accountOpt = accountResolverRegistry.createOrUpdate(context);
                }

                if (accountOpt.isPresent()) {
                    AppleAccount account = accountOpt.get();
                    account.getDevices().add(device);
                    appleAccountRepository.save(account);
                    logger.info("Account '{}' linked to User Enrollment device via EnrollmentUserID.", account.getUsername());
                } else {
                    logger.warn("Could not resolve or create account for User Enrollment. EnrollmentUserID: {}", enrollmentUserId);
                }
            } catch (Exception e) {
                logger.error("Failed to link account for User Enrollment: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Links an identity and account to a device using the resolved identity ID.
     * Finds or creates an AppleAccount, links the device, and publishes AccountCreatedEvent.
     */
    private void linkAccountWithIdentity(UUID identityId, AppleDevice device) {
        String deviceRef = device.getUdid() != null ? device.getUdid() : device.getEnrollmentId();
        try {
            Optional<AppleIdentity> identityOpt = appleIdentityRepository.findById(identityId);
            if (identityOpt.isEmpty()) {
                logger.error("AppleIdentity not found for id={} (device '{}')", identityId, deviceRef);
                return;
            }

            AppleIdentity identity = identityOpt.get();
            logger.info("Found identity username='{}' for device '{}'.", identity.getUsername(), deviceRef);

            Optional<AppleAccount> existingAccount = appleAccountRepository.findByIdentityId(identityId);

            AppleAccount account;
            if (existingAccount.isPresent()) {
                account = existingAccount.get();
                logger.info("Existing account found (id={}) for identity '{}'.", account.getId(), identity.getUsername());
            } else {
                account = AppleAccount.builder()
                        .username(identity.getUsername())
                        .email(identity.getEmail())
                        .fullName(identity.getFullName())
                        .identity(identity)
                        .build();
                account = appleAccountRepository.save(account);
                logger.info("New AppleAccount created (id={}) for identity '{}'.", account.getId(), identity.getUsername());
            }

            account.getDevices().add(device);
            appleAccountRepository.save(account);
            logger.info("Device '{}' linked to account '{}'.", deviceRef, identity.getUsername());

            String platform = detectPlatform(device);

            accountEventPublisher.publishAccountCreatedEvent(
                    AccountCreatedEvent.builder()
                            .accountId(account.getId())
                            .identityId(identityId)
                            .deviceId(device.getId())
                            .platform(platform)
                            .build()
            );
            logger.info("AccountCreatedEvent published for identity={}, device='{}'.", identityId, deviceRef);

            // Record SETUP auth history
            try {
                DeviceAuthHistory history = DeviceAuthHistory.builder()
                        .device(device)
                        .deviceIdentifier(deviceRef)
                        .identity(identity)
                        .username(identity.getUsername())
                        .authSource("SETUP")
                        .eventType("SIGN_IN")
                        .build();
                deviceAuthHistoryRepository.save(history);
            } catch (Exception ex) {
                logger.warn("Failed to record SETUP auth history for device '{}': {}", deviceRef, ex.getMessage());
            }
        } catch (Exception e) {
            logger.error("linkAccountWithIdentity FAILED for device '{}': {}", deviceRef, e.getMessage(), e);
        }
    }

    // --- DTOs for DM schema ---

    private String getString(NSDictionary dict, String key) {
        if (key == null) {
            logger.error("Failed to retrieve value: Dictionary key is null.");
            return null;
        }
        if (dict == null) {
            logger.error("Failed to retrieve value: Dictionary is null.");
            return null;
        }
        if (dict.objectForKey(key) == null) {
            logger.debug("Key '{}' not found in the dictionary.", key);
            return null;
        }
        String value = dict.objectForKey(key).toString();
        logger.debug("Successfully retrieved value for key '{}': {}", key, value);
        return value;
    }

    private byte[] getBytes(NSDictionary dict, String key) {
        if (key == null) {
            logger.error("Failed to retrieve value: Dictionary key is null.");
            return null;
        }
        if (dict == null) {
            logger.error("Failed to retrieve value: Dictionary is null.");
            return null;
        }
        if (dict.objectForKey(key) == null) {
            logger.debug("Key '{}' not found in the dictionary.", key);
            return null;
        }
        logger.debug("Key '{}' found in the dictionary. Attempting to retrieve its value as a byte array.", key);
        byte[] value = dict.objectForKey(key).toJavaObject(byte[].class);
        if (value == null) {
            logger.warn("The value for key '{}' is null or could not be converted to a byte array.", key);
        } else {
            logger.debug("Successfully retrieved and converted value for key '{}' to a byte array.", key);
        }
        return value;
    }

    private void delay() {
        try {
            logger.debug("Starting a delay of 10 seconds to ensure the device record is inserted into the database.");
            Thread.sleep(10000);
            logger.debug("Delay of 10 seconds completed successfully.");
        } catch (InterruptedException e) {
            logger.error("Thread was interrupted during the delay operation.", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread was interrupted during the delay operation.", e);
        }
    }

    public AppleCommandRepository getAppleCommandRepository() {
        return appleCommandRepository;
    }

    // --- Helper Methods ---

    private static class ManifestDeclaration {
        public String Identifier;
        public String ServerToken;
    }

    private static class DeclarationItems {
        public List<ManifestDeclaration> Activations = new ArrayList<>();
        public List<ManifestDeclaration> Assets = new ArrayList<>();
        public List<ManifestDeclaration> Configurations = new ArrayList<>();
        public List<ManifestDeclaration> Management = new ArrayList<>();
    }

    private static class DeclarationItemsResponse {
        public DeclarationItems Declarations = new DeclarationItems();
        public String DeclarationsToken;
    }
}

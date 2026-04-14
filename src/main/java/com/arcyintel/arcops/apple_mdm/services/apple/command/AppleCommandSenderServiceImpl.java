package com.arcyintel.arcops.apple_mdm.services.apple.command;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.domains.EnterpriseApp;
import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset.AssetAssignRequest;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.repositories.EnterpriseAppRepository;
import com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository;
import com.arcyintel.arcops.apple_mdm.services.app.EnterpriseAppService;
import com.arcyintel.arcops.apple_mdm.services.apple.abm.AppleVppService;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandBuilderService;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandQueueService;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandSenderService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.arcyintel.arcops.apple_mdm.models.enums.CommandTypes.*;

@SuppressWarnings("unchecked")
@RequiredArgsConstructor
@Service
@Primary
public class AppleCommandSenderServiceImpl implements AppleCommandSenderService {

    private static final Logger logger = LogManager.getLogger(AppleCommandSenderServiceImpl.class);

    private final AppleDeviceRepository appleDeviceRepository;
    private final AppleCommandQueueService appleCommandQueueService;
    private final AppleCommandBuilderService appleCommandBuilderService;
    private final ItunesAppMetaRepository itunesAppMetaRepository;
    private final EnterpriseAppRepository enterpriseAppRepository;
    private final AppleVppService appleVppService;
    private final EnterpriseAppService enterpriseAppService;
    private final PolicyComplianceTracker policyComplianceTracker;

    @Value("${host}")
    private String serverHost;

    @Value("${mdm.organization.name}")
    private String organizationName;

    @Async
    public void installApp(String deviceUdid, Object identifier, boolean removable, boolean fromPolicy, UUID policyId) throws Exception {
        logger.info("Starting app installation for device UDID: {}, identifier: {}, removable: {}, fromPolicy: {}, policyId: {}",
                deviceUdid, identifier, removable, fromPolicy, policyId);

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_INSTALL_APP_COMMAND.getRequestType(), fromPolicy, policyId);

        Integer trackIdResolved = null;
        String bundleIdResolved = null;
        EnterpriseApp enterpriseApp = null;

        if (identifier instanceof Integer i) {
            trackIdResolved = i;
        } else if (identifier instanceof Long l) {
            trackIdResolved = l.intValue();
        } else if (identifier instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.matches("\\d+")) {
                try {
                    trackIdResolved = Integer.valueOf(trimmed);
                } catch (NumberFormatException nfe) {
                    logger.warn("Failed to parse numeric identifier '{}': {}", trimmed, nfe.getMessage());
                }
            } else {
                bundleIdResolved = trimmed;
                // First, check VPP (iTunes App Store)
                Optional<ItunesAppMeta> meta = itunesAppMetaRepository.findByBundleId(bundleIdResolved);
                if (meta.isPresent() && meta.get().getTrackId() != null) {
                    trackIdResolved = meta.get().getTrackId().intValue();
                    logger.info("Resolved bundleId '{}' to VPP trackId={}", bundleIdResolved, trackIdResolved);
                } else {
                    // Not in VPP, check Enterprise apps
                    Optional<EnterpriseApp> enterpriseOpt = enterpriseAppRepository.findByBundleId(bundleIdResolved);
                    if (enterpriseOpt.isPresent()) {
                        enterpriseApp = enterpriseOpt.get();
                        logger.info("Found Enterprise app for bundleId '{}': id={}", bundleIdResolved, enterpriseApp.getId());
                    } else {
                        logger.warn("No VPP or Enterprise app found for bundleId '{}'", bundleIdResolved);
                    }
                }
            }
        } else {
            logger.error("Invalid app identifier type: {}", identifier == null ? "null" : identifier.getClass());
            throw new IllegalArgumentException("Invalid app identifier type");
        }

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Apple device with UDID: {} not found. Aborting app installation.", deviceUdid);
            return;
        }

        AppleDevice device = deviceOpt.get();
        boolean isMacOS = Optional.ofNullable(device.getProductName()).orElse("")
                .toLowerCase(Locale.ROOT).contains("mac");

        NSDictionary commandTemplate;
        String appIdentifierForTracking;
        String requestType;

        if (enterpriseApp != null) {
            // Enterprise app installation via ManifestURL
            String manifestUrl = enterpriseAppService.getManifestUrl(enterpriseApp.getId());

            if (isMacOS) {
                // macOS: Use InstallEnterpriseApplication (macOS-only command)
                logger.info("Installing Enterprise app via InstallEnterpriseApplication (macOS): bundleId={}, manifestUrl={}",
                        enterpriseApp.getBundleId(), manifestUrl);
                commandTemplate = appleCommandBuilderService.installEnterpriseApp(manifestUrl, commandUUID);
                requestType = DEVICE_INSTALL_ENTERPRISE_APP_COMMAND.getRequestType();
            } else {
                // iOS/iPadOS: Use InstallApplication with ManifestURL
                logger.info("Installing Enterprise app via InstallApplication (iOS): bundleId={}, manifestUrl={}",
                        enterpriseApp.getBundleId(), manifestUrl);

                // Build managed app configuration with device serial for agent identification
                NSDictionary managedConfig = buildManagedAppConfig(device);
                commandTemplate = appleCommandBuilderService.installAppFromManifest(manifestUrl, removable, commandUUID, managedConfig);
                requestType = DEVICE_INSTALL_APP_COMMAND.getRequestType();
            }

            appIdentifierForTracking = enterpriseApp.getBundleId();
        } else if (trackIdResolved != null) {
            // VPP app installation via iTunesStoreID
            commandTemplate = appleCommandBuilderService.installApp(trackIdResolved, removable, commandUUID);
            appIdentifierForTracking = String.valueOf(trackIdResolved);
            requestType = DEVICE_INSTALL_APP_COMMAND.getRequestType();

            // Assign VPP asset before MDM install command
            try {
                String serial = device.getSerialNumber();
                String adamId = String.valueOf(trackIdResolved);

                if (serial != null && !serial.isBlank()) {
                    AssetAssignRequest request = new AssetAssignRequest(serial, List.of(adamId));
                    appleVppService.assignAssetsToDevices(List.of(request));
                    logger.info("Assigned VPP asset (adamId={}) to device (serial={}) via ABM.", adamId, serial);
                } else {
                    logger.warn("Skipping ABM asset assignment. serial={}", serial);
                }
            } catch (Exception e) {
                logger.error("Failed to assign VPP asset via ABM for UDID={} trackId={}: {}",
                        deviceUdid, trackIdResolved, e.getMessage(), e);
            }
        } else if (bundleIdResolved != null) {
            // Fallback: try installing by bundleId (may not work without VPP/Enterprise)
            logger.warn("Installing app by bundleId without VPP or Enterprise metadata: {}", bundleIdResolved);
            commandTemplate = appleCommandBuilderService.installApp(bundleIdResolved, removable, commandUUID);
            appIdentifierForTracking = bundleIdResolved;
            requestType = DEVICE_INSTALL_APP_COMMAND.getRequestType();
        } else {
            logger.error("Unable to resolve identifier '{}' to trackId, bundleId, or Enterprise app.", identifier);
            throw new IllegalStateException("Identifier could not be resolved to a valid app reference");
        }

        // Register command for compliance tracking if from policy
        if (fromPolicy) {
            policyComplianceTracker.registerCommand(deviceUdid, commandUUID,
                    requestType, appIdentifierForTracking);
        }

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                requestType,
                false,
                fromPolicy,
                policyId
        );

        logger.info("App installation command queued for device UDID: {}, identifier: {}", deviceUdid, identifier);
    }

    @Async
    public void removeApp(String deviceUdid, String identifier) throws Exception {
        logger.info("Starting app removal for device UDID: {}, identifier: {}", deviceUdid, identifier);

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_REMOVE_APP_COMMAND.getRequestType(), false, null);

        String resolvedIdentifier = identifier;
        if (identifier != null && identifier.trim().matches("\\d+")) {
            Optional<ItunesAppMeta> meta = itunesAppMetaRepository.findByTrackId(Long.valueOf(identifier.trim()));
            if (meta.isPresent() && meta.get().getBundleId() != null) {
                resolvedIdentifier = meta.get().getBundleId();
                logger.info("Resolved trackId {} to bundleId {}", identifier, resolvedIdentifier);
            }
        }

        NSDictionary root = appleCommandBuilderService.removeApplication(resolvedIdentifier, commandUUID);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Apple device with UDID: {} not found. Aborting app removal.", deviceUdid);
            return;
        }

        AppleDevice device = deviceOpt.get();
        Optional<ItunesAppMeta> vppAsset = itunesAppMetaRepository.findByBundleId(resolvedIdentifier);
        if (vppAsset.isEmpty()) {
            logger.warn("VPP asset not found for bundleId: {} on device UDID: {}. Aborting app removal.", resolvedIdentifier, deviceUdid);
            return;
        }

        AssetAssignRequest request = new AssetAssignRequest(device.getSerialNumber(), List.of(vppAsset.get().getTrackId().toString()));
        appleVppService.disassociateAssetsFromDevices(List.of(request));
        logger.info("Disassociated asset (adamId={}) from device (serial={}) via ABM.", request.getAdamIds().getFirst(), request.getSerialNumber());

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                root,
                DEVICE_REMOVE_APP_COMMAND.getRequestType(),
                false,
                false,
                null
        );

        logger.info("RemoveApplication command queued for device UDID: {}", deviceUdid);
    }

    @Async
    public void removeProfile(String deviceUdid, String identifier) throws Exception {
        logger.info("Starting profile removal for device UDID: {} and identifier: {}", deviceUdid, identifier);

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_REMOVE_PROFILE_COMMAND.getRequestType(), false, null);
        NSDictionary commandTemplate = appleCommandBuilderService.removeProfile(identifier, commandUUID);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Apple device with UDID: {} not found. Aborting profile removal.", deviceUdid);
            return;
        }

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_REMOVE_PROFILE_COMMAND.getRequestType(),
                false,
                false,
                null);

        logger.info("Profile removal command queued for device UDID: {}", deviceUdid);
    }

    @Async
    public void queryDeviceInformation(String deviceUdid, boolean isSystem) throws Exception {
        queueDeviceInformationQuery(deviceUdid, isSystem);
    }

    /**
     * Synchronous helper that pushes a DeviceInformation query directly to the queue.
     * Use this from within other @Async command methods to guarantee ordering
     * (the info query is always queued AFTER the preceding command).
     */
    private void queueDeviceInformationQuery(String deviceUdid, boolean isSystem) throws Exception {
        String commandUUID = createCommandUUID(deviceUdid, DEVICE_INFO_COMMAND.getRequestType(), false, null);
        NSDictionary commandTemplate = appleCommandBuilderService.queryDeviceInformation(commandUUID);
        appleCommandQueueService.pushCommand(deviceUdid, commandUUID, commandTemplate, DEVICE_INFO_COMMAND.getRequestType(), isSystem, false, null);
    }

    public void sendDeclarativeManagementCommand(String deviceUdid, String deterministicHash, UUID policyId) throws Exception {
        String commandUUID = createCommandUUID(
                deviceUdid,
                DEVICE_DECLARATIVE_MANAGEMENT_COMMAND.getRequestType(),
                true,
                policyId
        );

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Apple device with UDID: {} not found. Aborting DeclarativeManagement command.", deviceUdid);
            return;
        }

        AppleDevice device = deviceOpt.get();
        String currentServerToken = device.getDeclarationToken();

        if (currentServerToken != null && currentServerToken.equals(deterministicHash)) {
            logger.info("Declarative management hash unchanged for device UDID: {}. Skipping.", deviceUdid);
            return;
        }

        device.setDeclarationToken(deterministicHash);
        appleDeviceRepository.save(device);

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String syncTokensJson = String.format(
                "{\"SyncTokens\": {\"Timestamp\": \"%s\", \"DeclarationsToken\": \"%s\"}}",
                now.toString(),
                device.getDeclarationToken()
        );
        String encodedData = Base64.getEncoder()
                .encodeToString(syncTokensJson.getBytes(StandardCharsets.UTF_8));

        NSDictionary commandTemplate = appleCommandBuilderService.declarativeManagement(encodedData, commandUUID);

        // Register DeclarativeManagement command for compliance tracking if from policy
        if (policyId != null) {
            policyComplianceTracker.registerCommand(deviceUdid, commandUUID, DEVICE_DECLARATIVE_MANAGEMENT_COMMAND.getRequestType(), "Declarative Management (Accounts & Updates)");
        }

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_DECLARATIVE_MANAGEMENT_COMMAND.getRequestType(),
                false,
                policyId != null,
                policyId
        );
    }

    @Async
    @Override
    public void sendSettings(String deviceUdid, List<NSDictionary> settingsPayloads, boolean fromPolicy, UUID policyId) throws Exception {
        if (settingsPayloads == null || settingsPayloads.isEmpty()) {
            logger.warn("Settings payloads list is empty. Skipping for device: {}", deviceUdid);
            return;
        }

        logger.info("Sending Settings command to device: {} with {} items", deviceUdid, settingsPayloads.size());

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_SETTINGS_COMMAND.getRequestType(), fromPolicy, policyId);
        NSDictionary settingsCommand = appleCommandBuilderService.settings(settingsPayloads, commandUUID);

        // Register Settings command for compliance tracking if from policy
        if (fromPolicy) {
            policyComplianceTracker.registerCommand(deviceUdid, commandUUID, DEVICE_SETTINGS_COMMAND.getRequestType(), "Device Settings");
        }

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                settingsCommand,
                DEVICE_SETTINGS_COMMAND.getRequestType(),
                false,
                fromPolicy,
                policyId
        );

        logger.info("Settings command queued. UUID: {}", commandUUID);
    }

    @Async
    public void restartDevice(String deviceUdid, Boolean notifyUser) throws Exception {
        logger.info("Starting restart device process for device with UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Apple device with UDID: {} not found. Aborting restart.", deviceUdid);
            return;
        }
        AppleDevice device = deviceOpt.get();

        if (device.getDeviceProperties() != null
                && Boolean.FALSE.equals(device.getDeviceProperties().getSupervised())) {
            logger.warn("Device {} is NOT supervised. RestartDevice command requires supervision. Aborting.", deviceUdid);
            return;
        }

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_RESTART_COMMAND.getRequestType(), false, null);
        logger.debug("Generated command UUID: {} for restart device.", commandUUID);

        NSDictionary commandTemplate = appleCommandBuilderService.restartDevice(commandUUID, notifyUser);

        logger.info("Queuing RestartDevice command for UDID: {}", deviceUdid);
        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_RESTART_COMMAND.getRequestType(),
                false,
                false,
                null
        );

        logger.info("RestartDevice command queued successfully for device with UDID: {}", deviceUdid);
    }

    @Async
    public void lockDevice(String deviceUdid, String message, String phoneNumber) throws Exception {
        logger.info("Starting DeviceLock process for UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Apple device with UDID: {} not found. Aborting lock.", deviceUdid);
            return;
        }

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_LOCK_COMMAND.getRequestType(), false, null);

        NSDictionary commandTemplate = appleCommandBuilderService.deviceLock(message, phoneNumber, commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_LOCK_COMMAND.getRequestType(),
                false, false, null
        );
        logger.info("DeviceLock command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void shutDownDevice(String deviceUdid) throws Exception {
        logger.info("Starting ShutDown process for UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }
        AppleDevice device = deviceOpt.get();

        if (device.getDeviceProperties() != null
                && Boolean.FALSE.equals(device.getDeviceProperties().getSupervised())) {
            logger.warn("Device {} is NOT supervised. ShutDown command requires supervision.", deviceUdid);
            return;
        }

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_SHUTDOWN_COMMAND.getRequestType(), false, null);
        NSDictionary commandTemplate = appleCommandBuilderService.shutDownDevice(commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_SHUTDOWN_COMMAND.getRequestType(),
                false, false, null
        );
        logger.info("ShutDown command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void eraseDevice(String deviceUdid, String pin, boolean preserveDataPlan) throws Exception {
        logger.info("Starting EraseDevice (Wipe) process for UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_ERASE_COMMAND.getRequestType(), false, null);
        NSDictionary commandTemplate = appleCommandBuilderService.eraseDevice(commandUUID, pin, preserveDataPlan);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_ERASE_COMMAND.getRequestType(),
                false, false, null
        );
        logger.info("EraseDevice command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void syncAppInventory(String deviceUdid) throws Exception {
        logger.info("Starting App Inventory Sync for device: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }

        String installCmdUUID = createCommandUUID(deviceUdid, DEVICE_INSTALLED_APPLICATION_LIST_COMMAND.getRequestType(), false, null);
        NSDictionary installCmdPayload = appleCommandBuilderService.installedApplicationList(installCmdUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                installCmdUUID,
                installCmdPayload,
                DEVICE_INSTALLED_APPLICATION_LIST_COMMAND.getRequestType(),
                false, false, null
        );

        String managedCmdUUID = createCommandUUID(deviceUdid, DEVICE_MANAGED_APPLICATION_LIST_COMMAND.getRequestType(), false, null);
        NSDictionary managedCmdPayload = appleCommandBuilderService.managedApplicationList(managedCmdUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                managedCmdUUID,
                managedCmdPayload,
                DEVICE_MANAGED_APPLICATION_LIST_COMMAND.getRequestType(),
                false, false, null
        );

        logger.info("Queued both InstalledApplicationList and ManagedApplicationList for UDID: {}", deviceUdid);
    }

    @Async
    @Override
    public void deviceConfigured(String deviceUdid) throws Exception {
        logger.info("Starting DeviceConfigured process for UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}. Aborting DeviceConfigured.", deviceUdid);
            return;
        }

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_CONFIGURED_COMMAND.getRequestType(), false, null);
        NSDictionary commandTemplate = appleCommandBuilderService.deviceConfigured(commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_CONFIGURED_COMMAND.getRequestType(),
                false, false, null
        );
        logger.info("DeviceConfigured command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void securityInfo(String deviceUdid) throws Exception {
        logger.info("Sending SecurityInfo request to device: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_SECURITY_INFO_COMMAND.getRequestType(), false, null);

        NSDictionary commandPayload = appleCommandBuilderService.querySecurityInformation(commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandPayload,
                DEVICE_SECURITY_INFO_COMMAND.getRequestType(),
                false, false, null
        );

        logger.info("Queued SecurityInfo command for UDID: {}", deviceUdid);
    }

    @Async
    public void clearPasscode(String deviceUdid) throws Exception {
        logger.info("Starting ClearPasscode process for UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }

        AppleDevice device = deviceOpt.get();
        String unlockToken = device.getUnlockToken();

        // Check if we have the token (Required for iOS/iPadOS)
        if (unlockToken == null || unlockToken.isBlank()) {
            logger.error("Cannot send ClearPasscode: UnlockToken is missing for device {}. The device may not have provided one during enrollment.", deviceUdid);
            throw new IllegalStateException("UnlockToken is missing. Cannot clear passcode.");
        }

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_CLEAR_PASSCODE_COMMAND.getRequestType(), false, null);
        NSDictionary commandTemplate = appleCommandBuilderService.clearPasscode(unlockToken, commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_CLEAR_PASSCODE_COMMAND.getRequestType(),
                false,
                false,
                null
        );

        logger.info("ClearPasscode command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void clearRestrictionsPassword(String deviceUdid) throws Exception {
        logger.info("Starting ClearRestrictionsPassword process for UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }

        // Optional: Check if device is Supervised.
        // This command usually fails on unsupervised devices, but we can try anyway.
    /* AppleDevice device = deviceOpt.get();
    if (device.getDeviceProperties() != null && Boolean.FALSE.equals(device.getDeviceProperties().getSupervised())) {
         logger.warn("Device {} is not Supervised. ClearRestrictionsPassword may fail.", deviceUdid);
    }
    */

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_CLEAR_RESTRICTIONS_PASSWORD_COMMAND.getRequestType(), false, null);

        NSDictionary commandTemplate = appleCommandBuilderService.clearRestrictionsPassword(commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_CLEAR_RESTRICTIONS_PASSWORD_COMMAND.getRequestType(),
                false,
                false,
                null
        );

        logger.info("ClearRestrictionsPassword command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void enableLostMode(String deviceUdid, String message, String phoneNumber, String footnote) throws Exception {
        logger.info("Starting EnableLostMode process for UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }
        AppleDevice device = deviceOpt.get();

        // Safety Check: Lost Mode requires Supervision
        if (device.getDeviceProperties() != null
                && Boolean.FALSE.equals(device.getDeviceProperties().getSupervised())) {
            logger.error("Cannot enable Lost Mode: Device {} is NOT supervised.", deviceUdid);
            throw new IllegalStateException("EnableLostMode requires a Supervised device.");
        }

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_ENABLE_LOST_MODE_COMMAND.getRequestType(), false, null);

        NSDictionary commandTemplate = appleCommandBuilderService.enableLostMode(message, phoneNumber, footnote, commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_ENABLE_LOST_MODE_COMMAND.getRequestType(),
                false,
                false,
                null
        );

        queueDeviceInformationQuery(deviceUdid, false);

        logger.info("EnableLostMode command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void requestUserList(String deviceUdid) throws Exception {
        logger.info("Requesting UserList for UDID: {}", deviceUdid);

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_USER_LIST_COMMAND.getRequestType(), false, null);
        NSDictionary commandTemplate = appleCommandBuilderService.userList(commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_USER_LIST_COMMAND.getRequestType(),
                false,
                false,
                null
        );
    }

    @Async
    public void logOutUser(String deviceUdid) throws Exception {
        logger.info("Starting LogOutUser process for UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }

        String requestType = DEVICE_LOG_OUT_USER_COMMAND.getRequestType();
        String commandUUID = createCommandUUID(deviceUdid, requestType, false, null);
        NSDictionary commandTemplate = appleCommandBuilderService.logOutUser(commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                requestType,
                false,
                false,
                null
        );

        logger.info("LogOutUser command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void deleteUser(String deviceUdid, String userName, boolean forceDeletion) throws Exception {
        logger.info("Starting DeleteUser process for UDID: {}, UserName: {}", deviceUdid, userName);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }

        String requestType = DEVICE_DELETE_USER_COMMAND.getRequestType();
        String commandUUID = createCommandUUID(deviceUdid, requestType, false, null);
        NSDictionary commandTemplate = appleCommandBuilderService.deleteUser(userName, forceDeletion, commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                requestType,
                false,
                false,
                null
        );

        // Best Practice: Automatically request an updated UserList right after deleting a user
        // This ensures your database state matches the device state immediately.
        requestUserList(deviceUdid);

        logger.info("DeleteUser command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void requestDeviceLocation(String deviceUdid) throws Exception {
        logger.info("Requesting DeviceLocation for UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }

    /* Optimistic Check: You could check `device.getDeviceProperties().getMdmlostModeEnabled()` here.
       However, since we just sent the command, the DB might not be updated yet.
       We will send the command anyway; if it fails, Apple will return an error.
    */

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_LOCATION_COMMAND.getRequestType(), false, null);

        NSDictionary commandTemplate = appleCommandBuilderService.deviceLocation(commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_LOCATION_COMMAND.getRequestType(),
                false,
                false,
                null
        );
        logger.info("DeviceLocation command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void playLostModeSound(String deviceUdid) throws Exception {
        logger.info("Starting PlayLostModeSound process for UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }

    /* Note: This command will likely fail (return an error status) if the device
       is not currently in Lost Mode. You can check `device.getDeviceProperties().getMdmlostModeEnabled()`
       here to warn the admin, but we send the command anyway in case the DB is stale.
    */

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_PLAY_LOST_MODE_SOUND_COMMAND.getRequestType(), false, null);

        NSDictionary commandTemplate = appleCommandBuilderService.playLostModeSound(commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_PLAY_LOST_MODE_SOUND_COMMAND.getRequestType(),
                false,
                false,
                null
        );

        logger.info("PlayLostModeSound command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void disableLostMode(String deviceUdid) throws Exception {
        logger.info("Starting DisableLostMode process for UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found: {}", deviceUdid);
            return;
        }

        String commandUUID = createCommandUUID(deviceUdid, DEVICE_DISABLE_LOST_MODE_COMMAND.getRequestType(), false, null);

        NSDictionary commandTemplate = appleCommandBuilderService.disableLostMode(commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                DEVICE_DISABLE_LOST_MODE_COMMAND.getRequestType(),
                false,
                false,
                null
        );

        // Queue device info query AFTER DisableLostMode (sync to guarantee ordering)
        queueDeviceInformationQuery(deviceUdid, false);

        logger.info("DisableLostMode command queued for UDID: {}", deviceUdid);
    }

    @Async
    public void renameDevice(String deviceUdid, String newName) throws Exception {
        logger.info("Renaming device {} to {}", deviceUdid, newName);
        NSDictionary nameSetting = new NSDictionary();
        nameSetting.put("Item", "DeviceName");
        nameSetting.put("DeviceName", newName);

        this.sendSettings(deviceUdid, List.of(nameSetting), false, null);
        // Queue device info query AFTER Settings (sync to guarantee ordering)
        queueDeviceInformationQuery(deviceUdid, false);
    }

    @Async
    public void setTimeZone(String deviceUdid, String timeZoneName) throws Exception {
        logger.info("Setting TimeZone to {} for device {}", timeZoneName, deviceUdid);
        NSDictionary setting = new NSDictionary();
        setting.put("Item", "TimeZone");
        setting.put("TimeZone", timeZoneName);

        this.sendSettings(deviceUdid, List.of(setting), false, null);
    }

    @Async
    public void setHostname(String deviceUdid, String hostname) throws Exception {
        logger.info("Setting Hostname to {} for device {}", hostname, deviceUdid);
        NSDictionary setting = new NSDictionary();
        setting.put("Item", "HostName");
        setting.put("HostName", hostname);

        this.sendSettings(deviceUdid, List.of(setting), false, null);
    }

    @Async
    public void setBluetooth(String deviceUdid, boolean enabled) throws Exception {
        logger.info("Setting Bluetooth to {} for device {}", enabled, deviceUdid);
        NSDictionary setting = new NSDictionary();
        setting.put("Item", "Bluetooth");
        setting.put("Enabled", enabled);

        this.sendSettings(deviceUdid, List.of(setting), false, null);
    }

    @Async
    public void setDataRoaming(String deviceUdid, boolean enabled) throws Exception {
        logger.info("Setting Data Roaming to {} for device {}", enabled, deviceUdid);
        NSDictionary setting = new NSDictionary();
        setting.put("Item", "DataRoaming");
        setting.put("Enabled", enabled);

        this.sendSettings(deviceUdid, List.of(setting), false, null);
    }

    @Async
    public void setVoiceRoaming(String deviceUdid, boolean enabled) throws Exception {
        logger.info("Setting Voice Roaming to {} for device {}", enabled, deviceUdid);
        NSDictionary setting = new NSDictionary();
        setting.put("Item", "VoiceRoaming");
        setting.put("Enabled", enabled);

        this.sendSettings(deviceUdid, List.of(setting), false, null);
    }

    @Async
    public void setPersonalHotspot(String deviceUdid, boolean enabled) throws Exception {
        logger.info("Setting Personal Hotspot to {} for device {}", enabled, deviceUdid);
        NSDictionary setting = new NSDictionary();
        setting.put("Item", "PersonalHotspot");
        setting.put("Enabled", enabled);

        this.sendSettings(deviceUdid, List.of(setting), false, null);
    }

    @Async
    public void setDiagnosticSubmission(String deviceUdid, boolean enabled) throws Exception {
        logger.info("Setting DiagnosticSubmission to {} for device {}", enabled, deviceUdid);
        NSDictionary setting = new NSDictionary();
        setting.put("Item", "DiagnosticSubmission");
        setting.put("Enabled", enabled);

        this.sendSettings(deviceUdid, List.of(setting), false, null);
    }

    @Async
    public void setAppAnalytics(String deviceUdid, boolean enabled) throws Exception {
        logger.info("Setting AppAnalytics to {} for device {}", enabled, deviceUdid);
        NSDictionary setting = new NSDictionary();
        setting.put("Item", "AppAnalytics");
        setting.put("Enabled", enabled);

        this.sendSettings(deviceUdid, List.of(setting), false, null);
    }

    @Async
    public void setWallpaper(String deviceUdid, byte[] imageBytes, Integer where) throws Exception {
        logger.info("Setting wallpaper for device {}. Target screen: {}", deviceUdid, where);

        // 1. Build the Wallpaper Item
        NSDictionary wallpaperSetting = new NSDictionary();
        wallpaperSetting.put("Item", "Wallpaper");
        wallpaperSetting.put("Image", imageBytes); // dd-plist handles byte[] automatically

        // Where: 1 (Lock Screen), 2 (Home Screen), 3 (Both)
        if (where != null) {
            wallpaperSetting.put("Where", where);
        }

        // 2. Wrap and Send
        this.sendSettings(deviceUdid, List.of(wallpaperSetting), false, null);
    }

    @Async
    public void requestCertificateList(String deviceUdid) throws Exception {
        logger.info("Requesting CertificateList for UDID: {}", deviceUdid);

        String requestType = "CertificateList";
        String commandUUID = createCommandUUID(deviceUdid, requestType, false, null);
        NSDictionary commandTemplate = appleCommandBuilderService.certificateList(commandUUID);

        appleCommandQueueService.pushCommand(
                deviceUdid,
                commandUUID,
                commandTemplate,
                requestType,
                false,
                false,
                null
        );
    }

    // ********* Helper Methods ********

    /**
     * Builds managed app configuration for the agent app.
     * This Configuration dict is included in InstallApplication commands and becomes
     * available to the app via UserDefaults(suiteName: "com.apple.configuration.managed").
     */
    private NSDictionary buildManagedAppConfig(AppleDevice device) {
        // Derive the base server URL (strip /api/apple context path)
        String baseUrl = serverHost;
        if (baseUrl.endsWith("/api/apple")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/api/apple".length());
        }

        NSDictionary config = new NSDictionary();
        config.put("serverURL", baseUrl);
        config.put("organizationName", organizationName);

        String serial = device.getSerialNumber();
        if (serial != null && !serial.isBlank()) {
            config.put("deviceSerialNumber", serial);
        }

        logger.info("Built managed app config for device {}: serverURL={}, serial={}",
                device.getUdid(), baseUrl, serial);
        return config;
    }

    private String createCommandUUID(String deviceUdid, String commandType, boolean fromPolicy, UUID policyId) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        if (fromPolicy && policyId != null) {
            return deviceUdid + "_" + commandType + "_" + policyId + "_" + suffix;
        }
        return deviceUdid + "_" + commandType + "_" + suffix;
    }
}

package com.arcyintel.arcops.apple_mdm.services.apple.command;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.domains.AgentLocation;
import com.arcyintel.arcops.apple_mdm.domains.AgentTelemetry;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.domains.AppleDeviceApp;
import com.arcyintel.arcops.apple_mdm.domains.AppleDeviceInformation;
import com.arcyintel.arcops.apple_mdm.domains.AppleDeviceLocation;
import com.arcyintel.arcops.apple_mdm.event.publisher.DeviceEventPublisher;
import com.arcyintel.arcops.apple_mdm.event.publisher.PolicyEventPublisher;
import com.arcyintel.arcops.apple_mdm.repositories.*;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandHandlerService;
import com.arcyintel.arcops.commons.constants.apple.Os;
import com.arcyintel.arcops.commons.events.device.DeviceEnrolledEvent;
import com.arcyintel.arcops.commons.events.device.DeviceInformationChangedEvent;
import com.arcyintel.arcops.commons.events.policy.PolicyAppliedEvent;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.arcyintel.arcops.apple_mdm.models.enums.CommandTypes.DEVICE_INFO_COMMAND;
import static com.arcyintel.arcops.commons.constants.apple.MacosUserApps.shouldKeep;
import static com.arcyintel.arcops.commons.constants.apple.CommandSpecificConfigurations.APPLICATIONS_MANAGEMENT;
import static com.arcyintel.arcops.commons.constants.apple.CommandSpecificConfigurations.DECLARATIVE_MANAGEMENT;
import static com.arcyintel.arcops.commons.constants.policy.PolicyStatus.*;
import static com.arcyintel.arcops.commons.constants.policy.PolicyTypes.PAYLOAD;

@RequiredArgsConstructor
@Primary
@Service
public class AppleCommandHandlerServiceImpl implements AppleCommandHandlerService {

    private static final Logger logger = LoggerFactory.getLogger(AppleCommandHandlerServiceImpl.class);

    private final AppleDeviceRepository appleDeviceRepository;
    private final AppleDeviceInformationRepository appleDeviceInformationRepository;
    private final AppleDeviceAppRepository appleDeviceAppRepository;
    private final AppleDeviceLocationRepository appleDeviceLocationRepository;
    private final AgentTelemetryRepository agentTelemetryRepository;
    private final DeviceEventPublisher deviceEventPublisher;
    private final PolicyEventPublisher policyEventPublisher;
    private final PolicyRepository policyRepository;
    private final PolicyComplianceTracker policyComplianceTracker;
    private final AgentLocationRepository agentLocationRepository;

    @Override
    @Transactional
    public void updateDeviceInfo(String deviceUdid, HashMap<String, Object> deviceInformation) {
        logger.info("Starting update of device information for device with UDID: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device with UDID: {} not found. Skipping update of device information.", deviceUdid);
            return;
        }

        AppleDevice device = deviceOpt.get();
        logger.debug("Device with UDID: {} found. Building device information object.", deviceUdid);

        // Build or update device information and save
        Optional<AppleDeviceInformation> existingDeviceInfoOpt = appleDeviceInformationRepository.findByAppleDevice(device.getId());
        AppleDeviceInformation deviceInfo;

        if (existingDeviceInfoOpt.isPresent()) {
            logger.debug("Device information already exists for device with UDID: {}. Applying new values.", deviceUdid);
            deviceInfo = existingDeviceInfoOpt.get();
            applyDeviceInformation(deviceInfo, deviceInformation, device);
        } else {
            logger.debug("No existing device information found for device with UDID: {}. Building a new object.", deviceUdid);
            deviceInfo = buildDeviceInformation(deviceInformation, device);
        }

        appleDeviceInformationRepository.saveAndFlush(deviceInfo);
        logger.debug("Device information object successfully saved for device with UDID: {}", deviceUdid);

        device.setDeviceProperties(deviceInfo);

        if (appleDeviceRepository.existsByIdAndAppleCommands_CommandType(device.getId(), DEVICE_INFO_COMMAND.getRequestType())) {

            String platform = Os.IOS;
            String modelName = deviceInfo.getModelName();
            if (modelName.startsWith("Mac")) {
                platform = Os.MACOS;
            } else if (modelName.startsWith("Watch")) {
                platform = Os.WATCHOS;
            }

            logger.info("Publishing device event for the new device with UDID '{}'.", deviceUdid);
            deviceEventPublisher.publishDeviceEnrolledEvent(new DeviceEnrolledEvent(device.getId(), new HashMap<>(), platform));
            logger.info("Device event published successfully for the new device with UDID '{}'.", deviceUdid);
        }

        logger.info("Publishing device information update event for device with UDID: {}", deviceUdid);
        Map<String, Object> props = deviceInfo.toMap();
        props.put("serialNumber", device.getSerialNumber());
        DeviceInformationChangedEvent event = new DeviceInformationChangedEvent(device.getId(), props);
        event.setAgentInfo(buildAgentInfoMap(device));
        deviceEventPublisher.publishDeviceInformationChangedEvent(event);
        logger.info("Device information update event published successfully for device with UDID: {}", deviceUdid);
    }


    private void applyDeviceInformation(AppleDeviceInformation deviceInformationEntity,
                                        HashMap<String, Object> info,
                                        AppleDevice device) {
        deviceInformationEntity.setAppleDevice(device);
        deviceInformationEntity.setAppAnalyticsEnabled(info.get("AppAnalyticsEnabled") != null ? (boolean) info.get("AppAnalyticsEnabled") : null);
        deviceInformationEntity.setAwaitingConfiguration(info.get("AwaitingConfiguration") != null ? (boolean) info.get("AwaitingConfiguration") : null);
        deviceInformationEntity.setBatteryLevel(info.get("BatteryLevel") != null ? (Number) info.get("BatteryLevel") : null);
        deviceInformationEntity.setBluetoothMAC(info.get("BluetoothMAC") != null ? (String) info.get("BluetoothMAC") : null);
        deviceInformationEntity.setBuildVersion(info.get("BuildVersion") != null ? (String) info.get("BuildVersion") : null);
        deviceInformationEntity.setCellularTechnology(info.get("CellularTechnology") != null ? (int) info.get("CellularTechnology") : null);
        deviceInformationEntity.setDataRoamingEnabled(info.get("DataRoamingEnabled") != null ? (boolean) info.get("DataRoamingEnabled") : null);
        deviceInformationEntity.setDeviceCapacity(info.get("DeviceCapacity") != null ? (Number) info.get("DeviceCapacity") : null);
        deviceInformationEntity.setDeviceName(info.get("DeviceName") != null ? (String) info.get("DeviceName") : null);
        deviceInformationEntity.setDiagnosticSubmissionEnabled(info.get("DiagnosticSubmissionEnabled") != null ? (boolean) info.get("DiagnosticSubmissionEnabled") : null);
        deviceInformationEntity.setEasDeviceIdentifier(info.get("EASDeviceIdentifier") != null ? (String) info.get("EASDeviceIdentifier") : null);
        deviceInformationEntity.setImei(info.get("IMEI") != null ? (String) info.get("IMEI") : null);
        deviceInformationEntity.setActivationLockEnabled(info.get("IsActivationLockEnabled") != null ? (boolean) info.get("IsActivationLockEnabled") : null);
        deviceInformationEntity.setCloudBackupEnabled(info.get("IsCloudBackupEnabled") != null ? (boolean) info.get("IsCloudBackupEnabled") : null);
        deviceInformationEntity.setDeviceLocatorServiceEnabled(info.get("IsDeviceLocatorServiceEnabled") != null ? (boolean) info.get("IsDeviceLocatorServiceEnabled") : null);
        deviceInformationEntity.setDoNotDisturbInEffect(info.get("IsDoNotDisturbInEffect") != null ? (boolean) info.get("IsDoNotDisturbInEffect") : null);
        deviceInformationEntity.setMdmlostModeEnabled(info.get("IsMDMLostModeEnabled") != null ? (boolean) info.get("IsMDMLostModeEnabled") : null);
        deviceInformationEntity.setMultiUser(info.get("IsMultiUser") != null ? (boolean) info.get("IsMultiUser") : null);
        deviceInformationEntity.setNetworkTethered(info.get("IsNetworkTethered") != null ? (boolean) info.get("IsNetworkTethered") : null);
        deviceInformationEntity.setRoaming(info.get("IsRoaming") != null ? (boolean) info.get("IsRoaming") : null);
        deviceInformationEntity.setSupervised(info.get("IsSupervised") != null ? (boolean) info.get("IsSupervised") : null);
        deviceInformationEntity.setMeid(info.get("MEID") != null ? (String) info.get("MEID") : null);
        deviceInformationEntity.setModelName(info.get("ModelName") != null ? (String) info.get("ModelName") : null);
        deviceInformationEntity.setModemFirmwareVersion(info.get("ModemFirmwareVersion") != null ? (String) info.get("ModemFirmwareVersion") : null);
        deviceInformationEntity.setOsVersion(info.get("OSVersion") != null ? (String) info.get("OSVersion") : null);
        deviceInformationEntity.setPersonalHotspotEnabled(info.get("PersonalHotspotEnabled") != null ? (boolean) info.get("PersonalHotspotEnabled") : null);
        deviceInformationEntity.setProductName(info.get("ProductName") != null ? (String) info.get("ProductName") : null);
        deviceInformationEntity.setSubscriberMCC(info.get("SubscriberMCC") != null ? extractSubscriberInfo(info, "SubscriberMCC") : null);
        deviceInformationEntity.setSubscriberMNC(info.get("SubscriberMNC") != null ? extractSubscriberInfo(info, "SubscriberMNC") : null);
        deviceInformationEntity.setUdid(info.get("UDID") != null ? (String) info.get("UDID") : null);
        deviceInformationEntity.setVoiceRoamingEnabled(info.get("VoiceRoamingEnabled") != null ? (boolean) info.get("VoiceRoamingEnabled") : null);
        deviceInformationEntity.setWifiMAC(info.get("WiFiMAC") != null ? (String) info.get("WiFiMAC") : null);
        deviceInformationEntity.setItunesStoreAccountIsActive(info.get("iTunesStoreAccountIsActive") != null ? (boolean) info.get("iTunesStoreAccountIsActive") : null);
    }

    /**
     * Builds Apple device information from device data
     */
    private AppleDeviceInformation buildDeviceInformation(HashMap<String, Object> info, AppleDevice device) {
        logger.debug("Building AppleDeviceInformation object from provided device information map.");

        AppleDeviceInformation deviceInformation = AppleDeviceInformation.builder()
                .appleDevice(device)
                .appAnalyticsEnabled(info.get("AppAnalyticsEnabled") != null ? (boolean) info.get("AppAnalyticsEnabled") : null)
                .awaitingConfiguration(info.get("AwaitingConfiguration") != null ? (boolean) info.get("AwaitingConfiguration") : null)
                .batteryLevel(info.get("BatteryLevel") != null ? (Number) info.get("BatteryLevel") : null)
                .bluetoothMAC(info.get("BluetoothMAC") != null ? (String) info.get("BluetoothMAC") : null)
                .buildVersion(info.get("BuildVersion") != null ? (String) info.get("BuildVersion") : null)
                .cellularTechnology(info.get("CellularTechnology") != null ? (int) info.get("CellularTechnology") : null)
                .dataRoamingEnabled(info.get("DataRoamingEnabled") != null ? (boolean) info.get("DataRoamingEnabled") : null)
                .deviceCapacity(info.get("DeviceCapacity") != null ? (Number) info.get("DeviceCapacity") : null)
                .deviceName(info.get("DeviceName") != null ? (String) info.get("DeviceName") : null)
                .diagnosticSubmissionEnabled(info.get("DiagnosticSubmissionEnabled") != null ? (boolean) info.get("DiagnosticSubmissionEnabled") : null)
                .easDeviceIdentifier(info.get("EASDeviceIdentifier") != null ? (String) info.get("EASDeviceIdentifier") : null)
                .imei(info.get("IMEI") != null ? (String) info.get("IMEI") : null)
                .activationLockEnabled(info.get("IsActivationLockEnabled") != null ? (boolean) info.get("IsActivationLockEnabled") : null)
                .cloudBackupEnabled(info.get("IsCloudBackupEnabled") != null ? (boolean) info.get("IsCloudBackupEnabled") : null)
                .deviceLocatorServiceEnabled(info.get("IsDeviceLocatorServiceEnabled") != null ? (boolean) info.get("IsDeviceLocatorServiceEnabled") : null)
                .doNotDisturbInEffect(info.get("IsDoNotDisturbInEffect") != null ? (boolean) info.get("IsDoNotDisturbInEffect") : null)
                .mdmlostModeEnabled(info.get("IsMDMLostModeEnabled") != null ? (boolean) info.get("IsMDMLostModeEnabled") : null)
                .multiUser(info.get("IsMultiUser") != null ? (boolean) info.get("IsMultiUser") : null)
                .networkTethered(info.get("IsNetworkTethered") != null ? (boolean) info.get("IsNetworkTethered") : null)
                .roaming(info.get("IsRoaming") != null ? (boolean) info.get("IsRoaming") : null)
                .supervised(info.get("IsSupervised") != null ? (boolean) info.get("IsSupervised") : null)
                .meid(info.get("MEID") != null ? (String) info.get("MEID") : null)
                .modelName(info.get("ModelName") != null ? (String) info.get("ModelName") : null)
                .modemFirmwareVersion(info.get("ModemFirmwareVersion") != null ? (String) info.get("ModemFirmwareVersion") : null)
                .osVersion(info.get("OSVersion") != null ? (String) info.get("OSVersion") : null)
                .personalHotspotEnabled(info.get("PersonalHotspotEnabled") != null ? (boolean) info.get("PersonalHotspotEnabled") : null)
                .productName(info.get("ProductName") != null ? (String) info.get("ProductName") : null)
                .subscriberMCC(info.get("SubscriberMCC") != null ? extractSubscriberInfo(info, "SubscriberMCC") : null)
                .subscriberMNC(info.get("SubscriberMNC") != null ? extractSubscriberInfo(info, "SubscriberMNC") : null)
                .udid(info.get("UDID") != null ? (String) info.get("UDID") : null)
                .voiceRoamingEnabled(info.get("VoiceRoamingEnabled") != null ? (boolean) info.get("VoiceRoamingEnabled") : null)
                .wifiMAC(info.get("WiFiMAC") != null ? (String) info.get("WiFiMAC") : null)
                .itunesStoreAccountIsActive(info.get("iTunesStoreAccountIsActive") != null ? (boolean) info.get("iTunesStoreAccountIsActive") : null)
                .build();

        logger.debug("AppleDeviceInformation object successfully built: {}", deviceInformation);

        return deviceInformation;
    }

    private String extractSubscriberInfo(HashMap<String, Object> info, String key) {
        Object[] subscriptions = (Object[]) info.get("ServiceSubscriptions");
        if (subscriptions != null && subscriptions.length > 0) {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> subscription = (HashMap<String, Object>) subscriptions[0];
            return (String) subscription.get(key);
        }
        return null;
    }

    @Override
    public void handleCommandSuccess(String deviceUdid, String commandUuid, String commandType) {
        logger.info("Handling command success for device UDID: {}, command UUID: {}, type: {}", deviceUdid, commandUuid, commandType);
        policyComplianceTracker.markCommandSuccess(deviceUdid, commandUuid);
        checkAndFinalizeCompliance(deviceUdid);
    }

    @Override
    public void handleCommandFailure(String deviceUdid, String commandUuid, String commandType, String errorMessage) {
        logger.warn("Handling command failure for device UDID: {}, command UUID: {}, type: {}, error: {}", deviceUdid, commandUuid, commandType, errorMessage);
        policyComplianceTracker.markCommandFailure(deviceUdid, commandUuid, errorMessage);
        checkAndFinalizeCompliance(deviceUdid);
    }

    private void checkAndFinalizeCompliance(String deviceUdid) {
        if (policyComplianceTracker.isTrackingComplete(deviceUdid)) {
            PolicyComplianceTracker.ComplianceResult result = policyComplianceTracker.getComplianceResult(deviceUdid);
            logger.info("Policy compliance tracking complete for device UDID: {}. Compliant: {}", deviceUdid, result.isCompliant());

            // Update device compliance status in DB
            appleDeviceRepository.findByUdid(deviceUdid).ifPresent(device -> {
                device.setIsCompliant(result.isCompliant());
                if (!result.isCompliant() && result.failures() != null && !result.failures().isEmpty()) {
                    device.setComplianceFailures(result.failures());
                    logger.warn("Device {} is NON-COMPLIANT. Failures: {}", deviceUdid, result.failures().keySet());
                } else {
                    device.setComplianceFailures(null);
                }
                appleDeviceRepository.save(device);
                logger.info("Device {} compliance status updated: isCompliant={}", deviceUdid, result.isCompliant());
            });

            policyComplianceTracker.clearTracking(deviceUdid);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleInstallProfileCommand(String deviceUdid, String commandUuid) {

        logger.info("Handling install profile command for device with UDID: {}", deviceUdid);

        // Mark command as successful in compliance tracking
        policyComplianceTracker.markCommandSuccess(deviceUdid, commandUuid);

        // Find device by udid
        logger.debug("Fetching device with UDID: {} from the repository.", deviceUdid);
        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device with UDID: {} not found. Aborting install profile command.", deviceUdid);
            return;
        }

        // Get applied policy
        AppleDevice device = deviceOpt.get();
        logger.debug("Device with UDID: {} found. Retrieving applied policy.", deviceUdid);
        Map<String, Object> appliedPolicy = device.getAppliedPolicy();

        // Update applied policies status
        if (appliedPolicy != null && !appliedPolicy.isEmpty()) {
            logger.debug("Applied policy found for device with UDID: {}. Checking for iOS policies.", deviceUdid);

            // Check if policy is applied for iOS
            if (appliedPolicy.containsKey(PAYLOAD)) {
                logger.debug("iOS policy found for device with UDID: {}. Updating policy status to INSTALLED.", deviceUdid);
                Map<String, Object> payload = (Map<String, Object>) appliedPolicy.get(PAYLOAD);
                for (Map.Entry<String, Object> entry : payload.entrySet()) {
                    if (!(entry.getValue() instanceof Map<?, ?>)) continue;

                    // Skip declarativeManagement — it is system-generated, not a user config
                    if (DECLARATIVE_MANAGEMENT.equals(entry.getKey())) continue;

                    // Get the configuration
                    Map<String, Object> configuration = (Map<String, Object>) entry.getValue();
                    logger.debug("Updating configuration for policy key: {} to status INSTALLED.", entry.getKey());
                    configuration.put(STATUS, STATUS_INSTALLED);

                    // Set the policy name to the policy key
                    String policyId = (String) configuration.get("policyId");
                    if (policyId != null) {
                        policyRepository.findById(UUID.fromString(policyId)).ifPresent(policy -> {
                            configuration.put("policyName", policy.getName());
                        });
                    }

                }
            } else {
                logger.info("No iOS policy found for device with UDID: {}. Skipping policy update.", deviceUdid);
            }
        } else {
            logger.info("No applied policy found for device with UDID: {}. Skipping policy update.", deviceUdid);
        }

        // Save device
        logger.info("Saving updated device information for device with UDID: {}", deviceUdid);
        appleDeviceRepository.save(device);
        logger.info("Install profile command successfully handled for device with UDID: {}", deviceUdid);

        // Publish event
        logger.info("Publishing device information changed event for device with UDID: {}", deviceUdid);
        AppleDeviceInformation deviceInfo = device.getDeviceProperties();
        if (deviceInfo != null) {
            Map<String, Object> infoProps = deviceInfo.toMap();
            infoProps.put("serialNumber", device.getSerialNumber());
            DeviceInformationChangedEvent infoEvent = new DeviceInformationChangedEvent(device.getId(), infoProps);
            infoEvent.setAgentInfo(buildAgentInfoMap(device));
            deviceEventPublisher.publishDeviceInformationChangedEvent(infoEvent);
        }

        logger.info("Publishing policy applied event for device with ID: {}", device.getId());
        policyEventPublisher.publishPolicyAppliedEvent(new PolicyAppliedEvent(device.getId(), "Apple", appliedPolicy));
        logger.info("Policy applied event published successfully for device with ID: {}", device.getId());

        // Check if all compliance tracking is complete
        checkAndFinalizeCompliance(deviceUdid);

        logger.info("Device information changed event published successfully for device with UDID: {}", deviceUdid);
    }

    @Override
    @Transactional
    public void handleUserListResponse(String udid, Object[] users) {
        logger.info("Handling UserList response for UDID: {}", udid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(udid);
        if (deviceOpt.isEmpty()) return;

        AppleDevice device = deviceOpt.get();
        AppleDeviceInformation deviceInfo = appleDeviceInformationRepository.findByAppleDevice(device.getId())
                .orElse(new AppleDeviceInformation());
        if (deviceInfo.getAppleDevice() == null) {
            deviceInfo.setAppleDevice(device);
        }

        // Normalize data to ensure it is JSON-safe (using your existing helper)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> userList = (List<Map<String, Object>>) normalizeForJson(users);

        deviceInfo.setUserList(userList);
        appleDeviceInformationRepository.save(deviceInfo);

        // Optionally, publish the device info changed event to refresh the UI
        Map<String, Object> props = deviceInfo.toMap();
        props.put("serialNumber", device.getSerialNumber());
        DeviceInformationChangedEvent event = new DeviceInformationChangedEvent(device.getId(), props);
        event.setAgentInfo(buildAgentInfoMap(device));
        deviceEventPublisher.publishDeviceInformationChangedEvent(event);

        logger.info("Saved {} users for device {}", userList.size(), udid);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleFailedInstallProfileCommand(String deviceUdid, String commandUuid, String errorMessage) {

        logger.info("Handling failed install profile command for device with UDID: {}", deviceUdid);

        // Mark command as failed in compliance tracking
        policyComplianceTracker.markCommandFailure(deviceUdid, commandUuid, errorMessage != null ? errorMessage : "Install profile failed");

        // Find device by udid
        logger.debug("Fetching device with UDID: {} from the repository.", deviceUdid);
        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device with UDID: {} not found. Aborting failed install profile command.", deviceUdid);
            return;
        }

        // Get applied policy
        AppleDevice device = deviceOpt.get();
        logger.debug("Device with UDID: {} found. Retrieving applied policy.", deviceUdid);
        Map<String, Object> appliedPolicy = device.getAppliedPolicy();

        // Update applied policies status to FAILED
        if (appliedPolicy != null && !appliedPolicy.isEmpty()) {
            logger.debug("Applied policy found for device with UDID: {}. Checking for iOS policies.", deviceUdid);

            if (appliedPolicy.containsKey(PAYLOAD)) {
                logger.debug("iOS policy found for device with UDID: {}. Updating policy status to FAILED.", deviceUdid);
                Map<String, Object> payload = (Map<String, Object>) appliedPolicy.get(PAYLOAD);
                for (Map.Entry<String, Object> entry : payload.entrySet()) {

                    // Get the configuration
                    Map<String, Object> configuration = (Map<String, Object>) entry.getValue();
                    logger.debug("Updating configuration for policy key: {} to status FAILED.", entry.getKey());
                    configuration.put(STATUS, STATUS_FAILED);

                    // Set the policy name if possible
                    String policyId = (String) configuration.get("policyId");
                    if (policyId != null) {
                        policyRepository.findById(UUID.fromString(policyId)).ifPresent(policy -> {
                            configuration.put("policyName", policy.getName());
                        });
                    }
                }

            } else {
                logger.info("No iOS policy found for device with UDID: {}. Skipping policy update.", deviceUdid);
            }
        } else {
            logger.info("No applied policy found for device with UDID: {}. Skipping policy update.", deviceUdid);
        }

        // Save device
        logger.info("Saving updated device information for device with UDID: {}", deviceUdid);
        appleDeviceRepository.save(device);
        logger.info("Failed install profile command successfully handled for device with UDID: {}", deviceUdid);

        // Check if all compliance tracking is complete
        checkAndFinalizeCompliance(deviceUdid);
    }

    /**
     * Processes the response from the "InstalledApplicationList" command.
     * Strategy: Synchronization (Upsert + Delete Orphans).
     * Updates existing apps, creates new ones, and deletes apps that are no longer installed.
     */
    @Transactional
    public void handleInstalledApplicationList(String deviceUdid, Object[] appList) {
        logger.info("Processing installed application list for device: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found for UDID: {}", deviceUdid);
            return;
        }
        AppleDevice device = deviceOpt.get();

        // Fetch all currently existing apps from DB to avoid N+1 queries
        List<AppleDeviceApp> existingApps = appleDeviceAppRepository.findAllByAppleDevice_Id(device.getId());

        // Create a map for fast lookup: BundleIdentifier -> AppleDeviceApp
        Map<String, AppleDeviceApp> appMap = existingApps.stream()
                .collect(Collectors.toMap(AppleDeviceApp::getBundleIdentifier, app -> app));

        List<AppleDeviceApp> appsToSave = new ArrayList<>();
        Set<String> incomingBundleIds = new HashSet<>();

        // Detect if this is a macOS device to filter system apps
        boolean isMacOS = Optional.ofNullable(device.getProductName()).orElse("")
                .toLowerCase(Locale.ROOT).contains("mac");

        // Iterate through the incoming list from the device
        for (Object appData : appList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> appDataObj = (Map<String, Object>) appData;

            String bundleId = (String) appDataObj.getOrDefault("Identifier", "unknown.bundle.id");

            // On macOS, skip system/helper apps not in the user-facing whitelist
            if (isMacOS && !shouldKeep(bundleId)) {
                continue;
            }

            incomingBundleIds.add(bundleId);

            // Get existing entity or create a new one
            AppleDeviceApp app = appMap.getOrDefault(bundleId, new AppleDeviceApp());

            if (app.getId() == null) {
                app.setAppleDevice(device);
                app.setBundleIdentifier(bundleId);
            }

            app.setBundleSize((int) appDataObj.getOrDefault("BundleSize", 0));
            app.setInstalling((boolean) appDataObj.getOrDefault("Installing", false));
            app.setName((String) appDataObj.getOrDefault("Name", "Unknown App"));
            app.setShortVersion((String) appDataObj.getOrDefault("ShortVersion", ""));
            app.setVersion((String) appDataObj.getOrDefault("Version", ""));

            appsToSave.add(app);
        }

        appleDeviceAppRepository.saveAll(appsToSave);

        // Identify and delete apps that are in DB but NOT in the new list (orphans)
        List<AppleDeviceApp> appsToDelete = existingApps.stream()
                .filter(app -> !incomingBundleIds.contains(app.getBundleIdentifier()))
                .collect(Collectors.toList());

        if (!appsToDelete.isEmpty()) {
            appleDeviceAppRepository.deleteAll(appsToDelete);
            logger.info("Deleted {} removed apps for device {}", appsToDelete.size(), deviceUdid);
        }

        logger.info("Saved/Updated {} apps for device {}", appsToSave.size(), deviceUdid);
    }

    /**
     * Processes the response from the "ManagedApplicationList" command.
     * Strategy: Update Existing.
     * Matches existing installed apps and marks them as managed/configured.
     */
    @Override
    @Transactional
    public void handleManagedApplicationList(String deviceUdid, Map<String, Object> managedAppsMap) {
        logger.info("Processing managed application list for device: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found for UDID: {}", deviceUdid);
            return;
        }

        AppleDevice device = deviceOpt.get();

        List<AppleDeviceApp> existingApps = appleDeviceAppRepository.findAllByAppleDevice_Id(device.getId());
        Map<String, AppleDeviceApp> appMap = existingApps.stream()
                .collect(Collectors.toMap(AppleDeviceApp::getBundleIdentifier, app -> app));

        // Reset managed flag for all existing apps — ManagedApplicationList is the source of truth
        for (AppleDeviceApp existingApp : existingApps) {
            if (existingApp.isManaged()) {
                existingApp.setManaged(false);
            }
        }

        Set<String> managedBundleIds = managedAppsMap.keySet();
        List<AppleDeviceApp> appsToUpdate = new ArrayList<>(existingApps);

        for (Map.Entry<String, Object> entry : managedAppsMap.entrySet()) {
            String bundleId = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) entry.getValue();

            AppleDeviceApp app = appMap.get(bundleId);

            if (app == null) {
                // Edge Case: The app is managed but hasn't appeared in the "Installed" list yet (e.g., installing).
                // We create a placeholder record.
                logger.debug("Managed app {} not found in installed list, creating new record.", bundleId);
                app = new AppleDeviceApp();
                app.setAppleDevice(device);
                app.setBundleIdentifier(bundleId);
                app.setName(bundleId);
                appsToUpdate.add(app);
            }

            // Any app present in ManagedApplicationList is managed by MDM
            app.setManaged(true);

            if (properties.get("HasConfiguration") instanceof Boolean b) {
                app.setHasConfiguration(b);
            }

            if (properties.get("HasFeedback") instanceof Boolean b) {
                app.setHasFeedback(b);
            }

            if (properties.get("IsValidated") instanceof Boolean b) {
                app.setValidated(b);
            }

            if (properties.get("ManagementFlags") instanceof Number n) {
                app.setManagementFlags(n.intValue());
            }
        }

        appleDeviceAppRepository.saveAll(appsToUpdate);
        logger.info("Updated managed status for {} apps ({} managed) on device {}",
                appsToUpdate.size(), managedBundleIds.size(), deviceUdid);
    }

    @Override
    @Transactional
    public void handleSecurityInfoResponse(String deviceUdid, Map<String, Object> securityInfoMap) {
        logger.info("Processing SecurityInfo response for device: {}", deviceUdid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceUdid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found for UDID: {}", deviceUdid);
            return;
        }
        AppleDevice device = deviceOpt.get();

        AppleDeviceInformation deviceInfo = appleDeviceInformationRepository.findByAppleDevice(device.getId())
                .orElse(new AppleDeviceInformation());

        if (deviceInfo.getAppleDevice() == null) {
            deviceInfo.setAppleDevice(device);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = (Map<String, Object>) normalizeForJson(securityInfoMap);
        deviceInfo.setSecurityInfo(normalized);

        appleDeviceInformationRepository.save(deviceInfo);

        Map<String, Object> secProps = deviceInfo.toMap();
        secProps.put("serialNumber", device.getSerialNumber());
        DeviceInformationChangedEvent secEvent = new DeviceInformationChangedEvent(device.getId(), secProps);
        secEvent.setAgentInfo(buildAgentInfoMap(device));
        deviceEventPublisher.publishDeviceInformationChangedEvent(secEvent);

        logger.info("Updated SecurityInfo JSON for device {}", deviceUdid);
    }

    @Override
    @Transactional
    public void handleDeviceLocationResponse(String udid, NSDictionary response) {
        logger.info("Handling DeviceLocation response for UDID: {}", udid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(udid);
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found for UDID: {}", udid);
            return;
        }

        try {
            AppleDeviceLocation location = new AppleDeviceLocation();
            location.setAppleDevice(deviceOpt.get());

            // Parse fields safely (Apple sends them as 'Real' numbers)
            if (response.containsKey("Latitude")) location.setLatitude(((Number) response.get("Latitude").toJavaObject()).doubleValue());
            if (response.containsKey("Longitude")) location.setLongitude(((Number) response.get("Longitude").toJavaObject()).doubleValue());
            if (response.containsKey("Altitude")) location.setAltitude(((Number) response.get("Altitude").toJavaObject()).doubleValue());
            if (response.containsKey("Speed")) location.setSpeed(((Number) response.get("Speed").toJavaObject()).doubleValue());
            if (response.containsKey("Course")) location.setCourse(((Number) response.get("Course").toJavaObject()).doubleValue());
            if (response.containsKey("HorizontalAccuracy")) location.setHorizontalAccuracy(((Number) response.get("HorizontalAccuracy").toJavaObject()).doubleValue());
            if (response.containsKey("VerticalAccuracy")) location.setVerticalAccuracy(((Number) response.get("VerticalAccuracy").toJavaObject()).doubleValue());

            // Parse Timestamp (ISO 8601 string)
            if (response.containsKey("Timestamp")) {
                String tsStr = response.get("Timestamp").toString();
                location.setTimestamp(Instant.parse(tsStr));
            } else {
                location.setTimestamp(Instant.now());
            }

            appleDeviceLocationRepository.save(location);

            // Also save to agent_location with source=MDM_LOST_MODE for the device detail API
            AgentLocation agentLoc = AgentLocation.builder()
                    .device(deviceOpt.get())
                    .deviceIdentifier(udid)
                    .latitude(location.getLatitude())
                    .longitude(location.getLongitude())
                    .altitude(location.getAltitude())
                    .horizontalAccuracy(location.getHorizontalAccuracy())
                    .verticalAccuracy(location.getVerticalAccuracy())
                    .speed(location.getSpeed())
                    .course(location.getCourse())
                    .deviceCreatedAt(location.getTimestamp() != null ? location.getTimestamp() : Instant.now())
                    .source("MDM_LOST_MODE")
                    .build();
            agentLocationRepository.save(agentLoc);

            logger.info("Device location saved successfully for UDID: {}", udid);

        } catch (Exception e) {
            logger.error("Failed to parse or save device location for UDID: {}", udid, e);
        }
    }

    @Override
    @Transactional
    public void handleCertificateListResponse(String udid, Object[] certificates) {
        logger.info("Handling CertificateList response for UDID: {}", udid);

        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(udid);
        if (deviceOpt.isEmpty()) return;

        AppleDevice device = deviceOpt.get();
        AppleDeviceInformation deviceInfo = appleDeviceInformationRepository.findByAppleDevice(device.getId())
                .orElse(new AppleDeviceInformation());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> certDataList = (List<Map<String, Object>>) normalizeForJson(certificates);

        deviceInfo.setCertificateList(certDataList);
        appleDeviceInformationRepository.save(deviceInfo);

        logger.info("Saved {} certificates for device {}", certDataList.size(), udid);
    }

    private Map<String, Object> buildAgentInfoMap(AppleDevice device) {
        Map<String, Object> agentInfo = new LinkedHashMap<>();

        agentInfo.put("online", Boolean.TRUE.equals(device.getAgentOnline()));
        agentInfo.put("last_seen_at", device.getAgentLastSeenAt() != null ? device.getAgentLastSeenAt().toString() : null);
        agentInfo.put("agent_version", device.getAgentVersion());
        agentInfo.put("agent_platform", device.getAgentPlatform());

        Optional<AgentTelemetry> telemetryOpt = agentTelemetryRepository
                .findFirstByDeviceIdentifierOrderByDeviceCreatedAtDesc(device.getUdid());

        if (telemetryOpt.isPresent()) {
            AgentTelemetry t = telemetryOpt.get();
            agentInfo.put("ip_address", t.getIpAddress());
            agentInfo.put("wifi_ssid", t.getWifiSsid());
            agentInfo.put("network_type", t.getNetworkType());
            agentInfo.put("vpn_active", t.getVpnActive());
            agentInfo.put("carrier_name", t.getCarrierName());
            agentInfo.put("storage_total_bytes", t.getStorageTotalBytes());
            agentInfo.put("storage_free_bytes", t.getStorageFreeBytes());
            agentInfo.put("storage_used_percent", t.getStorageUsagePercent());
            agentInfo.put("jailbreak_detected", t.getJailbreakDetected());
        }

        return agentInfo;
    }

    /**
     * Recursively converts plist-origin Java objects into clean JSON-friendly types.
     * <ul>
     *   <li>Map → LinkedHashMap with recursively normalized values</li>
     *   <li>Object[] → ArrayList with recursively normalized elements</li>
     *   <li>byte[] → Base64-encoded String</li>
     *   <li>Date → ISO-8601 String</li>
     *   <li>String, Number, Boolean, null → kept as-is</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private Object normalizeForJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), normalizeForJson(entry.getValue()));
            }
            return result;
        }
        if (value instanceof Object[] arr) {
            return Stream.of(arr).map(this::normalizeForJson).collect(Collectors.toList());
        }
        if (value instanceof Collection<?> col) {
            return col.stream().map(this::normalizeForJson).collect(Collectors.toList());
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        // String, Number, Boolean — already JSON-safe
        return value;
    }
}

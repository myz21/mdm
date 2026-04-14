package com.arcyintel.arcops.apple_mdm.services.apple.policy;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;
import com.arcyintel.arcops.apple_mdm.domains.*;
import com.arcyintel.arcops.apple_mdm.services.apple.command.PolicyComplianceTracker;
import com.arcyintel.arcops.apple_mdm.services.apple.command.strategy.PlatformPayloadStrategy;
import com.arcyintel.arcops.apple_mdm.models.profile.PolicyContext;
import com.arcyintel.arcops.apple_mdm.models.profile.Profile;
import com.arcyintel.arcops.apple_mdm.models.profile.Settings;
import com.arcyintel.arcops.apple_mdm.repositories.AppGroupRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.repositories.EnterpriseAppRepository;
import com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandBuilderService;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandQueueService;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandSenderService;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentCommandService;
import com.arcyintel.arcops.apple_mdm.services.apple.policy.ApplePolicyApplicationService;
import com.arcyintel.arcops.apple_mdm.event.publisher.PolicyEventPublisher;
import com.arcyintel.arcops.commons.constants.apple.ApplePlatform;
import com.arcyintel.arcops.commons.events.policy.PolicyAppliedEvent;
import com.arcyintel.arcops.commons.constants.apple.ManagementChannel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.*;

import static com.arcyintel.arcops.apple_mdm.models.enums.CommandTypes.DEVICE_INSTALL_PROFILE_COMMAND;
import static com.arcyintel.arcops.apple_mdm.models.profile.Profile.PROFILE_IDENTIFIER;
import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.*;
import static com.arcyintel.arcops.commons.constants.apple.CommandSpecificConfigurations.*;
import static com.arcyintel.arcops.commons.constants.policy.PolicyStatus.POLICY_ID;
import static com.arcyintel.arcops.commons.constants.policy.PolicyTypes.*;

@SuppressWarnings("unchecked")
@RequiredArgsConstructor
@Service
public class ApplePolicyApplicationServiceImpl implements ApplePolicyApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ApplePolicyApplicationServiceImpl.class);

    private final AppleCommandSenderService commandSenderService;
    private final AppleCommandBuilderService appleCommandBuilderService;
    private final AppleCommandQueueService appleCommandQueueService;
    private final AppleDeviceRepository appleDeviceRepository;
    private final PolicyComplianceTracker policyComplianceTracker;
    private final List<PlatformPayloadStrategy> platformStrategies;
    private final AgentCommandService agentCommandService;
    private final AppGroupRepository appGroupRepository;
    private final ItunesAppMetaRepository itunesAppMetaRepository;
    private final EnterpriseAppRepository enterpriseAppRepository;
    private final PolicyEventPublisher policyEventPublisher;

    @Async
    @Transactional
    @Override
    public void applyPolicy(AppleDevice persistedDevice, Map<String, Object> policyToApply) throws Exception {

        if (policyToApply == null || policyToApply.isEmpty()) {
            handleEmptyPolicy(persistedDevice);
            return;
        }

        policyComplianceTracker.startTracking(persistedDevice.getUdid());
        logger.info("Started policy compliance tracking for device UDID: {}", persistedDevice.getUdid());

        Map<String, Object> policy = deepCopyMap(policyToApply);

        String policyPlatform = (String) policy.get(PLATFORM);
        Map<String, Object> payload = (Map<String, Object>) policy.get(PAYLOAD);
        Map<String, Object> kioskLockdown = (Map<String, Object>) policy.get(KIOSK_LOCKDOWN);

        // If policy has no actual content (only platform metadata), treat as empty.
        // This handles cases where all policy sections were removed or cleared.
        if ((payload == null || payload.isEmpty()) && (kioskLockdown == null || kioskLockdown.isEmpty())) {
            logger.info("Policy has no payload or kioskLockdown content. Treating as empty for device: {}", persistedDevice.getUdid());
            handleEmptyPolicy(persistedDevice);
            return;
        }

        PolicyContext context = createPolicyContext(persistedDevice, policyPlatform);
        PlatformPayloadStrategy strategy = resolveStrategy(context.platform());

        if (!isPlatformCompatible(persistedDevice, policyPlatform)) {
            return;
        }

        logger.info("Processing {} policy for device with ID: {}", context.platform(), persistedDevice.getId());
        List<NSDictionary> payloadContent = new ArrayList<>();
        strategy.buildPayloads(persistedDevice, payload, kioskLockdown, payloadContent, context);

        if (payload != null && !payload.isEmpty()) {
            executeRequiredAppsInstall(persistedDevice, payload);
            if (strategy.supportsDeclarativeManagement()) {
                executeDeclarativeManagement(persistedDevice, payload);
            }
            executeSettingsCommand(persistedDevice, payload);
        }

        // Resolve blocked apps for macOS before saving policy (so _resolvedBlockedApps is persisted)
        if (context.platform() == ApplePlatform.MACOS && payload != null) {
            List<String> resolvedBlockedApps = resolveBlockedApps(persistedDevice, payload);
            if (payload.containsKey(APPLICATIONS_MANAGEMENT)) {
                ((Map<String, Object>) payload.get(APPLICATIONS_MANAGEMENT))
                        .put("_resolvedBlockedApps", resolvedBlockedApps);
            }
        }

        sendProfile(persistedDevice, payloadContent, policy);

        Map<String, Object> agentPayload = strategy.extractAgentCapabilities(payload);
        if (!agentPayload.isEmpty()) {
            logger.info("Agent capabilities extracted for {}: {}", context.platform(), agentPayload.keySet());
            try {
                agentCommandService.sendCommand(persistedDevice.getUdid(), CMD_UPDATE_CONFIG, agentPayload);
                // Update dataServices status to "sent" after successful MQTT publish
                if (payload != null && payload.get(DATA_SERVICES) instanceof Map<?, ?> dsMap) {
                    ((Map<String, Object>) dsMap).put(DATA_SERVICES_STATUS, "sent");
                    persistedDevice.setAppliedPolicy(policy);
                    appleDeviceRepository.save(persistedDevice);
                    logger.info("Agent config sent and dataServices status updated to 'sent' for device {}", persistedDevice.getUdid());
                }
            } catch (Exception e) {
                logger.error("Failed to send agent config to device {}: {}", persistedDevice.getUdid(), e.getMessage());
            }
        } else {
            logger.info("No agent capabilities in new policy for device {}. Sending disable config.", persistedDevice.getUdid());
            sendAgentDisableConfig(persistedDevice.getUdid());
        }

        // macOS: Always send app blocking policy via dedicated command (empty list = clear blocklist)
        if (context.platform() == ApplePlatform.MACOS) {
            sendAppBlockingPolicy(persistedDevice, payload);
            handleRemoteDesktopCommand(persistedDevice, payload);
        }
    }

    // ********* Policy Lifecycle ********

    private void handleEmptyPolicy(AppleDevice device) {
        logger.info("Policy is null/empty. Clearing applied policy for device with UDID: {}", device.getUdid());

        // Only send RemoveProfile if device previously had an MDM profile installed
        if (hasPreviousMdmProfile(device)) {
            try {
                commandSenderService.removeProfile(device.getUdid(), PROFILE_IDENTIFIER);
            } catch (Exception e) {
                logger.error("Failed to remove profile for device with UDID: {}", device.getUdid(), e);
            }
        } else {
            logger.info("No previous MDM profile on device {}. Skipping RemoveProfile.", device.getUdid());
        }

        device.setAppliedPolicy(null);
        appleDeviceRepository.save(device);

        try {
            handleDeclarativeManagement(device, Map.of());
        } catch (Exception ex) {
            logger.error("Failed to clear declarative management for device with UDID: {}", device.getUdid(), ex);
        }

        sendAgentDisableConfig(device.getUdid());

        // Clear app blocking on the macOS agent
        sendAppBlockingPolicyClear(device.getUdid());

        // Disable Remote Desktop on macOS (default disabled when policy is empty)
        if (detectApplePlatform(device) == ApplePlatform.MACOS) {
            handleRemoteDesktopCommand(device, null);
        }
    }

    // ********* Platform Resolution ********

    private boolean isPlatformCompatible(AppleDevice device, String policyPlatform) {
        ApplePlatform detectedPlatform = detectApplePlatform(device);
        ApplePlatform policyApplePlatform = policyPlatform != null ? ApplePlatform.fromString(policyPlatform) : null;
        if (policyApplePlatform != null && detectedPlatform != null && policyApplePlatform != detectedPlatform
                && !(policyApplePlatform.isIosFamily() && detectedPlatform.isIosFamily())) {
            logger.warn("Policy platform {} does not match device platform {}. Skipping.",
                    policyApplePlatform, detectedPlatform);
            return false;
        }
        return true;
    }

    private ApplePlatform detectApplePlatform(AppleDevice device) {
        String pn = Optional.ofNullable(device.getProductName()).orElse("").toLowerCase(Locale.ROOT);
        if (pn.contains("iphone") || pn.contains("ipod")) return ApplePlatform.IOS;
        if (pn.contains("ipad")) return ApplePlatform.IPADOS;
        if (pn.contains("mac")) return ApplePlatform.MACOS;
        if (pn.contains("apple tv") || pn.contains("appletv")) return ApplePlatform.TVOS;
        if (pn.contains("vision") || pn.contains("reality")) return ApplePlatform.VISIONOS;
        if (pn.contains("watch")) return ApplePlatform.WATCHOS;
        return ApplePlatform.IOS;
    }

    private PolicyContext createPolicyContext(AppleDevice device, String policyPlatform) {
        ApplePlatform platform = policyPlatform != null
                ? ApplePlatform.fromString(policyPlatform)
                : detectApplePlatform(device);
        if (platform == null) {
            platform = detectApplePlatform(device);
        }

        ManagementChannel channel;
        if (Boolean.TRUE.equals(device.getIsUserEnrollment())
                || device.getEnrollmentType() == EnrollmentType.USER_ENROLLMENT) {
            channel = ManagementChannel.USER_CHANNEL;
        } else if (device.getEnrollmentType() == EnrollmentType.DEP) {
            channel = ManagementChannel.SUPERVISED;
        } else {
            channel = ManagementChannel.UNSUPERVISED;
        }

        return new PolicyContext(platform, channel);
    }

    private PlatformPayloadStrategy resolveStrategy(ApplePlatform platform) {
        return platformStrategies.stream()
                .filter(s -> s.getPlatform() == platform
                        || (platform == ApplePlatform.IPADOS && s.getPlatform() == ApplePlatform.IOS))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No strategy for platform: " + platform));
    }

    // ********* Agent Config ********

    private void sendAgentDisableConfig(String udid) {
        try {
            Map<String, Object> disablePayload = Map.of(
                    AGENT_TELEMETRY, false,
                    AGENT_LOCATION, false
            );
            agentCommandService.sendCommand(udid, CMD_UPDATE_CONFIG, disablePayload);
            logger.info("Sent agent disable config (telemetry=false, location=false) to device {}", udid);
        } catch (Exception e) {
            logger.error("Failed to send agent disable config to device {}: {}", udid, e.getMessage());
        }
    }

    // ********* macOS App Blocking ********

    /**
     * Sends the app blocking policy to the macOS agent via update_app_policy command.
     * Empty list clears the blocklist on the agent.
     */
    private void sendAppBlockingPolicy(AppleDevice device, Map<String, Object> payload) {
        List<String> blockedApps = resolveBlockedApps(device, payload);
        try {
            agentCommandService.sendCommand(device.getUdid(), CMD_UPDATE_APP_POLICY,
                    Map.of("blockedBundleIds", blockedApps));
            logger.info("Sent app blocking policy ({} apps) to macOS agent {}", blockedApps.size(), device.getUdid());
        } catch (Exception e) {
            logger.error("Failed to send app blocking policy to macOS agent {}: {}", device.getUdid(), e.getMessage());
        }
    }

    /**
     * Clears the app blocking policy on the macOS agent.
     */
    private void sendAppBlockingPolicyClear(String udid) {
        try {
            agentCommandService.sendCommand(udid, CMD_UPDATE_APP_POLICY,
                    Map.of("blockedBundleIds", List.of()));
            logger.info("Sent app blocking policy clear to macOS agent {}", udid);
        } catch (Exception e) {
            logger.error("Failed to clear app blocking policy for macOS agent {}: {}", udid, e.getMessage());
        }
    }

    /**
     * Sends EnableRemoteDesktop or DisableRemoteDesktop MDM command based on the policy's
     * macosConfiguration.remoteDesktop section. Defaults to disabled when the section is
     * missing or the payload is empty, ensuring Screen Sharing is always turned off
     * unless explicitly enabled by policy.
     */
    @SuppressWarnings("unchecked")
    private void handleRemoteDesktopCommand(AppleDevice device, Map<String, Object> payload) {
        boolean enabled = false;

        if (payload != null && payload.containsKey(MACOS_CONFIGURATION)) {
            Map<String, Object> macosConfig = (Map<String, Object>) payload.get(MACOS_CONFIGURATION);
            if (macosConfig != null && macosConfig.containsKey(REMOTE_DESKTOP)) {
                Object rdRaw = macosConfig.get(REMOTE_DESKTOP);
                if (rdRaw instanceof Map<?, ?> rdConfig) {
                    enabled = Boolean.TRUE.equals(rdConfig.get("enabled"));
                }
            }
        }

        try {
            String commandUUID = UUID.randomUUID().toString();
            NSDictionary commandTemplate;

            if (enabled) {
                commandTemplate = appleCommandBuilderService.enableRemoteDesktop(commandUUID);
                logger.info("Queuing EnableRemoteDesktop for device {}", device.getUdid());
            } else {
                commandTemplate = appleCommandBuilderService.disableRemoteDesktop(commandUUID);
                logger.info("Queuing DisableRemoteDesktop for device {}", device.getUdid());
            }

            appleCommandQueueService.pushCommand(
                    device.getUdid(),
                    commandUUID,
                    commandTemplate,
                    enabled ? "EnableRemoteDesktop" : "DisableRemoteDesktop",
                    false,
                    false,
                    null
            );
        } catch (Exception e) {
            logger.error("Failed to queue Remote Desktop command for device {}: {}", device.getUdid(), e.getMessage());
        }
    }

    /**
     * Resolves the complete list of blocked app bundle IDs from the policy payload,
     * including apps from app groups.
     */
    private List<String> resolveBlockedApps(AppleDevice device, Map<String, Object> payload) {
        if (payload == null || !payload.containsKey(APPLICATIONS_MANAGEMENT)) return List.of();

        Map<String, Object> appMgmt = (Map<String, Object>) payload.get(APPLICATIONS_MANAGEMENT);
        List<String> blockedApps = new ArrayList<>();

        // Direct bundle IDs
        if (appMgmt.containsKey(APPLICATIONS_MANAGEMENT_BLOCK_LIST)) {
            Object directList = appMgmt.get(APPLICATIONS_MANAGEMENT_BLOCK_LIST);
            if (directList instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s && !s.isBlank()) {
                        blockedApps.add(s);
                    } else if (item instanceof Map<?, ?> appMap) {
                        // Frontend may send [{bundleId: "..."}, ...] format
                        Object bundleId = ((Map<String, Object>) appMap).get("bundleId");
                        if (bundleId instanceof String s && !s.isBlank()) {
                            blockedApps.add(s);
                        }
                    }
                }
            }
        }

        // Resolve from app groups
        if (appMgmt.containsKey(APPLICATIONS_MANAGEMENT_BLOCK_LIST_APP_GROUPS)) {
            List<String> groupIds = (List<String>) appMgmt.get(APPLICATIONS_MANAGEMENT_BLOCK_LIST_APP_GROUPS);
            blockedApps = addAppsFromGroups(device, groupIds, blockedApps, true);
        }

        return blockedApps.stream().distinct().toList();
    }

    // ********* Command Execution ********

    private void executeRequiredAppsInstall(AppleDevice device, Map<String, Object> platformPolicy) {
        if (!platformPolicy.containsKey(APPLICATIONS_MANAGEMENT)) return;

        Map<String, Object> appMgmtMap = (Map<String, Object>) platformPolicy.get(APPLICATIONS_MANAGEMENT);
        if (!appMgmtMap.containsKey(APPLICATIONS_MANAGEMENT_REQUIRE_APPS)) return;

        UUID policyId = UUID.fromString(appMgmtMap.get(POLICY_ID).toString());
        Map<String, Object> requiredAppMap = (Map<String, Object>) appMgmtMap.get(APPLICATIONS_MANAGEMENT_REQUIRE_APPS);

        Boolean removable = (Boolean) requiredAppMap.get(APPLICATIONS_MANAGEMENT_REQUIRE_APPS_REMOVABLE);
        List<Map<String, Object>> appMaps = (List<Map<String, Object>>) requiredAppMap.get(APPLICATIONS_MANAGEMENT_REQUIRE_APPS_APPS);
        List<String> requiredApps = new ArrayList<>();
        if (appMaps != null) {
            for (Map<String, Object> appMap : appMaps) {
                String bundleId = (String) appMap.get("bundleId");
                if (bundleId != null && !bundleId.isBlank()) {
                    requiredApps.add(bundleId);
                }
            }
        }
        List<String> requiredAppGroups = (List<String>) requiredAppMap.get(APPLICATIONS_MANAGEMENT_REQUIRE_APPS_APP_GROUPS);

        requiredApps = addAppsFromGroups(device, requiredAppGroups, requiredApps, false);

        if (requiredApps.isEmpty()) return;

        List<String> distinctApps = requiredApps.stream().distinct().toList();
        for (String identifier : distinctApps) {
            try {
                logger.info("Initiating installation of app: {} for device UDID: {}", identifier, device.getUdid());
                commandSenderService.installApp(device.getUdid(), identifier, removable, true, policyId);
            } catch (Exception e) {
                logger.error("Error initiating app install: {} for device UDID: {}", identifier, device.getUdid(), e);
            }
        }
    }

    private void executeDeclarativeManagement(AppleDevice device, Map<String, Object> platformPolicy) {
        handleDeclarativeManagement(device, platformPolicy);
    }

    private void executeSettingsCommand(AppleDevice device, Map<String, Object> platformPolicy) {
        if (!platformPolicy.containsKey(CONFIGURATIONS)) return;

        Map<String, Object> configurationsMap = (Map<String, Object>) platformPolicy.get(CONFIGURATIONS);
        if (!configurationsMap.containsKey(SETTINGS)) return;

        logger.info("Settings policy found for device with ID: {}", device.getId());

        resolveDeviceNameTemplateInSettings(device, configurationsMap);

        List<NSDictionary> settingsPayloads = Settings.buildSettingsPayloads(configurationsMap);
        if (settingsPayloads.isEmpty()) return;

        String policyID = (String) configurationsMap.get("policyId");
        UUID policyUuid = UUID.fromString(policyID);

        try {
            commandSenderService.sendSettings(device.getUdid(), settingsPayloads, true, policyUuid);
        } catch (Exception e) {
            logger.error("Failed to send settings for device with ID: {}", device.getId(), e);
        }
    }

    private void sendProfile(AppleDevice device, List<NSDictionary> payloadContent, Map<String, Object> policy) throws Exception {
        if (payloadContent.isEmpty()) {
            logger.info("No MDM profile payload for device {}. Policy contains agent-only config.", device.getId());

            // Only send RemoveProfile if device previously had an MDM profile installed
            if (hasPreviousMdmProfile(device)) {
                try {
                    commandSenderService.removeProfile(device.getUdid(), PROFILE_IDENTIFIER);
                } catch (Exception e) {
                    logger.error("Failed to remove profile for device {}: {}", device.getUdid(), e.getMessage());
                }
            }

            device.setAppliedPolicy(policy);
            appleDeviceRepository.save(device);

            // Publish PolicyAppliedEvent so back_core and UI know the policy was applied
            policyEventPublisher.publishPolicyAppliedEvent(
                    new PolicyAppliedEvent(device.getId(), "Apple", policy));
            logger.info("Policy applied (agent-only) for device {}. PolicyAppliedEvent published.", device.getId());
            return;
        }

        NSDictionary profile = Profile.createProfile(payloadContent.toArray(new NSDictionary[0])).createPayload();
        String formattedProfile = convertNSDictionaryToString(profile);
        String payload = Base64.getEncoder().encodeToString(formattedProfile.getBytes());

        String commandUUID = createCommandUUID(device.getUdid(), DEVICE_INSTALL_PROFILE_COMMAND.getRequestType());
        NSDictionary commandTemplate = appleCommandBuilderService.installProfile(payload, commandUUID);

        logger.info("Adding InstallProfile command to queue for device UDID: {}", device.getUdid());

        policyComplianceTracker.registerCommand(device.getUdid(), commandUUID, DEVICE_INSTALL_PROFILE_COMMAND.getRequestType(), "MDM Configuration Profile");

        appleCommandQueueService.pushCommand(device.getUdid(), commandUUID, commandTemplate, DEVICE_INSTALL_PROFILE_COMMAND.getRequestType(), false, false, null);

        device.setAppliedPolicy(policy);
        appleDeviceRepository.save(device);
    }

    /**
     * Checks if the device previously had an MDM configuration profile installed.
     * A profile was installed if the device has an appliedPolicy with non-empty payload
     * that contained APNs-deliverable content (not just agent/data-services config).
     */
    private boolean hasPreviousMdmProfile(AppleDevice device) {
        Map<String, Object> prev = device.getAppliedPolicy();
        if (prev == null || prev.isEmpty()) return false;

        Map<String, Object> prevPayload = (Map<String, Object>) prev.get(PAYLOAD);
        if (prevPayload == null || prevPayload.isEmpty()) return false;

        // If the only key in payload is dataServices, no MDM profile was installed
        if (prevPayload.size() == 1 && prevPayload.containsKey(DATA_SERVICES)) return false;

        return true;
    }

    // ********* Declarative Management ********

    private void handleDeclarativeManagement(AppleDevice device, Map<String, Object> platformPolicy) {
        if (device == null) return;

        String deviceToken = device.getDeclarationToken();
        String desiredToken = resolveDesiredDeclarativeToken(platformPolicy);

        if ((deviceToken == null || deviceToken.isBlank()) && isEmptyDmToken(desiredToken)) {
            return;
        }

        if (desiredToken.equals(deviceToken)) {
            return;
        }

        logger.info("Declarative token change for device {}. Old: {}, New: {}", device.getId(), deviceToken, desiredToken);

        UUID policyId = null;
        try {
            if (platformPolicy != null) {
                Object dmObj = platformPolicy.get(DECLARATIVE_MANAGEMENT);
                if (dmObj instanceof Map<?, ?> dmRaw) {
                    Map<String, Object> dmMap = (Map<String, Object>) dmRaw;
                    if (dmMap.get(POLICY_ID) != null) {
                        policyId = UUID.fromString(dmMap.get(POLICY_ID).toString());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            commandSenderService.sendDeclarativeManagementCommand(device.getUdid(), desiredToken, policyId);
        } catch (Exception e) {
            logger.error("Error sending declarative management command for device {}", device.getId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String resolveDesiredDeclarativeToken(Map<String, Object> platformPolicy) {
        final String emptyToken = emptyDmToken();

        if (platformPolicy == null || platformPolicy.isEmpty()) {
            return emptyToken;
        }

        Object dmObj = platformPolicy.get(DECLARATIVE_MANAGEMENT);
        if (!(dmObj instanceof Map<?, ?> dmRaw)) {
            return emptyToken;
        }

        Map<String, Object> dmMap = (Map<String, Object>) dmRaw;

        Object configsObj = dmMap.get("configurations");
        if (!(configsObj instanceof List<?> configs) || configs.isEmpty()) {
            return emptyToken;
        }

        Object tokenObj = dmMap.get("server_token");
        if (tokenObj == null) {
            return emptyToken;
        }

        String token = tokenObj.toString();
        return token.isBlank() ? emptyToken : token;
    }

    private boolean isEmptyDmToken(String token) {
        return token == null || token.isBlank() || emptyDmToken().equals(token);
    }

    private String emptyDmToken() {
        return "0";
    }

    // ********* Device Name Template ********

    private void resolveDeviceNameTemplateInSettings(AppleDevice device, Map<String, Object> configurationsMap) {
        Object settingsRaw = configurationsMap.get(SETTINGS);
        if (!(settingsRaw instanceof Map<?, ?> settingsMap)) return;

        Map<String, Object> settings = (Map<String, Object>) settingsMap;
        Object deviceNameRaw = settings.get(SETTINGS_DEVICE_NAME);
        if (!(deviceNameRaw instanceof Map<?, ?> deviceNameMap)) return;

        Map<String, Object> deviceNameSettings = (Map<String, Object>) deviceNameMap;
        Object nameValue = deviceNameSettings.get("DeviceName");
        if (!(nameValue instanceof String nameStr)) return;

        if (nameStr.contains("{")) {
            AppleDevice freshDevice = appleDeviceRepository.findById(device.getId()).orElse(device);
            String resolved = resolveDeviceNameTemplate(nameStr, freshDevice);
            deviceNameSettings.put("DeviceName", resolved);
            logger.info("Resolved device name template '{}' -> '{}' for device {}", nameStr, resolved, device.getUdid());
        }
    }

    private String resolveDeviceNameTemplate(String template, AppleDevice device) {
        String result = template;

        result = result.replace("{serial_number}", defaultStr(device.getSerialNumber()));
        result = result.replace("{udid}", defaultStr(device.getUdid()));
        result = result.replace("{product_name}", defaultStr(device.getProductName()));

        AppleDeviceInformation info = device.getDeviceProperties();
        if (info != null) {
            result = result.replace("{device_name}", defaultStr(info.getDeviceName()));
            result = result.replace("{model_name}", defaultStr(info.getModelName()));
        } else {
            result = result.replace("{device_name}", "");
            result = result.replace("{model_name}", "");
        }

        Set<AppleAccount> accounts = device.getAccounts();
        String accountName = "";
        if (accounts != null && !accounts.isEmpty()) {
            AppleAccount account = accounts.iterator().next();
            accountName = account.getFullName() != null && !account.getFullName().isBlank()
                    ? account.getFullName()
                    : defaultStr(account.getUsername());
        }
        result = result.replace("{account_name}", accountName);

        return result;
    }

    private static String defaultStr(String value) {
        return value != null ? value : "";
    }

    // ********* App Group Resolution ********

    private List<String> addAppsFromGroups(AppleDevice device, List<String> resultAppList, List<String> requiredApps, boolean addBundleId) {
        if (requiredApps == null) {
            requiredApps = new ArrayList<>();
        }
        if (resultAppList == null || resultAppList.isEmpty()) {
            return requiredApps;
        }

        List<UUID> groupIds = resultAppList.stream().map(UUID::fromString).toList();
        List<AppGroup> appGroups = appGroupRepository.findByIdIn(groupIds);

        String product = Optional.ofNullable(device.getProductName()).orElse("").toLowerCase(Locale.ROOT);
        boolean isIphone = product.contains("iphone");
        boolean isIpad = product.contains("ipad");
        boolean isMac = product.contains("mac");

        List<Long> trackIdNums = new ArrayList<>();
        List<String> enterpriseBundleIds = new ArrayList<>();

        for (AppGroup appGroup : appGroups) {
            for (var item : appGroup.getItems()) {
                if (AppGroup.AppType.VPP.equals(item.getAppType())) {
                    if (item.getTrackId() != null && !item.getTrackId().isBlank()) {
                        try {
                            trackIdNums.add(Long.parseLong(item.getTrackId().trim()));
                        } catch (NumberFormatException ignore) {
                        }
                    }
                } else if (AppGroup.AppType.ENTERPRISE.equals(item.getAppType())) {
                    if (item.getBundleId() != null && !item.getBundleId().isBlank()) {
                        enterpriseBundleIds.add(item.getBundleId().trim());
                    }
                }
            }
        }

        Map<Long, List<String>> vppPlatformLookup = new HashMap<>();
        if (!trackIdNums.isEmpty()) {
            for (ItunesAppMeta meta : itunesAppMetaRepository.findAllByTrackIdIn(trackIdNums)) {
                vppPlatformLookup.put(meta.getTrackId(),
                        meta.getSupportedPlatforms() != null
                                ? meta.getSupportedPlatforms().stream().filter(Objects::nonNull).map(s -> s.toLowerCase(Locale.ROOT)).toList()
                                : List.of());
            }
        }

        Map<String, List<String>> enterprisePlatformLookup = new HashMap<>();
        if (!enterpriseBundleIds.isEmpty()) {
            for (EnterpriseApp app : enterpriseAppRepository.findAllByBundleIdIn(enterpriseBundleIds)) {
                enterprisePlatformLookup.put(app.getBundleId(),
                        app.getSupportedPlatforms() != null
                                ? app.getSupportedPlatforms().stream().filter(Objects::nonNull).map(s -> s.toLowerCase(Locale.ROOT)).toList()
                                : List.of());
            }
        }

        for (AppGroup appGroup : appGroups) {
            for (var item : appGroup.getItems()) {
                List<String> platforms = List.of();
                boolean isVpp = AppGroup.AppType.VPP.equals(item.getAppType());
                boolean isEnterprise = AppGroup.AppType.ENTERPRISE.equals(item.getAppType());

                if (!isVpp && !isEnterprise) {
                    continue;
                }

                if (isVpp && item.getTrackId() != null && !item.getTrackId().isBlank()) {
                    try {
                        platforms = vppPlatformLookup.getOrDefault(Long.parseLong(item.getTrackId().trim()), List.of());
                    } catch (NumberFormatException ignore) {
                    }
                } else if (isEnterprise && item.getBundleId() != null && !item.getBundleId().isBlank()) {
                    platforms = enterprisePlatformLookup.getOrDefault(item.getBundleId().trim(), List.of());
                }

                boolean iosSupported = platforms.isEmpty() || platforms.stream().anyMatch(p ->
                        p.contains("ios") || p.contains("iphone") || p.contains("ipad"));
                boolean macSupported = platforms.isEmpty() || platforms.stream().anyMatch(p ->
                        p.contains("macos") || p.contains("mac"));

                boolean platformMatch =
                        ((isIphone || isIpad) && iosSupported)
                                || (isMac && macSupported);

                if (!platformMatch) {
                    logger.debug("Skipping app {} due to platform mismatch. device={}, platforms={}",
                            isVpp ? item.getTrackId() : item.getBundleId(), product, platforms);
                    continue;
                }

                if (isVpp) {
                    if (addBundleId) {
                        String bundleId = item.getBundleId();
                        if (bundleId != null && !bundleId.isBlank()) {
                            requiredApps.add(bundleId);
                        }
                    } else {
                        String trackId = item.getTrackId();
                        if (trackId != null && !trackId.isBlank()) {
                            requiredApps.add(trackId);
                        }
                    }
                } else {
                    String bundleId = item.getBundleId();
                    if (bundleId != null && !bundleId.isBlank()) {
                        requiredApps.add(bundleId);
                        logger.debug("Added Enterprise app to install list: bundleId={}", bundleId);
                    }
                }
            }
        }

        return requiredApps;
    }

    // ********* Utility ********

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> original) {
        if (original == null) return null;
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), deepCopyMap((Map<String, Object>) value));
            } else if (value instanceof List<?> list) {
                List<Object> listCopy = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof Map) {
                        listCopy.add(deepCopyMap((Map<String, Object>) item));
                    } else {
                        listCopy.add(item);
                    }
                }
                copy.put(entry.getKey(), listCopy);
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    private String convertNSDictionaryToString(NSDictionary nsDictionary) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PropertyListParser.saveAsXML(nsDictionary, outputStream);
        return outputStream.toString();
    }

    private String createCommandUUID(String deviceUdid, String commandType) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return deviceUdid + "_" + commandType + "_" + suffix;
    }
}

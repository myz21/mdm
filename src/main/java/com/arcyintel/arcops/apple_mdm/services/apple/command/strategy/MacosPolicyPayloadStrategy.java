package com.arcyintel.arcops.apple_mdm.services.apple.command.strategy;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.models.profile.*;
import com.arcyintel.arcops.apple_mdm.models.profile.restrictions.MacosRestrictions;
import com.arcyintel.arcops.apple_mdm.repositories.AppGroupRepository;
import com.arcyintel.arcops.apple_mdm.repositories.EnterpriseAppRepository;
import com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository;
import com.arcyintel.arcops.commons.constants.apple.ApplePlatform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.arcyintel.arcops.commons.constants.apple.CommandSpecificConfigurations.*;
import static com.arcyintel.arcops.commons.constants.policy.PolicyStatus.*;

/**
 * macOS platform strategy.
 *
 * Differences from iOS:
 * - Cellular not supported
 * - Kiosk lockdown (AppLock) supported (macOS 10.14+, com.apple.app.lock)
 * - blockedAppBundleIDs / allowListedAppBundleIDs not supported via MDM profile (will be handled by agent)
 * - macOS-specific restriction keys filtered via MacosRestrictions
 */
@SuppressWarnings("unchecked")
@Component
public class MacosPolicyPayloadStrategy extends AbstractPlatformPayloadStrategy {

    private static final Logger logger = LogManager.getLogger(MacosPolicyPayloadStrategy.class);

    public MacosPolicyPayloadStrategy(ItunesAppMetaRepository itunesAppMetaRepository,
                                       EnterpriseAppRepository enterpriseAppRepository,
                                       AppGroupRepository appGroupRepository) {
        super(itunesAppMetaRepository, enterpriseAppRepository, appGroupRepository);
    }

    @Override
    public ApplePlatform getPlatform() {
        return ApplePlatform.MACOS;
    }

    @Override
    public boolean supportsKioskLockdown() {
        return true;  // macOS 10.14+ supports com.apple.app.lock
    }

    /**
     * macOS: extract dataServices (telemetry, location) for delivery via agent.
     * App blocking (blockedAppBundleIDs) is handled separately via update_app_policy command.
     */
    @Override
    public Map<String, Object> extractAgentCapabilities(Map<String, Object> payload) {
        Map<String, Object> agentPayload = new LinkedHashMap<>();
        if (payload == null) return agentPayload;

        // dataServices (telemetry, location)
        agentPayload.putAll(extractDataServicesForAgent(payload));

        return agentPayload;
    }

    @Override
    protected void buildPlatformPayloads(AppleDevice device,
                                          Map<String, Object> payload,
                                          List<NSDictionary> payloadContent,
                                          PolicyContext context) {
        handlePasscode(payload, payloadContent);
        handleProfileRemovalPassword(payload, payloadContent);
        handleRestrictions(payload, payloadContent);
        handleApplicationPayloads(device, payload, payloadContent, context);
        handleNetworkConfiguration(device, payload, payloadContent);
        handleSecurityConfiguration(device, payload, payloadContent);
        handleConfigurations(device, payload, payloadContent);
        handleMacosConfiguration(device, payload, payloadContent);
    }

    // ==================== macOS-Specific Handlers ====================

    private void handleRestrictions(Map<String, Object> platformPolicy, List<NSDictionary> payloadContent) {
        if (!platformPolicy.containsKey(RESTRICTIONS)) return;

        logger.info("macOS Restrictions policy found. Creating restrictions payload.");
        Map<String, Object> restrictionsMap = (Map<String, Object>) platformPolicy.get(RESTRICTIONS);
        MacosRestrictions restrictions = MacosRestrictions.createFromMap(restrictionsMap);
        payloadContent.add(restrictions.createPayload());
        restrictionsMap.replace(STATUS, STATUS_SENT);
    }

    /**
     * macOS: blockedAppBundleIDs and allowListedAppBundleIDs are not supported via MDM profile.
     * These capabilities will be handled via agent in the future.
     * Web clips and notifications are supported.
     */
    private void handleApplicationPayloads(AppleDevice device, Map<String, Object> platformPolicy,
                                            List<NSDictionary> payloadContent, PolicyContext context) {
        if (!platformPolicy.containsKey(APPLICATIONS_MANAGEMENT)) return;

        logger.info("macOS Application management policy found for device with ID: {}", device.getId());
        Map<String, Object> appMgmtMap = (Map<String, Object>) platformPolicy.get(APPLICATIONS_MANAGEMENT);
        UUID policyId = UUID.fromString(appMgmtMap.get(POLICY_ID).toString());

        // macOS: block list and allow list not supported via MDM - SKIP
        // handleAppBlockList and handleAppAllowList intentionally NOT called
        handleWebClips(device, appMgmtMap, payloadContent, policyId);
        handleNotifications(device, appMgmtMap, payloadContent, policyId, context);

        appMgmtMap.replace(STATUS, STATUS_SENT);
    }

    /**
     * macOS: WiFi + VPN supported. Cellular not supported.
     */
    private void handleNetworkConfiguration(AppleDevice device, Map<String, Object> platformPolicy,
                                             List<NSDictionary> payloadContent) {
        if (!platformPolicy.containsKey(NETWORK_CONFIGURATION)) return;

        logger.info("macOS Network configuration policy found for device with ID: {}", device.getId());
        Map<String, Object> networkConfigurationMap = (Map<String, Object>) platformPolicy.get(NETWORK_CONFIGURATION);
        UUID policyId = UUID.fromString(networkConfigurationMap.get(POLICY_ID).toString());

        // macOS: WiFi + VPN (Cellular not supported)
        handleWifi(networkConfigurationMap, payloadContent, platformPolicy, policyId);
        // handleCellular intentionally NOT called
        handleVpn(networkConfigurationMap, payloadContent);

        networkConfigurationMap.replace(STATUS, STATUS_SENT);
    }

    /**
     * macOS: extends base security configuration with FileVault and Firewall payloads.
     */
    @Override
    protected void handleSecurityConfiguration(AppleDevice device, Map<String, Object> platformPolicy,
                                                List<NSDictionary> payloadContent) {
        super.handleSecurityConfiguration(device, platformPolicy, payloadContent);

        if (!platformPolicy.containsKey(SECURITY_CONFIGURATION)) return;

        Map<String, Object> securityConfigurationMap = (Map<String, Object>) platformPolicy.get(SECURITY_CONFIGURATION);
        UUID policyId = UUID.fromString(securityConfigurationMap.get(POLICY_ID).toString());

        if (securityConfigurationMap.containsKey(FILEVAULT)) {
            logger.info("FileVault configuration found for device with ID: {}", device.getId());
            Map<String, Object> filevaultMap = (Map<String, Object>) securityConfigurationMap.get(FILEVAULT);
            payloadContent.add(FileVault.createFromMap(filevaultMap, policyId).createPayload());
        }

        if (securityConfigurationMap.containsKey(FIREWALL)) {
            logger.info("Firewall configuration found for device with ID: {}", device.getId());
            Map<String, Object> firewallMap = (Map<String, Object>) securityConfigurationMap.get(FIREWALL);
            payloadContent.add(Firewall.createFromMap(firewallMap, policyId).createPayload());
        }
    }

    /**
     * macOS-specific configuration payloads: System Extensions, Kernel Extensions,
     * TCC/PPPC, Login Window, Screensaver, Energy Saver.
     */
    private void handleMacosConfiguration(AppleDevice device, Map<String, Object> platformPolicy,
                                           List<NSDictionary> payloadContent) {
        if (!platformPolicy.containsKey(MACOS_CONFIGURATION)) return;

        logger.info("macOS-specific configuration found for device with ID: {}", device.getId());
        Map<String, Object> macosConfig = (Map<String, Object>) platformPolicy.get(MACOS_CONFIGURATION);
        UUID policyId = UUID.fromString(macosConfig.get(POLICY_ID).toString());

        if (macosConfig.containsKey(SYSTEM_EXTENSIONS)) {
            logger.info("System Extensions configuration found for device with ID: {}", device.getId());
            Map<String, Object> map = (Map<String, Object>) macosConfig.get(SYSTEM_EXTENSIONS);
            payloadContent.add(SystemExtensionPolicy.createFromMap(map, policyId).createPayload());
        }

        if (macosConfig.containsKey(KERNEL_EXTENSIONS)) {
            logger.info("Kernel Extensions configuration found for device with ID: {}", device.getId());
            Map<String, Object> map = (Map<String, Object>) macosConfig.get(KERNEL_EXTENSIONS);
            payloadContent.add(KernelExtensionPolicy.createFromMap(map, policyId).createPayload());
        }

        if (macosConfig.containsKey(TCC_PPPC)) {
            logger.info("TCC/PPPC configuration found for device with ID: {}", device.getId());
            Map<String, Object> map = (Map<String, Object>) macosConfig.get(TCC_PPPC);
            payloadContent.add(TccPolicy.createFromMap(map, policyId).createPayload());
        }

        if (macosConfig.containsKey(LOGIN_WINDOW)) {
            logger.info("Login Window configuration found for device with ID: {}", device.getId());
            Map<String, Object> map = (Map<String, Object>) macosConfig.get(LOGIN_WINDOW);
            payloadContent.add(LoginWindow.createFromMap(map, policyId).createPayload());
        }

        if (macosConfig.containsKey(SCREENSAVER)) {
            logger.info("Screensaver configuration found for device with ID: {}", device.getId());
            Map<String, Object> map = (Map<String, Object>) macosConfig.get(SCREENSAVER);
            payloadContent.add(ScreenSaver.createFromMap(map, policyId).createPayload());
        }

        if (macosConfig.containsKey(ENERGY_SAVER)) {
            logger.info("Energy Saver configuration found for device with ID: {}", device.getId());
            Map<String, Object> map = (Map<String, Object>) macosConfig.get(ENERGY_SAVER);
            payloadContent.add(EnergySaver.createFromMap(map, policyId).createPayload());
        }

        if (macosConfig.containsKey(SYSTEM_PREFERENCES)) {
            logger.info("System Preferences configuration found for device with ID: {}", device.getId());
            Map<String, Object> map = (Map<String, Object>) macosConfig.get(SYSTEM_PREFERENCES);
            payloadContent.add(SystemPreferencesPolicy.createFromMap(map, policyId).createPayload());
        }

        if (macosConfig.containsKey(DIRECTORY_SERVICE)) {
            logger.info("Directory Service configuration found for device with ID: {}", device.getId());
            Map<String, Object> map = (Map<String, Object>) macosConfig.get(DIRECTORY_SERVICE);
            payloadContent.add(DirectoryService.createFromMap(map, policyId).createPayload());
        }
    }
}

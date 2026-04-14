package com.arcyintel.arcops.apple_mdm.services.apple.command.strategy;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.models.profile.PolicyContext;
import com.arcyintel.arcops.apple_mdm.models.profile.restrictions.VisionosRestrictions;
import com.arcyintel.arcops.apple_mdm.repositories.AppGroupRepository;
import com.arcyintel.arcops.apple_mdm.repositories.EnterpriseAppRepository;
import com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository;
import com.arcyintel.arcops.commons.constants.apple.ApplePlatform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.arcyintel.arcops.commons.constants.apple.CommandSpecificConfigurations.*;
import static com.arcyintel.arcops.commons.constants.policy.PolicyStatus.*;

/**
 * visionOS platform strategy.
 *
 * Differences from iOS:
 * - Relatively new platform with limited MDM support
 * - Cellular not supported
 * - Kiosk lockdown not supported
 * - WiFi + VPN supported
 * - Limited restriction set (VisionosRestrictions)
 */
@SuppressWarnings("unchecked")
@Component
public class VisionosPolicyPayloadStrategy extends AbstractPlatformPayloadStrategy {

    private static final Logger logger = LogManager.getLogger(VisionosPolicyPayloadStrategy.class);

    public VisionosPolicyPayloadStrategy(ItunesAppMetaRepository itunesAppMetaRepository,
                                          EnterpriseAppRepository enterpriseAppRepository,
                                          AppGroupRepository appGroupRepository) {
        super(itunesAppMetaRepository, enterpriseAppRepository, appGroupRepository);
    }

    @Override
    public ApplePlatform getPlatform() {
        return ApplePlatform.VISIONOS;
    }

    @Override
    public boolean supportsKioskLockdown() {
        return false;
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
    }

    // ==================== visionOS-Specific Handlers ====================

    private void handleRestrictions(Map<String, Object> platformPolicy, List<NSDictionary> payloadContent) {
        if (!platformPolicy.containsKey(RESTRICTIONS)) return;

        logger.info("visionOS Restrictions policy found. Creating restrictions payload.");
        Map<String, Object> restrictionsMap = (Map<String, Object>) platformPolicy.get(RESTRICTIONS);
        VisionosRestrictions restrictions = VisionosRestrictions.createFromMap(restrictionsMap);
        payloadContent.add(restrictions.createPayload());
        restrictionsMap.replace(STATUS, STATUS_SENT);
    }

    private void handleApplicationPayloads(AppleDevice device, Map<String, Object> platformPolicy,
                                            List<NSDictionary> payloadContent, PolicyContext context) {
        if (!platformPolicy.containsKey(APPLICATIONS_MANAGEMENT)) return;

        logger.info("visionOS Application management policy found for device with ID: {}", device.getId());
        Map<String, Object> appMgmtMap = (Map<String, Object>) platformPolicy.get(APPLICATIONS_MANAGEMENT);
        UUID policyId = UUID.fromString(appMgmtMap.get(POLICY_ID).toString());

        handleAppBlockList(device, appMgmtMap, payloadContent);
        handleAppAllowList(device, appMgmtMap, payloadContent);
        handleWebClips(device, appMgmtMap, payloadContent, policyId);
        handleNotifications(device, appMgmtMap, payloadContent, policyId, context);

        appMgmtMap.replace(STATUS, STATUS_SENT);
    }

    /**
     * visionOS: WiFi + VPN supported. Cellular not supported.
     */
    private void handleNetworkConfiguration(AppleDevice device, Map<String, Object> platformPolicy,
                                             List<NSDictionary> payloadContent) {
        if (!platformPolicy.containsKey(NETWORK_CONFIGURATION)) return;

        logger.info("visionOS Network configuration policy found for device with ID: {}", device.getId());
        Map<String, Object> networkConfigurationMap = (Map<String, Object>) platformPolicy.get(NETWORK_CONFIGURATION);
        UUID policyId = UUID.fromString(networkConfigurationMap.get(POLICY_ID).toString());

        handleWifi(networkConfigurationMap, payloadContent, platformPolicy, policyId);
        // handleCellular intentionally NOT called - visionOS does not support cellular
        handleVpn(networkConfigurationMap, payloadContent);

        networkConfigurationMap.replace(STATUS, STATUS_SENT);
    }
}

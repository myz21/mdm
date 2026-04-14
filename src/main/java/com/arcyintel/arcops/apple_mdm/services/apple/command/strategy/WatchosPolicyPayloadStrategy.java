package com.arcyintel.arcops.apple_mdm.services.apple.command.strategy;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.models.profile.PolicyContext;
import com.arcyintel.arcops.apple_mdm.models.profile.restrictions.WatchosRestrictions;
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
 * watchOS platform strategy.
 *
 * Differences from iOS:
 * - Very limited MDM support
 * - Cellular not supported (via MDM)
 * - Kiosk lockdown not supported
 * - VPN not supported
 * - Web clips not supported
 * - Only basic restrictions (WatchosRestrictions)
 * - WiFi supported
 */
@SuppressWarnings("unchecked")
@Component
public class WatchosPolicyPayloadStrategy extends AbstractPlatformPayloadStrategy {

    private static final Logger logger = LogManager.getLogger(WatchosPolicyPayloadStrategy.class);

    public WatchosPolicyPayloadStrategy(ItunesAppMetaRepository itunesAppMetaRepository,
                                         EnterpriseAppRepository enterpriseAppRepository,
                                         AppGroupRepository appGroupRepository) {
        super(itunesAppMetaRepository, enterpriseAppRepository, appGroupRepository);
    }

    @Override
    public ApplePlatform getPlatform() {
        return ApplePlatform.WATCHOS;
    }

    @Override
    public boolean supportsKioskLockdown() {
        return false;
    }

    @Override
    public boolean supportsDeclarativeManagement() {
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
        handleNetworkConfiguration(device, payload, payloadContent);
    }

    // ==================== watchOS-Specific Handlers ====================

    private void handleRestrictions(Map<String, Object> platformPolicy, List<NSDictionary> payloadContent) {
        if (!platformPolicy.containsKey(RESTRICTIONS)) return;

        logger.info("watchOS Restrictions policy found. Creating restrictions payload.");
        Map<String, Object> restrictionsMap = (Map<String, Object>) platformPolicy.get(RESTRICTIONS);
        WatchosRestrictions restrictions = WatchosRestrictions.createFromMap(restrictionsMap);
        payloadContent.add(restrictions.createPayload());
        restrictionsMap.replace(STATUS, STATUS_SENT);
    }

    /**
     * watchOS: WiFi only. Cellular and VPN not supported.
     */
    private void handleNetworkConfiguration(AppleDevice device, Map<String, Object> platformPolicy,
                                             List<NSDictionary> payloadContent) {
        if (!platformPolicy.containsKey(NETWORK_CONFIGURATION)) return;

        logger.info("watchOS Network configuration policy found for device with ID: {}", device.getId());
        Map<String, Object> networkConfigurationMap = (Map<String, Object>) platformPolicy.get(NETWORK_CONFIGURATION);
        UUID policyId = UUID.fromString(networkConfigurationMap.get(POLICY_ID).toString());

        handleWifi(networkConfigurationMap, payloadContent, platformPolicy, policyId);
        // handleCellular and handleVpn intentionally NOT called - watchOS does not support them

        networkConfigurationMap.replace(STATUS, STATUS_SENT);
    }
}

package com.arcyintel.arcops.apple_mdm.services.apple.command.strategy;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.domains.AppGroup;
import com.arcyintel.arcops.apple_mdm.domains.EnterpriseApp;
import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import com.arcyintel.arcops.apple_mdm.models.profile.*;
import com.arcyintel.arcops.apple_mdm.repositories.AppGroupRepository;
import com.arcyintel.arcops.apple_mdm.repositories.EnterpriseAppRepository;
import com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static com.arcyintel.arcops.commons.constants.apple.AgentDataServiceKeys.*;
import static com.arcyintel.arcops.commons.constants.apple.CommandSpecificConfigurations.*;
import static com.arcyintel.arcops.commons.constants.policy.PolicyStatus.*;

@SuppressWarnings("unchecked")
public abstract class AbstractPlatformPayloadStrategy implements PlatformPayloadStrategy {

    private static final Logger logger = LogManager.getLogger(AbstractPlatformPayloadStrategy.class);

    private static final Set<String> BYOD_EXCLUDED_PAYLOAD_TYPES = Set.of(
            "com.apple.app.lock",
            "com.apple.proxy.http.global"
    );

    private static final Set<String> BYOD_EXCLUDED_RESTRICTION_KEYS = Set.of(
            "blockedAppBundleIDs",
            "allowListedAppBundleIDs",
            "autonomousSingleAppModePermittedAppIDs",
            "allowHostPairing",
            "allowUSBRestrictedMode"
    );

    protected final ItunesAppMetaRepository itunesAppMetaRepository;
    protected final EnterpriseAppRepository enterpriseAppRepository;
    protected final AppGroupRepository appGroupRepository;

    protected AbstractPlatformPayloadStrategy(ItunesAppMetaRepository itunesAppMetaRepository,
                                               EnterpriseAppRepository enterpriseAppRepository,
                                               AppGroupRepository appGroupRepository) {
        this.itunesAppMetaRepository = itunesAppMetaRepository;
        this.enterpriseAppRepository = enterpriseAppRepository;
        this.appGroupRepository = appGroupRepository;
    }

    // ==================== Template Method ====================

    @Override
    public final void buildPayloads(AppleDevice device,
                                     Map<String, Object> payload,
                                     Map<String, Object> kioskLockdown,
                                     List<NSDictionary> payloadContent,
                                     PolicyContext context) {
        if (supportsKioskLockdown()) {
            handleKioskLockdown(device, kioskLockdown, payloadContent);
        }

        if (payload != null && !payload.isEmpty()) {
            buildPlatformPayloads(device, payload, payloadContent, context);
        }

        if (context.isByod()) {
            filterForByod(payloadContent);
        }
    }

    /**
     * Platform-specific payload building. Subclasses implement this.
     */
    protected abstract void buildPlatformPayloads(AppleDevice device,
                                                   Map<String, Object> payload,
                                                   List<NSDictionary> payloadContent,
                                                   PolicyContext context);

    // ==================== Kiosk Lockdown ====================

    private void handleKioskLockdown(AppleDevice device, Map<String, Object> kioskLockdown,
                                      List<NSDictionary> payloadContent) {
        if (kioskLockdown == null || kioskLockdown.isEmpty()) return;

        logger.info("Kiosk Lockdown (AppLock) detected in policy for device: {}", device.getUdid());

        UUID policyId = null;
        if (kioskLockdown.get(POLICY_ID) != null) {
            policyId = UUID.fromString(kioskLockdown.get(POLICY_ID).toString());
        }

        if (kioskLockdown.containsKey(SINGLE_APP)) {
            logger.info("Single App Kiosk mode detected for device: {}", device.getUdid());
            Map<String, Object> singleAppMap = (Map<String, Object>) kioskLockdown.get(SINGLE_APP);
            NSDictionary kioskPayload = AppLock.createFromMap(singleAppMap, policyId).createPayload();
            payloadContent.add(kioskPayload);
        } else if (kioskLockdown.containsKey(MULTI_APP)) {
            logger.info("Multi App Kiosk mode (Home Screen Layout) detected for device: {}", device.getUdid());
            Map<String, Object> multiAppMap = (Map<String, Object>) kioskLockdown.get(MULTI_APP);
            payloadContent.add(HomeScreenLayout.createFromMap(multiAppMap, policyId).createPayload());
        } else if (kioskLockdown.containsKey(SAFARI_DOMAIN_LOCK)) {
            logger.info("Safari Domain Lock kiosk mode detected for device: {}", device.getUdid());
            Map<String, Object> safariMap = (Map<String, Object>) kioskLockdown.get(SAFARI_DOMAIN_LOCK);
            String domain = (String) safariMap.getOrDefault("domain", "");
            boolean allowSubdomains = Boolean.TRUE.equals(safariMap.get("allowSubdomains"));

            if (domain == null || domain.isBlank()) {
                logger.warn("Safari Domain Lock: domain is empty, skipping for device {}", device.getUdid());
            } else {
                // 1) WebClip — full-screen web app (FullScreen + IgnoreManifestScope hides all Safari UI)
                payloadContent.add(WebClip.createForKiosk(domain, policyId).createPayload());
                logger.info("Safari Domain Lock: WebClip payload added for device {}", device.getUdid());

                // 2) Restrictions — only allow WebClip app (hides all other apps from home screen)
                //    Note: Apple always forces one system app to remain visible alongside the allowlist:
                //    iPhone → Phone app (for emergency calls), iPad → Settings app.
                //    This is an Apple MDM platform limitation with allowListedAppBundleIDs.
                payloadContent.add(Restrictions.createForKioskAllowList(List.of("com.apple.webapp"), policyId));
                logger.info("Safari Domain Lock: Restrictions allowlist payload added for device {}", device.getUdid());

                // 3) WebContentFilter — restrict browsing to specified domain only
                payloadContent.add(WebContentFilter.createForDomainLock(domain, allowSubdomains, policyId).createPayload());
                logger.info("Safari Domain Lock: WebContentFilter payload added for device {}", device.getUdid());
            }
        }

        kioskLockdown.put(STATUS, STATUS_SENT);
    }

    // ==================== Agent Capabilities ====================

    /**
     * Extract dataServices from policy payload and map to agent-native keys.
     * Shared by iOS and macOS strategies.
     *
     * Handles nested format (stored in DB):
     *   dataServices.telemetry: { enabled: true, intervalSeconds: 1800 }
     *   dataServices.location:  { enabled: true, intervalSeconds: 1800 }
     */
    /**
     * Extracts agent data services config using "desired state" model.
     * Always emits telemetry/location keys — absent in policy means disabled.
     * Uses distinct interval keys (telemetryIntervalSeconds / locationIntervalSeconds)
     * matching what agents expect.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> extractDataServicesForAgent(Map<String, Object> payload) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Defaults: disabled
        boolean telemetryEnabled = false;
        boolean locationEnabled = false;
        Object telemetryIntervalVal = null;
        Object locationIntervalVal = null;

        if (payload != null) {
            Object dsObj = payload.get(DATA_SERVICES);
            if (dsObj instanceof Map<?, ?> dsRaw) {
                Map<String, Object> ds = (Map<String, Object>) dsRaw;

                if (ds.get(AGENT_TELEMETRY) instanceof Map<?, ?> telemetryRaw) {
                    Map<String, Object> telemetry = (Map<String, Object>) telemetryRaw;
                    telemetryEnabled = Boolean.TRUE.equals(telemetry.get("enabled"));
                    telemetryIntervalVal = telemetry.get(TELEMETRY_INTERVAL_SECONDS);
                }

                if (ds.get(AGENT_LOCATION) instanceof Map<?, ?> locationRaw) {
                    Map<String, Object> location = (Map<String, Object>) locationRaw;
                    locationEnabled = Boolean.TRUE.equals(location.get("enabled"));
                    locationIntervalVal = location.get(LOCATION_INTERVAL_SECONDS);
                }
            }
        }

        // Always send complete desired state
        result.put(AGENT_TELEMETRY, telemetryEnabled);
        result.put(AGENT_LOCATION, locationEnabled);
        if (telemetryIntervalVal != null) {
            result.put(AGENT_TELEMETRY_INTERVAL, telemetryIntervalVal);
        }
        if (locationIntervalVal != null) {
            result.put(AGENT_LOCATION_INTERVAL, locationIntervalVal);
        }

        return result;
    }

    // ==================== BYOD Filtering ====================

    private void filterForByod(List<NSDictionary> payloadContent) {
        int initialSize = payloadContent.size();

        payloadContent.removeIf(payload -> {
            String type = payload.containsKey("PayloadType")
                    ? payload.get("PayloadType").toString() : "";
            return BYOD_EXCLUDED_PAYLOAD_TYPES.contains(type);
        });

        int removedPayloads = initialSize - payloadContent.size();
        int removedKeys = 0;

        for (NSDictionary payload : payloadContent) {
            String payloadType = payload.containsKey("PayloadType")
                    ? payload.get("PayloadType").toString() : "";
            if ("com.apple.applicationaccess".equals(payloadType)) {
                for (String key : BYOD_EXCLUDED_RESTRICTION_KEYS) {
                    if (payload.containsKey(key)) {
                        payload.remove(key);
                        removedKeys++;
                    }
                }
            }
        }

        if (removedPayloads > 0 || removedKeys > 0) {
            logger.info("BYOD filter applied: removed {} payload(s) and {} restriction key(s)", removedPayloads, removedKeys);
        }
    }

    // ==================== Shared Payload Handlers ====================

    protected void handlePasscode(Map<String, Object> platformPolicy, List<NSDictionary> payloadContent) {
        if (!platformPolicy.containsKey(PASSCODE)) return;

        logger.debug("Passcode policy found. Creating passcode payload.");
        Map<String, Object> passcodeMap = (Map<String, Object>) platformPolicy.get(PASSCODE);
        NSDictionary passcode = Passcode.createFromMap(passcodeMap).createPayload();
        passcodeMap.replace(STATUS, STATUS_SENT);
        payloadContent.add(passcode);
    }

    protected void handleProfileRemovalPassword(Map<String, Object> platformPolicy, List<NSDictionary> payloadContent) {
        if (!platformPolicy.containsKey(PROFILE_REMOVAL_PASSWORD)) return;

        logger.debug("Profile removal password policy found.");
        Map<String, Object> profileRemovalPasswordMap = (Map<String, Object>) platformPolicy.get(PROFILE_REMOVAL_PASSWORD);
        NSDictionary profileRemovalPassword = ProfileRemovalPassword.createFromMap(profileRemovalPasswordMap).createPayload();
        profileRemovalPasswordMap.replace(STATUS, STATUS_SENT);
        payloadContent.add(profileRemovalPassword);
    }

    protected void handleWifi(Map<String, Object> networkConfigurationMap, List<NSDictionary> payloadContent,
                               Map<String, Object> platformPolicy, UUID policyId) {
        if (!networkConfigurationMap.containsKey(WIFI)) return;

        logger.info("Wi-Fi configuration found.");

        Map<String, Object> securityConfigurationMap = null;
        if (platformPolicy.containsKey(SECURITY_CONFIGURATION)) {
            Object secObj = platformPolicy.get(SECURITY_CONFIGURATION);
            if (secObj instanceof Map<?, ?> secRaw) {
                securityConfigurationMap = (Map<String, Object>) secRaw;
            }
        }

        List<Map<String, Object>> wifiMaps = (List<Map<String, Object>>) networkConfigurationMap.get(WIFI);
        for (Map<String, Object> wifiMap : wifiMaps) {
            Wifi wifiModel = Wifi.createFromMap(wifiMap, policyId);
            if (wifiModel == null) continue;
            wifiModel.tryResolveAndSetPayloadCertificateUUID(securityConfigurationMap, policyId);
            payloadContent.add(wifiModel.createPayload());
        }
    }

    protected void handleCellular(Map<String, Object> networkConfigurationMap, List<NSDictionary> payloadContent, UUID policyId) {
        if (!networkConfigurationMap.containsKey(CELLULAR)) return;

        logger.info("Cellular configuration found.");
        Map<String, Object> cellularMap = (Map<String, Object>) networkConfigurationMap.get(CELLULAR);
        NSDictionary cellular = Cellular.createFromMap(cellularMap, policyId).createPayload();
        payloadContent.add(cellular);
    }

    protected void handleVpn(Map<String, Object> networkConfigurationMap, List<NSDictionary> payloadContent) {
        if (!networkConfigurationMap.containsKey(VPN)) return;

        Object vpnObj = networkConfigurationMap.get(VPN);
        if (vpnObj instanceof Map<?, ?> singleVpnMapRaw) {
            Map<String, Object> singleVpnMap = (Map<String, Object>) singleVpnMapRaw;
            NSDictionary vpnPayload = buildVpnPayload(singleVpnMap);
            if (vpnPayload != null) payloadContent.add(vpnPayload);
        } else if (vpnObj instanceof List<?> vpnListRaw) {
            List<Map<String, Object>> vpnList = (List<Map<String, Object>>) vpnListRaw;
            for (Map<String, Object> vpnMap : vpnList) {
                NSDictionary vpnPayload = buildVpnPayload(vpnMap);
                if (vpnPayload != null) payloadContent.add(vpnPayload);
            }
        }
    }

    protected void handleWebClips(AppleDevice device, Map<String, Object> appMgmtMap,
                                   List<NSDictionary> payloadContent, UUID policyId) {
        if (!appMgmtMap.containsKey(WEB_CLIP)) return;

        logger.info("Web clips policy found. Processing web clips for device with ID: {}", device.getId());
        List<Map<String, Object>> webClipMaps = (List<Map<String, Object>>) appMgmtMap.get(WEB_CLIP);
        for (Map<String, Object> webClipMap : webClipMaps) {
            NSDictionary webClip = WebClip.createFromMap(webClipMap, policyId).createPayload();
            payloadContent.add(webClip);
        }
    }

    protected void handleNotifications(AppleDevice device, Map<String, Object> appMgmtMap,
                                        List<NSDictionary> payloadContent, UUID policyId,
                                        PolicyContext context) {
        if (!appMgmtMap.containsKey(NOTIFICATION)) return;

        // iOS requires supervision for com.apple.notificationsettings
        if (context != null && context.isIosFamily() && !context.isSupervised()) {
            logger.warn("Skipping notification settings for device {} — iOS requires supervision", device.getId());
            return;
        }

        List<Map<String, Object>> notificationMapList = (List<Map<String, Object>>) appMgmtMap.get(NOTIFICATION);
        if (notificationMapList == null || notificationMapList.isEmpty()) {
            return;
        }

        logger.info("Processing {} notification setting(s) for device {}", notificationMapList.size(), device.getId());

        List<Notification> notifications = new ArrayList<>();
        for (Map<String, Object> notificationMap : notificationMapList) {
            Notification notification = Notification.createFromMap(notificationMap, policyId);
            if (notification != null && notification.getBundleIdentifier() != null) {
                notifications.add(notification);
            }
        }

        if (!notifications.isEmpty()) {
            NSDictionary combinedPayload = Notification.createCombinedPayload(notifications, policyId);
            payloadContent.add(combinedPayload);
            logger.info("Created notification payload with {} app(s) for device {}", notifications.size(), device.getId());
        }
    }

    protected void handleSecurityConfiguration(AppleDevice device, Map<String, Object> platformPolicy, List<NSDictionary> payloadContent) {
        if (!platformPolicy.containsKey(SECURITY_CONFIGURATION)) return;

        logger.info("Security configuration policy found for device with ID: {}", device.getId());
        Map<String, Object> securityConfigurationMap = (Map<String, Object>) platformPolicy.get(SECURITY_CONFIGURATION);
        UUID policyId = UUID.fromString(securityConfigurationMap.get(POLICY_ID).toString());

        if (securityConfigurationMap.containsKey(SCEP)) {
            logger.info("SCEP configuration found for device with ID: {}", device.getId());
            Map<String, Object> scepMap = (Map<String, Object>) securityConfigurationMap.get(SCEP);
            Scep scepModel = Scep.createFromMap(scepMap, policyId);
            payloadContent.add(scepModel.createPayload());
        }

        if (securityConfigurationMap.containsKey(DNS_PROXY)) {
            logger.info("DNS Proxy configuration found for device with ID: {}", device.getId());
            Map<String, Object> dnsProxyMap = (Map<String, Object>) securityConfigurationMap.get(DNS_PROXY);
            payloadContent.add(DnsProxy.createFromMap(dnsProxyMap, policyId).createPayload());
        }

        if (securityConfigurationMap.containsKey(WEB_CONTENT_FILTER)) {
            logger.info("Web Content Filter configuration found for device with ID: {}", device.getId());
            Map<String, Object> webContentFilterMap = (Map<String, Object>) securityConfigurationMap.get(WEB_CONTENT_FILTER);
            payloadContent.add(WebContentFilter.createFromMap(webContentFilterMap, policyId).createPayload());
        }

        if (securityConfigurationMap.containsKey(GLOBAL_HTTP_PROXY)) {
            logger.info("Global HTTP Proxy configuration found for device with ID: {}", device.getId());
            Map<String, Object> globalHttpProxyMap = (Map<String, Object>) securityConfigurationMap.get(GLOBAL_HTTP_PROXY);
            payloadContent.add(GlobalHttpProxy.createFromMap(globalHttpProxyMap, policyId).createPayload());
        }
    }

    protected void handleConfigurations(AppleDevice device, Map<String, Object> platformPolicy, List<NSDictionary> payloadContent) {
        if (!platformPolicy.containsKey(CONFIGURATIONS)) return;

        logger.info("Configurations policy found for device with ID: {}", device.getId());
        Map<String, Object> configurationsMap = (Map<String, Object>) platformPolicy.get(CONFIGURATIONS);
        String policyID = (String) configurationsMap.get("policyId");
        UUID policyUuid = UUID.fromString(policyID);

        if (configurationsMap.containsKey(FONT)) {
            Map<String, Object> fontMap = (Map<String, Object>) configurationsMap.get(FONT);
            payloadContent.add(Font.createFromMap(fontMap, policyUuid).createPayload());
        }

        if (configurationsMap.containsKey(AIRPRINT)) {
            Map<String, Object> airprintMap = (Map<String, Object>) configurationsMap.get(AIRPRINT);
            payloadContent.add(AirPrint.createFromMap(airprintMap, policyUuid).createPayload());
        }

        if (configurationsMap.containsKey(LOCK_SCREEN_MESSAGE)) {
            Map<String, Object> lsMap = (Map<String, Object>) configurationsMap.get(LOCK_SCREEN_MESSAGE);
            payloadContent.add(LockScreenMessage.createFromMap(lsMap, policyUuid).createPayload());
        }

        if (configurationsMap.containsKey(AIRPLAY)) {
            Map<String, Object> airplayMap = (Map<String, Object>) configurationsMap.get(AIRPLAY);
            payloadContent.add(AirPlay.createFromMap(airplayMap, policyUuid).createPayload());
        }
    }

    protected void handleAppBlockList(AppleDevice device, Map<String, Object> appMgmtMap, List<NSDictionary> payloadContent) {
        if (!appMgmtMap.containsKey(APPLICATIONS_MANAGEMENT_BLOCK_LIST)) return;

        List<String> blockListAppGroups = (List<String>) appMgmtMap.get(APPLICATIONS_MANAGEMENT_BLOCK_LIST_APP_GROUPS);
        logger.info("Block list found in the application management policy for device with ID: {}", device.getId());

        NSDictionary restrictions = getOrCreateRestrictionsPayload(payloadContent);
        List<String> blockedApps = (List<String>) appMgmtMap.get(APPLICATIONS_MANAGEMENT_BLOCK_LIST);

        if (blockListAppGroups != null && !blockListAppGroups.isEmpty()) {
            blockedApps.addAll(addAppsFromGroups(device, blockListAppGroups, blockedApps, true));
        }
        restrictions.put(APPLICATIONS_MANAGEMENT_BLOCK_LIST, blockedApps);
    }

    protected void handleAppAllowList(AppleDevice device, Map<String, Object> appMgmtMap, List<NSDictionary> payloadContent) {
        if (!appMgmtMap.containsKey(APPLICATIONS_MANAGEMENT_ALLOW_LIST)) return;

        List<String> allowListAppGroups = (List<String>) appMgmtMap.get(APPLICATIONS_MANAGEMENT_ALLOW_LIST_APP_GROUPS);
        logger.info("Allow list found in the application management policy for device with ID: {}", device.getId());

        NSDictionary restrictions = getOrCreateRestrictionsPayload(payloadContent);
        List<String> allowedApps = (List<String>) appMgmtMap.get(APPLICATIONS_MANAGEMENT_ALLOW_LIST);

        if (allowListAppGroups != null && !allowListAppGroups.isEmpty()) {
            allowedApps.addAll(addAppsFromGroups(device, allowListAppGroups, allowedApps, true));
        }
        restrictions.put(APPLICATIONS_MANAGEMENT_ALLOW_LIST, allowedApps);
    }

    // ==================== Helper Methods ====================

    protected NSDictionary getOrCreateRestrictionsPayload(List<NSDictionary> payloadContent) {
        for (NSDictionary payload : payloadContent) {
            if (payload.containsKey("PayloadType") && "com.apple.applicationaccess".equals(payload.get("PayloadType").toString())) {
                return payload;
            }
        }
        NSDictionary restrictions = new Restrictions().createPayload();
        payloadContent.add(restrictions);
        return restrictions;
    }

    protected List<String> addAppsFromGroups(AppleDevice device, List<String> resultAppList, List<String> requiredApps, boolean addBundleId) {
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

    private NSDictionary buildVpnPayload(Map<String, Object> vpnMap) {
        if (vpnMap == null || vpnMap.isEmpty()) {
            logger.warn("buildVpnPayload called with empty vpnMap");
            return null;
        }
        String type = Optional.ofNullable(vpnMap.get("type"))
                .map(Object::toString)
                .map(String::trim)
                .map(String::toLowerCase)
                .orElse("");

        try {
            return switch (type) {
                case "ikev2" -> Vpn.createIKEv2VpnFromMap(vpnMap).convertIKEv2VpnToNSDictionary();
                case "always_on" -> Vpn.createAlwaysOnVpnFromMap(vpnMap).convertAlwaysOnVpnToNSDictionary();
                case "l2tp" -> Vpn.createL2TPVpnFromMap(vpnMap).convertL2TPVpnToNSDictionary();
                default -> {
                    logger.warn("Unsupported VPN type: '{}'. Expected: ikev2, always_on, l2tp", type);
                    yield null;
                }
            };
        } catch (Exception ex) {
            logger.error("Failed to build VPN payload for type '{}': {}", type, ex.getMessage(), ex);
            return null;
        }
    }
}

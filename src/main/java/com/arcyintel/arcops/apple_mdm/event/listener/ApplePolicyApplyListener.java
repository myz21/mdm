package com.arcyintel.arcops.apple_mdm.event.listener;


import com.arcyintel.arcops.apple_mdm.event.publisher.PolicyEventPublisher;
import com.arcyintel.arcops.apple_mdm.repositories.AppGroupRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandSenderService;
import com.arcyintel.arcops.apple_mdm.services.apple.policy.ApplePolicyApplicationService;
import com.arcyintel.arcops.commons.events.policy.PolicyApplicationFailedEvent;
import com.arcyintel.arcops.commons.events.policy.PolicyApplyEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.arcyintel.arcops.commons.constants.apple.CommandSpecificConfigurations.*;
import static com.arcyintel.arcops.commons.constants.events.PolicyEvents.*;
import static com.arcyintel.arcops.commons.constants.policy.PolicyTypes.PAYLOAD;


@Component
@RequiredArgsConstructor
public class ApplePolicyApplyListener {

    private static final Logger logger = LoggerFactory.getLogger(ApplePolicyApplyListener.class);
    private final AppleCommandSenderService appleCommandSenderService;
    private final ApplePolicyApplicationService policyApplicationService;
    private final AppleDeviceRepository appleDeviceRepository;
    private final PolicyEventPublisher policyEventPublisher;
    private final AppGroupRepository appGroupRepository;
    private final com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository itunesAppMetaRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Value("${host}")
    private String apiHost;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = POLICY_APPLY_QUEUE_APPLE, durable = "true"),
            exchange = @Exchange(value = POLICY_EVENT_EXCHANGE, type = "topic"),
            key = POLICY_APPLY_ROUTE_KEY_APPLE
    ))
    @Transactional
    public void handlePolicyApplyEvent(PolicyApplyEvent event) {

        logger.info("[Policy] handlePolicyApplyEvent received in Apple MDM Service: {}", event);

        appleDeviceRepository.findById(event.getDeviceId()).ifPresentOrElse(device -> {
                    logger.debug("Device found: {}", device.getId());

                    try {
                        // 1) Read the required apps blocks from old and new policies
                        var oldApplied = device.getAppliedPolicy(); // may be null
                        var newApplied = event.getAppliedPolicy();  // may be null/empty

                        Map<String, Object> oldReq = extractRequireAppsBlock(oldApplied);
                        Map<String, Object> newReq = extractRequireAppsBlock(newApplied);

                        // 2) Check removeOnRemoval (from old policy block)
                        boolean removeOnRemoval = false;
                        if (oldReq != null && oldReq.get(APPLICATIONS_MANAGEMENT_REQUIRE_APPS_REMOVE_ON_REMOVAL) instanceof Boolean b) {
                            removeOnRemoval = b;
                        }

                        // 3) Build comparable VPP app sets (adamIds/bundleIds) for old and new
                        Set<String> oldVppApps = resolveVppAppSet(device, oldReq);
                        Set<String> newVppApps = resolveVppAppSet(device, newReq);

                        // 4) Remove apps that were in old but not in new (only if removeOnRemoval)
                        if (removeOnRemoval && !oldVppApps.isEmpty()) {
                            if (newVppApps.isEmpty()) {
                                for (String id : oldVppApps) {
                                    appleCommandSenderService.removeApp(device.getUdid(), id);
                                    logger.info("Removed (policy cleared) app {} for device {}", id, device.getUdid());
                                }
                            } else {
                                for (String id : oldVppApps) {
                                    if (!newVppApps.contains(id)) {
                                        appleCommandSenderService.removeApp(device.getUdid(), id);
                                        logger.info("Removed (diff) app {} for device {}", id, device.getUdid());
                                    } else {
                                        logger.debug("Keeping app {} on device {} (still present in new policy)", id, device.getUdid());
                                    }
                                }
                            }
                        }

                        // Enrich declarative management configurations (e.g., software update settings)
                        enrichDeclarativeManagementConfigs(device.getId(), event.getAppliedPolicy());
                        // 5) Apply the new policy (installs will be handled there)
                        policyApplicationService.applyPolicy(device, newApplied);

                    } catch (Exception e) {
                        policyEventPublisher.publishPolicyApplicationFailedEvent(new PolicyApplicationFailedEvent(
                                device.getId(),
                                "Apple",
                                e.getMessage()
                        ));
                        logger.error("Error applying policy to device {}: {}", device.getId(), e.getMessage());
                    }

                    logger.info("Policy applied successfully to device {}.", device.getId());
                },
                () -> logger.warn("Device with ID {} not found.", event.getDeviceId()));
    }

    // --- Helpers ---

    private Map<String, Object> extractRequireAppsBlock(Map<String, Object> policyRoot) {
        if (policyRoot == null) return null;
        Object ios = policyRoot.get(PAYLOAD);
        if (!(ios instanceof Map<?, ?> iosMap)) return null;
        Object appMgmt = iosMap.get(APPLICATIONS_MANAGEMENT);
        if (!(appMgmt instanceof Map<?, ?> appMgmtMap)) return null;
        Object req = appMgmtMap.get(APPLICATIONS_MANAGEMENT_REQUIRE_APPS);
        if (req instanceof Map<?, ?> reqMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) reqMap;
            return cast;
        }
        return null;
    }

    /**
     * Build a set of VPP apps to compare. The set contains a normalized identifier per app:
     * - adamId (trackId as string) if available
     * - otherwise bundleId
     * It merges plain "apps" list with all VPP items coming from referenced app groups,
     * filtered by device platform (ios/mac) using itunes_app_meta.supported_platforms.
     */
    private Set<String> resolveVppAppSet(com.arcyintel.arcops.apple_mdm.domains.AppleDevice device, Map<String, Object> reqBlock) {
        if (reqBlock == null) return java.util.Collections.emptySet();

        java.util.Set<String> result = new java.util.HashSet<>();

        // 1) Direct apps list — each item is now {"bundleId": "...", "type": "vpp"|"enterprise"}
        Object appsObj = reqBlock.get(APPLICATIONS_MANAGEMENT_REQUIRE_APPS_APPS);
        if (appsObj instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> appMap) {
                    Object bundleId = appMap.get("bundleId");
                    if (bundleId != null && !bundleId.toString().isBlank()) {
                        result.add(bundleId.toString().trim());
                    }
                } else if (o != null) {
                    // Fallback for legacy plain string format
                    result.add(o.toString().trim());
                }
            }
        }

        // 2) App groups -> merge only VPP items, platform-matching
        Object groupsObj = reqBlock.get(APPLICATIONS_MANAGEMENT_REQUIRE_APPS_APP_GROUPS);
        java.util.List<java.util.UUID> groupIds = new java.util.ArrayList<>();
        if (groupsObj instanceof java.util.List<?> gList) {
            for (Object g : gList) {
                try {
                    groupIds.add(java.util.UUID.fromString(String.valueOf(g)));
                } catch (Exception ignore) {
                }
            }
        }
        if (!groupIds.isEmpty()) {
            // Load groups once
            var groups = appGroupRepository.findByIdIn(groupIds);
            String product = device.getProductName() != null ? device.getProductName().toLowerCase() : "";
            boolean isIos = product.contains("iphone") || product.contains("ipad");
            boolean isMac = product.contains("mac");

            // Collect all VPP trackIds to batch-load platform info from itunes_app_meta
            java.util.List<Long> trackIds = new java.util.ArrayList<>();
            for (var g : groups) {
                for (var item : g.getItems()) {
                    if (item.getAppType() != com.arcyintel.arcops.apple_mdm.domains.AppGroup.AppType.VPP) continue;
                    if (item.getTrackId() != null && !item.getTrackId().isBlank()) {
                        try { trackIds.add(Long.parseLong(item.getTrackId().trim())); } catch (NumberFormatException ignore) {}
                    }
                }
            }
            // Build trackId -> supportedPlatforms lookup from itunes_app_meta
            java.util.Map<Long, java.util.List<String>> platformLookup = new java.util.HashMap<>();
            if (!trackIds.isEmpty()) {
                for (var meta : itunesAppMetaRepository.findAllByTrackIdIn(trackIds)) {
                    platformLookup.put(meta.getTrackId(),
                            meta.getSupportedPlatforms() != null
                                    ? meta.getSupportedPlatforms().stream().map(s -> s == null ? "" : s.toLowerCase()).toList()
                                    : java.util.List.of());
                }
            }

            for (var g : groups) {
                for (var item : g.getItems()) {
                    if (item.getAppType() != com.arcyintel.arcops.apple_mdm.domains.AppGroup.AppType.VPP) continue;

                    String trackId = item.getTrackId();
                    java.util.List<String> platforms = java.util.List.of();
                    if (trackId != null && !trackId.isBlank()) {
                        try {
                            platforms = platformLookup.getOrDefault(Long.parseLong(trackId.trim()), java.util.List.of());
                        } catch (NumberFormatException ignore) {}
                    }

                    boolean platformOk = platforms.isEmpty() // if not specified, assume all
                            || (isIos && platforms.contains("ios"))
                            || (isMac && platforms.contains("macos"));

                    if (!platformOk) continue;

                    String bundleId = item.getBundleId();
                    if (trackId != null && !trackId.isBlank()) {
                        result.add(trackId.trim());
                    } else if (bundleId != null && !bundleId.isBlank()) {
                        result.add(bundleId.trim());
                    }
                }
            }
        }

        return result;
    }

    // --- Account type mapping ---
    private static final String ACCOUNTS = "accounts";
    private static final String SOFTWARE_UPDATE = "softwareUpdate";
    private static final String WATCH_ENROLLMENT = "watchEnrollment";
    private static final String SECURITY_CONFIGURATION = "securityConfiguration";

    private static final Map<String, String> ACCOUNT_TYPE_MAP = Map.of(
            "caldavAccount", "com.apple.configuration.account.caldav",
            "carddavAccount", "com.apple.configuration.account.carddav",
            "googleAccount", "com.apple.configuration.account.google",
            "ldapAccount", "com.apple.configuration.account.ldap",
            "subscribedCalendar", "com.apple.configuration.account.subscribed-calendar",
            "mailAccount", "com.apple.configuration.account.mail",
            "exchangeAccount", "com.apple.configuration.account.exchange"
    );

    /**
     * Builds declarativeManagement from payload.accounts and payload.securityConfiguration
     * (softwareUpdate, watchEnrollment). The input payload no longer contains a declarativeManagement
     * block; the system generates it automatically.
     */
    @SuppressWarnings("unchecked")
    private void enrichDeclarativeManagementConfigs(UUID deviceId, Map<String, Object> rootPolicyMap) {
        if (rootPolicyMap == null || rootPolicyMap.isEmpty()) return;

        Object iosObj = rootPolicyMap.get(PAYLOAD);
        if (!(iosObj instanceof Map<?, ?> iosRaw)) return;
        Map<String, Object> iosPolicy = (Map<String, Object>) iosRaw;

        // Collect configurations and assets from accounts + securityConfiguration
        final java.util.List<Map<String, Object>> configurations = new java.util.ArrayList<>();
        final java.util.List<Map<String, Object>> assetDeclarations = new java.util.ArrayList<>();
        final java.util.Map<String, Object> resolvedAssetData = new java.util.HashMap<>();
        final java.util.List<Map<String, Object>> assetDataList = new java.util.ArrayList<>();

        // 1) Process accounts
        Object accountsObj = iosPolicy.get(ACCOUNTS);
        if (accountsObj instanceof Map<?, ?> accountsRaw) {
            Map<String, Object> accounts = (Map<String, Object>) accountsRaw;
            for (Map.Entry<String, String> entry : ACCOUNT_TYPE_MAP.entrySet()) {
                String accountKey = entry.getKey();
                String appleType = entry.getValue();
                Object accountObj = accounts.get(accountKey);
                if (!(accountObj instanceof Map<?, ?> accountRaw)) continue;
                Map<String, Object> accountData = new java.util.HashMap<>((Map<String, Object>) accountRaw);

                buildAccountConfiguration(deviceId, accountKey, appleType, accountData,
                        configurations, assetDeclarations, resolvedAssetData, assetDataList);
            }
        }

        // 2) Process securityConfiguration -> softwareUpdate, watchEnrollment
        Object secCfgObj = iosPolicy.get(SECURITY_CONFIGURATION);
        if (secCfgObj instanceof Map<?, ?> secCfgRaw) {
            Map<String, Object> secCfg = (Map<String, Object>) secCfgRaw;

            Object swObj = secCfg.get(SOFTWARE_UPDATE);
            if (swObj instanceof Map<?, ?> swRaw) {
                Map<String, Object> cfgMap = new java.util.HashMap<>();
                cfgMap.put("Type", "com.apple.configuration.softwareupdate.settings");
                cfgMap.put("Payload", new java.util.HashMap<>((Map<String, Object>) swRaw));
                cfgMap.put("Identifier", UUID.randomUUID().toString());
                cfgMap.put("ServerToken", generateServerToken(cfgMap));
                configurations.add(cfgMap);
                secCfg.remove(SOFTWARE_UPDATE);
            }

            Object weObj = secCfg.get(WATCH_ENROLLMENT);
            if (weObj instanceof Map<?, ?> weRaw) {
                Map<String, Object> wePayload = new java.util.HashMap<>((Map<String, Object>) weRaw);
                wePayload.put("EnrollmentProfileURL", apiHost + "/mdm/enrollment");

                Map<String, Object> cfgMap = new java.util.HashMap<>();
                cfgMap.put("Type", "com.apple.configuration.watch.enrollment");
                cfgMap.put("Payload", wePayload);
                cfgMap.put("Identifier", UUID.randomUUID().toString());
                cfgMap.put("ServerToken", generateServerToken(cfgMap));
                configurations.add(cfgMap);
                secCfg.remove(WATCH_ENROLLMENT);
            }
        }

        // If nothing to declare, skip
        if (configurations.isEmpty() && assetDeclarations.isEmpty()) return;

        // 3) Build declarativeManagement map
        Map<String, Object> dmMap = new java.util.HashMap<>();
        if (!configurations.isEmpty()) dmMap.put("configurations", configurations);
        if (!assetDeclarations.isEmpty()) dmMap.put("assets", assetDeclarations);
        if (!assetDataList.isEmpty()) dmMap.put("assetData", assetDataList);
        if (!resolvedAssetData.isEmpty()) dmMap.put("resolvedAssetData", resolvedAssetData);

        // Change detection
        Object existingDmObj = iosPolicy.get(DECLARATIVE_MANAGEMENT);
        String existingDmToken = null;
        if (existingDmObj instanceof Map<?, ?> existingDm) {
            Object tok = ((Map<String, Object>) existingDm).get("server_token");
            if (tok != null) existingDmToken = tok.toString();
        }
        String computedDmToken = generateDeterministicHash(dmMap);
        if (existingDmToken != null && existingDmToken.equals(computedDmToken)) {
            logger.info("declarative_management content unchanged.");
            return;
        }
        dmMap.put("server_token", computedDmToken);

        iosPolicy.put(DECLARATIVE_MANAGEMENT, dmMap);
    }

    /**
     * Builds a single account configuration and its associated assets.
     */
    @SuppressWarnings("unchecked")
    private void buildAccountConfiguration(UUID deviceId, String accountKey, String appleConfigType,
                                           Map<String, Object> accountData,
                                           List<Map<String, Object>> configurations,
                                           List<Map<String, Object>> assetDeclarations,
                                           Map<String, Object> resolvedAssetData,
                                           List<Map<String, Object>> assetDataList) {

        Map<String, Object> cfgMap = new java.util.HashMap<>();
        cfgMap.put("Type", appleConfigType);

        if ("mailAccount".equals(accountKey)) {
            buildMailConfiguration(deviceId, accountData, cfgMap, assetDeclarations, resolvedAssetData, assetDataList);
        } else if ("googleAccount".equals(accountKey)) {
            buildGoogleConfiguration(deviceId, accountData, cfgMap, assetDeclarations, assetDataList);
        } else if ("exchangeAccount".equals(accountKey)) {
            // Exchange: no credential, just payload
            cfgMap.put("Payload", accountData);
        } else {
            // CalDAV, CardDAV, LDAP, SubscribedCalendar: optional credential → reference asset
            buildSimpleCredentialConfiguration(deviceId, accountKey, accountData, cfgMap,
                    assetDeclarations, resolvedAssetData, assetDataList);
        }

        cfgMap.put("Identifier", UUID.randomUUID().toString());
        cfgMap.put("ServerToken", generateServerToken(cfgMap));
        configurations.add(cfgMap);
    }

    /**
     * CalDAV / CardDAV / LDAP / SubscribedCalendar: extract optional credential, bind as reference asset.
     */
    @SuppressWarnings("unchecked")
    private void buildSimpleCredentialConfiguration(UUID deviceId, String accountKey,
                                                    Map<String, Object> accountData, Map<String, Object> cfgMap,
                                                    List<Map<String, Object>> assetDeclarations,
                                                    Map<String, Object> resolvedAssetData,
                                                    List<Map<String, Object>> assetDataList) {
        Map<String, Object> credential = extractAndRemove(accountData, "credential");
        cfgMap.put("Payload", accountData);

        if (credential != null && !credential.isEmpty()) {
            String assetName = accountKey + "-cred";
            Map<String, Object> assetData = new java.util.HashMap<>();
            assetData.put("Type", "com.apple.credential.usernameandpassword");
            assetData.put("name", assetName);
            assetData.put("Payload", credential);
            assetDataList.add(assetData);

            Map<String, Map<String, Object>> credByName = Map.of(assetName, assetData);
            cfgMap.put("name", assetName);
            bindAsset(deviceId, cfgMap, "name", "AuthenticationCredentialsAssetReference",
                    "com.apple.asset.credential.userpassword", credByName, assetDeclarations, resolvedAssetData, null);
        }
    }

    /**
     * Google account: credential (FullName + EmailAddress) is required, produces inline identity asset.
     */
    @SuppressWarnings("unchecked")
    private void buildGoogleConfiguration(UUID deviceId, Map<String, Object> accountData,
                                          Map<String, Object> cfgMap,
                                          List<Map<String, Object>> assetDeclarations,
                                          List<Map<String, Object>> assetDataList) {
        Map<String, Object> credential = extractAndRemove(accountData, "credential");
        cfgMap.put("Payload", accountData);

        if (credential != null && !credential.isEmpty()) {
            String assetName = "googleAccount-identity";
            Map<String, Object> assetData = new java.util.HashMap<>();
            assetData.put("Type", "com.apple.asset.useridentity");
            assetData.put("name", assetName);
            assetData.put("Payload", credential);
            assetDataList.add(assetData);

            Map<String, Map<String, Object>> credByName = Map.of(assetName, assetData);
            cfgMap.put("name", assetName);
            bindAsset(deviceId, cfgMap, "name", "UserIdentityAssetReference",
                    "com.apple.asset.useridentity", credByName, assetDeclarations, new java.util.HashMap<>(), null);
        }
    }

    /**
     * Mail account: up to 3 assets (identity inline, incoming credential reference, outgoing credential reference).
     * All optional.
     */
    @SuppressWarnings("unchecked")
    private void buildMailConfiguration(UUID deviceId, Map<String, Object> accountData,
                                        Map<String, Object> cfgMap,
                                        List<Map<String, Object>> assetDeclarations,
                                        Map<String, Object> resolvedAssetData,
                                        List<Map<String, Object>> assetDataList) {
        // Extract identity
        Map<String, Object> identity = extractAndRemove(accountData, "identity");

        // Extract IncomingServer.credential
        Map<String, Object> incomingCredential = null;
        Object inObj = accountData.get("IncomingServer");
        if (inObj instanceof Map<?, ?> inMap) {
            Map<String, Object> inServer = new java.util.HashMap<>((Map<String, Object>) inMap);
            incomingCredential = extractAndRemove(inServer, "credential");
            accountData.put("IncomingServer", inServer);
        }

        // Extract OutgoingServer.credential
        Map<String, Object> outgoingCredential = null;
        Object outObj = accountData.get("OutgoingServer");
        if (outObj instanceof Map<?, ?> outMap) {
            Map<String, Object> outServer = new java.util.HashMap<>((Map<String, Object>) outMap);
            outgoingCredential = extractAndRemove(outServer, "credential");
            accountData.put("OutgoingServer", outServer);
        }

        cfgMap.put("Payload", accountData);

        Map<String, Map<String, Object>> credByName = new java.util.HashMap<>();

        // 1) Identity (inline)
        if (identity != null && !identity.isEmpty()) {
            String identityName = "mailAccount-identity";
            Map<String, Object> identityAsset = new java.util.HashMap<>();
            identityAsset.put("Type", "com.apple.asset.useridentity");
            identityAsset.put("name", identityName);
            identityAsset.put("Payload", identity);
            assetDataList.add(identityAsset);
            credByName.put(identityName, identityAsset);
            cfgMap.put("name", identityName);
            bindAsset(deviceId, cfgMap, "name", "UserIdentityAssetReference",
                    "com.apple.asset.useridentity", credByName, assetDeclarations, resolvedAssetData, null);
        }

        // 2) Incoming credential (reference)
        if (incomingCredential != null && !incomingCredential.isEmpty()) {
            String inName = "mailAccount-in-cred";
            Map<String, Object> inAsset = new java.util.HashMap<>();
            inAsset.put("Type", "com.apple.credential.usernameandpassword");
            inAsset.put("name", inName);
            inAsset.put("Payload", incomingCredential);
            assetDataList.add(inAsset);
            credByName.put(inName, inAsset);
            cfgMap.put("incomingName", inName);
            bindAsset(deviceId, cfgMap, "incomingName", "AuthenticationCredentialsAssetReference",
                    "com.apple.asset.credential.userpassword", credByName, assetDeclarations, resolvedAssetData, "IncomingServer");
        }

        // 3) Outgoing credential (reference)
        if (outgoingCredential != null && !outgoingCredential.isEmpty()) {
            String outName = "mailAccount-out-cred";
            Map<String, Object> outAsset = new java.util.HashMap<>();
            outAsset.put("Type", "com.apple.credential.usernameandpassword");
            outAsset.put("name", outName);
            outAsset.put("Payload", outgoingCredential);
            assetDataList.add(outAsset);
            credByName.put(outName, outAsset);
            cfgMap.put("outgoingName", outName);
            bindAsset(deviceId, cfgMap, "outgoingName", "AuthenticationCredentialsAssetReference",
                    "com.apple.asset.credential.userpassword", credByName, assetDeclarations, resolvedAssetData, "OutgoingServer");
        }
    }

    /**
     * Extracts a nested map by key and removes it from the source map. Returns null if not found.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAndRemove(Map<String, Object> source, String key) {
        Object obj = source.remove(key);
        if (obj instanceof Map<?, ?> map) {
            return new java.util.HashMap<>((Map<String, Object>) map);
        }
        return null;
    }

    /**
     * Generates a hex-encoded SHA-256 hash of the configuration map combined with the current timestamp.
     * Falls back to a random UUID string if SHA-256 is unavailable.
     * Uses canonical JSON serialization (sorted keys) for stable hashes.
     */
    private String generateServerToken(Map<String, Object> configMap) {
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(configMap);
        } catch (Exception e) {
            serialized = String.valueOf(configMap);
        }
        String timestamp = Instant.now().toString();
        String toHash = serialized + "|" + timestamp;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("[WARN] Failed to compute SHA-256 for ServerToken, falling back to UUID. Error: {}", e.getMessage());
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    /**
     * Generates a deterministic hex-encoded SHA-256 hash of the given declarative_management map
     * WITHOUT any salt, using canonical JSON serialization (sorted keys).
     * <p>
     * IMPORTANT:
     * - Ignores previously generated fields:
     * - dm.server_token
     * - per-config Identifier / ServerToken
     */
    private String generateDeterministicHash(Map<String, Object> dmMap) {
        try {
            Map<String, Object> normalized = deepCopyAndNormalizeForDmHash(dmMap);

            String canonicalJson = objectMapper.writeValueAsString(normalized);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("[WARN] Failed to compute SHA-256 for deterministic hash, falling back to UUID. Error: {}", e.getMessage());
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyAndNormalizeForDmHash(Map<String, Object> dmMap) {
        // Shallow copy dmMap and remove dm-level server_token
        Map<String, Object> out = new java.util.TreeMap<>();
        for (Map.Entry<String, Object> e : dmMap.entrySet()) {
            if (e.getKey() == null) continue;
            if ("server_token".equals(e.getKey())) continue;
            out.put(e.getKey(), normalizeValueForDmHash(e.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object normalizeValueForDmHash(Object v) {
        if (v == null) return null;

        if (v instanceof Map<?, ?> m) {
            java.util.TreeMap<String, Object> tm = new java.util.TreeMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null) continue;
                String k = e.getKey().toString();

                // Ignore per-config generated fields for deterministic hash
                if ("Identifier".equals(k) || "ServerToken".equals(k)) continue;

                tm.put(k, normalizeValueForDmHash(e.getValue()));
            }
            return tm;
        }

        if (v instanceof List<?> list) {
            java.util.List<Object> out = new java.util.ArrayList<>(list.size());
            for (Object item : list) {
                out.add(normalizeValueForDmHash(item));
            }
            return out;
        }

        return v;
    }

    /**
     * Helper to bind an asset to a configuration payload or a sub-object within the payload.
     * Identity assets (useridentity) are inline and NOT added to resolvedAssetData.
     * Credential assets (userpassword) are reference-based and stored in resolvedAssetData.
     */
    @SuppressWarnings("unchecked")
    private void bindAsset(UUID deviceId, Map<String, Object> cfgMap, String configNameKey, String payloadRefKey, String appleAssetType,
                           Map<String, Map<String, Object>> credentialByName, List<Map<String, Object>> assetDeclarations,
                           Map<String, Object> resolvedAssetData, String subObjectKey) {

        Object assetNameObj = cfgMap.get(configNameKey);
        String assetName = (assetNameObj == null) ? null : String.valueOf(assetNameObj);

        if (assetName != null && !assetName.isBlank() && credentialByName.containsKey(assetName)) {
            Map<String, Object> sourceData = credentialByName.get(assetName);

            String assetIdentifier = UUID.randomUUID().toString();
            String assetServerToken = generateServerToken(sourceData);

            // Create Asset Declaration
            Map<String, Object> assetDecl = new java.util.HashMap<>();
            assetDecl.put("Type", appleAssetType);
            assetDecl.put("Identifier", assetIdentifier);
            assetDecl.put("ServerToken", assetServerToken);

            // STRATEGY: useridentity is INLINE, userpassword is REFERENCE
            if ("com.apple.asset.useridentity".equals(appleAssetType)) {
                // Inline: Put actual data (FullName, EmailAddress) into Payload
                Object sourcePayload = sourceData.get("Payload");
                assetDecl.put("Payload", sourcePayload != null ? sourcePayload : new java.util.HashMap<>());

                // DO NOT add to resolvedAssetData as it's not needed for DataURL
            } else {
                // Reference: Create DataURL reference
                Map<String, Object> assetPayload = new java.util.HashMap<>();
                Map<String, Object> reference = new java.util.HashMap<>();
                reference.put("DataURL", apiHost + "/mdm/checkin/" + deviceId + "/" + assetIdentifier);
                reference.put("ContentType", "application/json");
                assetPayload.put("Reference", reference);
                assetDecl.put("Payload", assetPayload);

                // Add to resolvedAssetData for the GET DataURL endpoint to serve the šifre
                resolvedAssetData.put(assetIdentifier, sourceData);
            }

            assetDeclarations.add(assetDecl);

            // Bind identifier to Config Payload (Directly or inside SubObject)
            Object pObj = cfgMap.get("Payload");
            if (pObj instanceof Map<?, ?> p) {
                Map<String, Object> payloadMap = (Map<String, Object>) p;
                if (subObjectKey == null) {
                    payloadMap.put(payloadRefKey, assetIdentifier);
                } else {
                    Object subObj = payloadMap.get(subObjectKey);
                    if (subObj instanceof Map<?, ?> sub) {
                        ((Map<String, Object>) sub).put(payloadRefKey, assetIdentifier);
                    }
                }
            }
            logger.info("[Policy] Bound {} config to {} asset (name={}, inline={}) using key {}",
                    cfgMap.get("Type"), appleAssetType, assetName,
                    "com.apple.asset.useridentity".equals(appleAssetType), payloadRefKey);
        }
    }
}
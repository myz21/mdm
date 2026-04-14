package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSDictionary;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Wifi extends BasePayload {


    // Network
    private String ssid;
    private boolean autoJoin = true;
    private boolean hiddenNetwork = false;
    private boolean disableMACAddressRandomization = false;
    private String password;
    private String encryptionType = "Any";
    private boolean captiveBypass = false;
    private boolean enableIPv6 = true;
    private boolean allowJoinBeforeFirstUnlock = false;

    // Proxy
    private String proxyType = "None";
    private String proxyServer;
    private int proxyPort;
    private String proxyUsername;
    private String proxyPassword;
    private String proxyPACURL;
    private boolean proxyPACFallbackAllowed = false;

    // EAP (Protocols)
    private int[] acceptedEAPTypes;
    private boolean eAPFastUsePAC;
    private boolean eAPFastProvisionPAC;
    private boolean eAPFASTProvisionPACAnonymously;
    private String tlsInnerAuthentication = "MSCHAPv2";
    private String tlsMinimumVersion = "1.0";
    private String tlsMaximumVersion = "1.2";
    private boolean tlsCertificateRequired = false;
    private List<String> tlsTrustedServerNames;

    // Authentication
    private String username;
    private boolean oneTimeUserPassword = false;
    private String userPassword;

    /**
     * Apple Wi-Fi payloadındaki `PayloadCertificateUUID` alanı.
     * Bunu biz, seçilen Identity Certificate payload’ının UUID’si ile dolduracağız.
     */
    private String payloadCertificateUUID;

    /**
     * UI’de gördüğün “Identity Certificate” dropdown’ının değeri.
     * Örn: "uconos-device-cert".
     * Bu isim, SCEP / diğer certificate payload’larının adı ile eşleşecek.
     */
    private String identityCertificateName;

    private String outerIdentity;

    /**
     * Resolves the SCEP payload UUID for the given identity certificate logical name.
     *
     * The caller provides the identity dropdown value (logicalName) and the raw SecurityConfiguration map.
     * This method:
     *  - Looks for SCEP in the security map
     *  - Builds a Scep model to obtain its PayloadUUID
     *  - Compares the Scep logical name (configurationName or name) with the Wi-Fi identity logicalName
     *  - Returns the matching SCEP payload UUID if matched; otherwise null.
     */
    @SuppressWarnings("unchecked")
    public static String resolvePayloadCertificateUuidFromSecurityMap(String logicalName,
                                                                     Map<String, Object> securityConfigurationMap,
                                                                     UUID policyId) {
        if (logicalName == null || logicalName.isBlank()) {
            return null;
        }
        if (securityConfigurationMap == null || securityConfigurationMap.isEmpty()) {
            return null;
        }
        if (policyId == null) {
            return null;
        }

        // Try direct key first.
        Object scepObj = securityConfigurationMap.get("SCEP");

        // If keys differ in casing, fall back to scanning entries.
        if (scepObj == null) {
            for (Map.Entry<String, Object> e : securityConfigurationMap.entrySet()) {
                if (e == null || e.getKey() == null) continue;
                if (e.getKey().equalsIgnoreCase("SCEP")) {
                    scepObj = e.getValue();
                    break;
                }
            }
        }

        if (!(scepObj instanceof Map<?, ?> scepRaw)) {
            return null;
        }

        Map<String, Object> scepMap = (Map<String, Object>) scepRaw;
        if (scepMap.isEmpty()) {
            return null;
        }

        // Build the Scep model to reuse its existing parsing and deterministic PayloadUUID generation.
        com.arcyintel.arcops.apple_mdm.models.profile.Scep scepModel =
                com.arcyintel.arcops.apple_mdm.models.profile.Scep.createFromMap(scepMap, policyId);
        if (scepModel == null) {
            return null;
        }

        String scepLogicalName = Optional.ofNullable(scepModel.getConfigurationName())
                .filter(s -> !s.isBlank())
                .orElseGet(scepModel::getName);

        if (scepLogicalName == null || scepLogicalName.isBlank()) {
            return null;
        }

        if (!scepLogicalName.equals(logicalName)) {
            return null;
        }

        String uuid = scepModel.getPayloadUUID();
        if (uuid == null || uuid.isBlank()) {
            return null;
        }

        return uuid;
    }

    /**
     * If this Wi-Fi payload has an Identity Certificate logical name, tries to resolve the
     * corresponding SCEP payload UUID from the given SecurityConfiguration map and sets
     * {@code payloadCertificateUUID} accordingly.
     *
     * No-op if:
     * - identityCertificateName is blank
     * - securityConfigurationMap is null/empty
     * - policyId is null
     * - a matching SCEP entry cannot be found
     */
    public void tryResolveAndSetPayloadCertificateUUID(Map<String, Object> securityConfigurationMap, UUID policyId) {
        String logicalName = getIdentityCertificateName();
        if (logicalName == null || logicalName.isBlank()) {
            return;
        }

        String resolvedUuid = resolvePayloadCertificateUuidFromSecurityMap(logicalName, securityConfigurationMap, policyId);
        if (resolvedUuid == null || resolvedUuid.isBlank()) {
            return;
        }

        setPayloadCertificateUUID(resolvedUuid);
    }



    @SuppressWarnings("unchecked")
    public static Wifi createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }
        Wifi wifi = Wifi.builder()
                .ssid((String) map.get("ssid"))
                .autoJoin(toBool(map.get("autoJoin"), true))
                .hiddenNetwork(toBool(map.get("hiddenNetwork"), false))
                .disableMACAddressRandomization(toBool(map.get("disableMACAddressRandomization"), false))
                .encryptionType(map.getOrDefault("encryptionType", "Any").toString())
                .captiveBypass(toBool(map.get("captiveBypass"), false))
                .enableIPv6(toBool(map.get("enableIPv6"), true))
                .allowJoinBeforeFirstUnlock(toBool(map.get("allowJoinBeforeFirstUnlock"), false))
                .proxyType(map.getOrDefault("proxyType", "None").toString())
                .build();

        // Basit WPA/WEP/WPA3 şifreli ağ
        if (Objects.equals(wifi.encryptionType, "WEP") ||
                Objects.equals(wifi.encryptionType, "WPA/WPA2") ||
                Objects.equals(wifi.encryptionType, "WPA3") ||
                Objects.equals(wifi.encryptionType, "Any")) {

            wifi.setPassword((String) map.get("password"));

            // Enterprise Wi-Fi
        } else if ((Objects.equals(wifi.encryptionType, "WEP_ENTERPRISE") ||
                Objects.equals(wifi.encryptionType, "WPA_WPA2_ENTERPRISE") ||
                Objects.equals(wifi.encryptionType, "WPA3_ENTERPRISE") ||
                Objects.equals(wifi.encryptionType, "Any_ENTERPRISE"))
                && map.containsKey("protocols")) {

            Map<String, Object> protocols = (Map<String, Object>) map.get("protocols");

            Object eapTypesObj = protocols.get("acceptedEAPTypes");
            if (eapTypesObj instanceof int[] rawArr) {
                wifi.setAcceptedEAPTypes(rawArr);
            } else if (eapTypesObj instanceof java.util.List<?> list) {
                int[] arr = list.stream()
                        .filter(Objects::nonNull)
                        .mapToInt(o -> ((Number) o).intValue())
                        .toArray();
                wifi.setAcceptedEAPTypes(arr);
            }

            wifi.setEAPFastUsePAC((boolean) protocols.getOrDefault("eAPFastUsePAC", true));
            wifi.setEAPFastProvisionPAC((boolean) protocols.getOrDefault("eAPFastProvisionPAC", true));
            wifi.setEAPFASTProvisionPACAnonymously((boolean) protocols.getOrDefault("eAPFASTProvisionPACAnonymously", false));
            wifi.setTlsInnerAuthentication(protocols.getOrDefault("tlsInnerAuthentication", "MSCHAPv2").toString());
            wifi.setTlsMinimumVersion(protocols.getOrDefault("tlsMinimumVersion", "1.0").toString());
            wifi.setTlsMaximumVersion(protocols.getOrDefault("tlsMaximumVersion", "1.2").toString());
            wifi.setTlsCertificateRequired((boolean) protocols.getOrDefault("tlsCertificateRequired", false));
            if (protocols.get("tlsTrustedServerNames") instanceof List<?> names) {
                wifi.setTlsTrustedServerNames(names.stream().map(Object::toString).toList());
            }

            if (map.containsKey("authentication")) {
                Map<String, Object> authentication = (Map<String, Object>) map.get("authentication");

                Object usernameObj = authentication.get("username");
                if (usernameObj != null) {
                    wifi.setUsername(usernameObj.toString());
                }

                wifi.setOneTimeUserPassword((boolean) authentication.getOrDefault("oneTimeUserPassword", false));

                Object pwdObj = authentication.get("userPassword");
                if (pwdObj != null) {
                    wifi.setUserPassword(pwdObj.toString());
                }

                // UI’de seçilen identity adı (dropdown)
                Object identityNameObj = authentication.get("identityCertificateName");
                if (identityNameObj != null) {
                    wifi.setIdentityCertificateName(identityNameObj.toString());
                }

                Object outerIdObj = authentication.get("outerIdentity");
                if (outerIdObj != null) {
                    wifi.setOuterIdentity(outerIdObj.toString());
                }
            }
        }

        if (Objects.equals(wifi.proxyType, "Manual")) {
            wifi.setProxyServer((String) map.get("proxyServer"));
            wifi.setProxyPort(toInt(map.get("proxyPort"), 0));
            wifi.setProxyUsername((String) map.get("proxyUsername"));
            wifi.setProxyPassword((String) map.get("proxyPassword"));
        } else if (Objects.equals(wifi.proxyType, "Auto")) {
            wifi.setProxyPACURL((String) map.get("proxyPACURL"));
            wifi.setProxyPACFallbackAllowed(toBool(map.get("proxyPACFallbackAllowed"), false));
        }

        wifi.setPayloadIdentifier(String.format("policy_wifi-%s", policyId));
        wifi.setPayloadType("com.apple.wifi.managed");
        wifi.setPayloadUUID(UUID.randomUUID().toString());
        wifi.setPayloadVersion(1);

        return wifi;
    }

    /**
     * Maps internal encryption type values to Apple-valid EncryptionType plist values.
     * Apple accepts: WEP, WPA, WPA2, WPA3, Any, None.
     * Enterprise is determined by the presence of EAPClientConfiguration, not EncryptionType.
     */
    private String mapEncryptionTypeForPayload() {
        return switch (this.encryptionType) {
            case "WPA/WPA2" -> "WPA";
            case "WEP_ENTERPRISE" -> "WEP";
            case "WPA_WPA2_ENTERPRISE" -> "WPA";
            case "WPA3_ENTERPRISE" -> "WPA3";
            case "Any_ENTERPRISE" -> "Any";
            default -> this.encryptionType; // WEP, WPA2, WPA3, Any, None
        };
    }

    public NSDictionary createPayload() {
        NSDictionary wifiSettings = new NSDictionary();

        wifiSettings.put("AutoJoin", this.isAutoJoin());
        wifiSettings.put("SSID_STR", this.getSsid());
        wifiSettings.put("HIDDEN_NETWORK", this.isHiddenNetwork());
        wifiSettings.put("DisableAssociationMACRandomization", this.isDisableMACAddressRandomization());
        wifiSettings.put("EncryptionType", mapEncryptionTypeForPayload());
        wifiSettings.put("CaptiveBypass", this.isCaptiveBypass());
        wifiSettings.put("EnableIPv6", this.isEnableIPv6());
        wifiSettings.put("IsHotspot", false);
        wifiSettings.put("PayloadDisplayName", "Wi-Fi");

        if (Objects.equals(this.encryptionType, "WEP") ||
                Objects.equals(this.encryptionType, "WPA/WPA2") ||
                Objects.equals(this.encryptionType, "WPA3") ||
                Objects.equals(this.encryptionType, "Any")) {

            if (this.getPassword() != null) {
                wifiSettings.put("Password", this.getPassword());
            }

        } else if (Objects.equals(this.encryptionType, "WEP_ENTERPRISE") ||
                Objects.equals(this.encryptionType, "WPA_WPA2_ENTERPRISE") ||
                Objects.equals(this.encryptionType, "WPA3_ENTERPRISE") ||
                Objects.equals(this.encryptionType, "Any_ENTERPRISE")) {

            NSDictionary eAPClientConfiguration = new NSDictionary();

            if (this.getAcceptedEAPTypes() != null) {
                eAPClientConfiguration.put("AcceptedEAPTypes", this.getAcceptedEAPTypes());
            }

            eAPClientConfiguration.put("EAPFASTUsePAC", this.isEAPFastUsePAC());
            eAPClientConfiguration.put("EAPFASTProvisionPAC", this.isEAPFastProvisionPAC());
            eAPClientConfiguration.put("EAPFASTProvisionPACAnonymously", this.isEAPFASTProvisionPACAnonymously());
            eAPClientConfiguration.put("TTLSInnerAuthentication", this.getTlsInnerAuthentication());
            if (this.getTlsMinimumVersion() != null) {
                eAPClientConfiguration.put("TLSMinimumVersion", this.getTlsMinimumVersion());
            }
            if (this.getTlsMaximumVersion() != null) {
                eAPClientConfiguration.put("TLSMaximumVersion", this.getTlsMaximumVersion());
            }
            eAPClientConfiguration.put("TLSCertificateIsRequired", this.isTlsCertificateRequired());
            if (this.getTlsTrustedServerNames() != null && !this.getTlsTrustedServerNames().isEmpty()) {
                eAPClientConfiguration.put("TLSTrustedServerNames", this.getTlsTrustedServerNames().toArray(new String[0]));
            }

            if (this.getUsername() != null) {
                eAPClientConfiguration.put("Username", this.getUsername());
            }
            eAPClientConfiguration.put("OneTimeUserPassword", this.isOneTimeUserPassword());
            if (this.getUserPassword() != null) {
                eAPClientConfiguration.put("UserPassword", this.getUserPassword());
            }

            if (this.getPayloadCertificateUUID() != null) {
                eAPClientConfiguration.put("PayloadCertificateUUID", this.getPayloadCertificateUUID());
            }

            if (this.getOuterIdentity() != null) {
                eAPClientConfiguration.put("OuterIdentity", this.getOuterIdentity());
            }

            wifiSettings.put("EAPClientConfiguration", eAPClientConfiguration);
        }

        // ProxyType is always included per Apple spec
        wifiSettings.put("ProxyType", this.getProxyType());
        if (Objects.equals(this.proxyType, "Manual")) {
            wifiSettings.put("ProxyServer", this.getProxyServer());
            wifiSettings.put("ProxyServerPort", this.getProxyPort());
            if (this.getProxyUsername() != null) {
                wifiSettings.put("ProxyUsername", this.getProxyUsername());
            }
            if (this.getProxyPassword() != null) {
                wifiSettings.put("ProxyPassword", this.getProxyPassword());
            }
        } else if (Objects.equals(this.proxyType, "Auto")) {
            if (this.getProxyPACURL() != null) {
                wifiSettings.put("ProxyPACURL", this.getProxyPACURL());
            }
            wifiSettings.put("ProxyPACFallbackAllowed", this.isProxyPACFallbackAllowed());
        }

        wifiSettings.put("PayloadIdentifier", this.getPayloadIdentifier());
        wifiSettings.put("PayloadType", this.getPayloadType());
        wifiSettings.put("PayloadUUID", this.getPayloadUUID());
        wifiSettings.put("PayloadVersion", this.getPayloadVersion());

        return wifiSettings;
    }
}

package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSString;
import lombok.*;

import java.util.Arrays;
import java.util.Map;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Vpn extends BasePayload {


    private String vpnType;
    private String vpnSubType;
    private String userDefinedName;

    private AlwaysOn vpnAlwaysOn;
    private IKEv2 vpnIkev2;
    private IpSec vpnSec;
    private IPv4 vpnIpv4;
    private PPP vpnPPP;
    private Proxies vpnProxies;
    private TransparentProxy vpnTransparentProxy;
    private VendorConfig vpnVendorConfig;
    private VpnConfig vpnVpn;


    // IKEv2 VPN
    public static Vpn createIKEv2VpnFromMap(Map<String, Object> params) {
        // ---- Normalize expected UI keys to internal names ----
        // Support both serverAddress and remoteAddress
        if (!params.containsKey("remoteAddress") && params.containsKey("serverAddress")) {
            params.put("remoteAddress", params.get("serverAddress"));
        }
        // authenticationMethod from machineAuthentication if provided (None / SharedSecret / Certificate)
        if (!params.containsKey("authenticationMethod") && params.containsKey("machineAuthentication")) {
            params.put("authenticationMethod", params.get("machineAuthentication"));
        }
        // userDefinedName from connectionName
        if (!params.containsKey("userDefinedName") && params.containsKey("connectionName")) {
            params.put("userDefinedName", params.get("connectionName"));
        }
        // NAT keepalive flags (booleans in UI)
        if (params.containsKey("enableNatKeepalive") && !params.containsKey("natKeepAliveOffloadEnable")) {
            Object v = params.get("enableNatKeepalive");
            params.put("natKeepAliveOffloadEnable", (v instanceof Boolean ? ((Boolean) v ? 1 : 0) : (v instanceof Number ? (((Number) v).intValue() != 0 ? 1 : 0) : 0)));
        }
        if (params.containsKey("natKeepaliveIntervalSeconds") && !params.containsKey("natKeepAliveInterval")) {
            Object v = params.get("natKeepaliveIntervalSeconds");
            params.put("natKeepAliveInterval", v instanceof Number ? ((Number) v).intValue() : 3600);
        }
        // Extended auth (EAP)
        if (params.containsKey("enableExtendedAuth") && !params.containsKey("extendedAuthEnabled")) {
            Object v = params.get("enableExtendedAuth");
            params.put("extendedAuthEnabled", (v instanceof Boolean ? ((Boolean) v ? 1 : 0) : (v instanceof Number ? (((Number) v).intValue() != 0 ? 1 : 0) : 0)));
        }
        // PFS / Revocation / MOBIKE / Redirect / Internal subnet attributes (booleans)
        String[] boolIntKeysSrc = new String[]{
                "enablePfs", "enableCertRevocationCheck", "useIpv4v6InternalSubnetAttributes", "disableMobike", "disableRedirect"
        };
        String[] boolIntKeysDst = new String[]{
                "enablePFS", "enableCertificateRevocationCheck", "useConfigurationAttributeInternalIPSubnet", "disableMOBIKE", "disableRedirect"
        };
        for (int i = 0; i < boolIntKeysSrc.length; i++) {
            if (params.containsKey(boolIntKeysSrc[i]) && !params.containsKey(boolIntKeysDst[i])) {
                Object v = params.get(boolIntKeysSrc[i]);
                params.put(boolIntKeysDst[i], (v instanceof Boolean ? ((Boolean) v ? 1 : 0) : (v instanceof Number ? (((Number) v).intValue() != 0 ? 1 : 0) : 0)));
            }
        }
        // Security Parameters mode mapping (UI: securityParamsMode = IKE_SA | CHILD_SA)
        if (params.containsKey("securityParamsMode") && !params.containsKey("securityParameters")) {
            String m = String.valueOf(params.get("securityParamsMode"));
            params.put("securityParameters", "CHILD_SA".equalsIgnoreCase(m) ? "Child SA" : "IKE SA");
        }

        // Proxy mapping from UI (proxyMode = NONE | MANUAL | PAC)
        if (!params.containsKey("proxyType") && params.containsKey("proxyMode")) {
            String mode = String.valueOf(params.get("proxyMode"));
            String proxyType = "None";
            if ("MANUAL".equalsIgnoreCase(mode)) proxyType = "Manual";
            if ("PAC".equalsIgnoreCase(mode)) proxyType = "Automatic";
            params.put("proxyType", proxyType);
        }
        if (!params.containsKey("proxyServerUrl") && params.containsKey("proxyAutoConfigUrl")) {
            params.put("proxyServerUrl", params.get("proxyAutoConfigUrl"));
        }

        // ---- Build IKEv2 from normalized map ----
        IKEv2 ikev2 = IKEv2.builder()
                .remoteAddress((String) params.getOrDefault("remoteAddress", null))
                .remoteIdentifier((String) params.getOrDefault("remoteIdentifier", null))
                .localIdentifier((String) params.getOrDefault("localIdentifier", null))
                .authenticationMethod((String) params.getOrDefault("authenticationMethod", "SharedSecret"))
                .extendedAuthEnabled((params.get("extendedAuthEnabled") instanceof Number) ? ((Number) params.get("extendedAuthEnabled")).intValue() : 0)
                .natKeepAliveOffloadEnable((params.get("natKeepAliveOffloadEnable") instanceof Number) ? ((Number) params.get("natKeepAliveOffloadEnable")).intValue() : 1)
                .natKeepAliveInterval((params.get("natKeepAliveInterval") instanceof Number) ? ((Number) params.get("natKeepAliveInterval")).intValue() : 3600)
                .deadPeerDetectionRate((String) params.getOrDefault("deadPeerDetectionRate", "Medium"))
                .enablePFS((params.get("enablePFS") instanceof Number) ? ((Number) params.get("enablePFS")).intValue() : 0)
                .enableCertificateRevocationCheck((params.get("enableCertificateRevocationCheck") instanceof Number) ? ((Number) params.get("enableCertificateRevocationCheck")).intValue() : 0)
                .useConfigurationAttributeInternalIPSubnet((params.get("useConfigurationAttributeInternalIPSubnet") instanceof Number) ? ((Number) params.get("useConfigurationAttributeInternalIPSubnet")).intValue() : 0)
                .disableMOBIKE((params.get("disableMOBIKE") instanceof Number) ? ((Number) params.get("disableMOBIKE")).intValue() : 0)
                .disableRedirect((params.get("disableRedirect") instanceof Number) ? ((Number) params.get("disableRedirect")).intValue() : 0)
                .sharedSecret((String) params.getOrDefault("sharedSecret", null))
                .certificateType((String) params.getOrDefault("certificateType", "RSA"))
                .payloadCertificateUUID((String) params.getOrDefault("payloadCertificateUUID", null))
                .serverCertificateIssuerCommonName((String) params.getOrDefault("serverCertificateIssuerCommonName", null))
                .serverCertificateCommonName((String) params.getOrDefault("serverCertificateCommonName", null))
                // New/optional fields pass-through if present
                .allowPostQuantumKeyExchangeFallback((Integer) params.getOrDefault("allowPostQuantumKeyExchangeFallback", null))
                .disconnectOnIdle((Integer) params.getOrDefault("disconnectOnIdle", null))
                .disconnectOnIdleTimer((Integer) params.getOrDefault("disconnectOnIdleTimer", null))
                .enforceRoutes((Integer) params.getOrDefault("enforceRoutes", null))
                .excludeAPNs((Integer) params.getOrDefault("excludeAPNs", null))
                .excludeCellularServices((Integer) params.getOrDefault("excludeCellularServices", null))
                .excludeDeviceCommunication((Integer) params.getOrDefault("excludeDeviceCommunication", null))
                .excludeLocalNetworks((Integer) params.getOrDefault("excludeLocalNetworks", null))
                .includeAllNetworks((Integer) params.getOrDefault("includeAllNetworks", null))
                .onDemandEnabled((Integer) params.getOrDefault("onDemandEnabled", null))
                .onDemandUserOverrideDisabled((Integer) params.getOrDefault("onDemandUserOverrideDisabled", null))
                .password((String) params.getOrDefault("password", null))
                .mtu((Integer) params.getOrDefault("mtu", null))
                .tlsMaximumVersion((String) params.getOrDefault("tlsMaximumVersion", null))
                .tlsMinimumVersion((String) params.getOrDefault("tlsMinimumVersion", null))
                .providerBundleIdentifier((String) params.getOrDefault("providerBundleIdentifier", null))
                .providerDesignatedRequirement((String) params.getOrDefault("providerDesignatedRequirement", null))
                .providerType((String) params.getOrDefault("providerType", "packet-tunnel"))
                .ppk((String) params.getOrDefault("ppk", null))
                .ppkIdentifier((String) params.getOrDefault("ppkIdentifier", null))
                .ppkMandatory((Integer) params.getOrDefault("ppkMandatory", null))
                .build();

        // Build SecurityAssociationParameters based on selected mode
        String secMode = String.valueOf(params.getOrDefault("securityParameters", "IKE SA"));
        IKEv2.SecurityAssociationParameters.SecurityAssociationParametersBuilder sa =
                IKEv2.SecurityAssociationParameters.builder()
                        .diffieHellmanGroup((params.get("diffieHellmanGroup") instanceof Number) ? ((Number) params.get("diffieHellmanGroup")).intValue() : 14)
                        .encryptionAlgorithm((String) params.getOrDefault("encryptionAlgorithm", "AES-256"))
                        .integrityAlgorithm((String) params.getOrDefault("integrityAlgorithm", "SHA2-256"))
                        .lifeTimeInMinutes((params.get("lifeTimeInMinutes") instanceof Number) ? ((Number) params.get("lifeTimeInMinutes")).intValue()
                                : (params.get("lifetimeMinutes") instanceof Number ? ((Number) params.get("lifetimeMinutes")).intValue() : 1440));

        // optional PQ methods: accept int[] or List<Number>
        Object pq = params.get("postQuantumKeyExchangeMethods");
        if (pq instanceof int[]) {
            sa.postQuantumKeyExchangeMethods((int[]) pq);
        } else if (pq instanceof java.util.List) {
            java.util.List<?> lst = (java.util.List<?>) pq;
            int[] arr = new int[lst.size()];
            for (int i = 0; i < lst.size(); i++) {
                Object n = lst.get(i);
                arr[i] = (n instanceof Number) ? ((Number) n).intValue() : 0;
            }
            sa.postQuantumKeyExchangeMethods(arr);
        }

        if ("Child SA".equalsIgnoreCase(secMode)) {
            ikev2.setChildSecurityAssociationParameters(sa.build());
        } else {
            ikev2.setIKESecurityAssociationParameters(sa.build());
        }

        Vpn.VpnBuilder vpnBuilder = Vpn.builder()
                .vpnType((String) params.getOrDefault("vpnType", "IKEv2"))
                .userDefinedName((String) params.getOrDefault("userDefinedName", "Default VPN"))
                .vpnIkev2(ikev2);

        Proxies proxies = createProxyFromMap(params);
        if (proxies != null) {
            vpnBuilder.vpnProxies(proxies);
        }

        return vpnBuilder.build();
    }

    private static NSDictionary convertSecurityAssociationParametersToNSDictionary(IKEv2.SecurityAssociationParameters secParams) {
        NSDictionary secDict = new NSDictionary();
        secDict.put("DiffieHellmanGroup", new NSNumber(secParams.getDiffieHellmanGroup()));
        secDict.put("EncryptionAlgorithm", new NSString(secParams.getEncryptionAlgorithm()));
        secDict.put("IntegrityAlgorithm", new NSString(secParams.getIntegrityAlgorithm()));
        secDict.put("LifeTimeInMinutes", new NSNumber(secParams.getLifeTimeInMinutes()));
        // Add PQ KEX methods if present
        if (secParams.getPostQuantumKeyExchangeMethods() != null && secParams.getPostQuantumKeyExchangeMethods().length > 0) {
            NSNumber[] pqArray = Arrays.stream(secParams.getPostQuantumKeyExchangeMethods()).mapToObj(NSNumber::new).toArray(NSNumber[]::new);
            secDict.put("PostQuantumKeyExchangeMethods", new NSArray(pqArray));
        }
        return secDict;
    }

    // Always-On VPN
    public static Vpn createAlwaysOnVpnFromMap(Map<String, Object> params) {
        IKEv2 ikev2 = IKEv2.builder()
                .remoteAddress((String) params.getOrDefault("remoteAddress", null))
                .remoteIdentifier((String) params.getOrDefault("remoteIdentifier", null))
                .localIdentifier((String) params.getOrDefault("localIdentifier", null))
                .authenticationMethod((String) params.getOrDefault("authenticationMethod", "None"))
                .extendedAuthEnabled((int) params.getOrDefault("extendedAuthEnabled", 0))
                .natKeepAliveOffloadEnable((int) params.getOrDefault("natKeepAliveOffloadEnable", 1))
                .natKeepAliveInterval((int) params.getOrDefault("natKeepAliveInterval", 3600))
                .deadPeerDetectionRate((String) params.getOrDefault("deadPeerDetectionRate", "Medium"))
                .enablePFS((int) params.getOrDefault("enablePFS", 0))
                .enableCertificateRevocationCheck((int) params.getOrDefault("enableCertificateRevocationCheck", 0))
                .useConfigurationAttributeInternalIPSubnet((int) params.getOrDefault("useConfigurationAttributeInternalIPSubnet", 0))
                .disableMOBIKE((int) params.getOrDefault("disableMOBIKE", 0))
                .disableRedirect((int) params.getOrDefault("disableRedirect", 0))
                .certificateType((String) params.getOrDefault("certificateType", "RSA"))
                .build();

        if ((params.getOrDefault("securityParameters", "IKE SA")).equals("IKE SA")) {
            ikev2.setIKESecurityAssociationParameters(IKEv2.SecurityAssociationParameters.builder()
                    .diffieHellmanGroup((int) params.getOrDefault("diffieHellmanGroup", 14))
                    .encryptionAlgorithm((String) params.getOrDefault("encryptionAlgorithm", "AES-256"))
                    .integrityAlgorithm((String) params.getOrDefault("integrityAlgorithm", "SHA2-256"))
                    .lifeTimeInMinutes((int) params.getOrDefault("lifeTimeInMinutes", 1440))
                    .build());
        } else {
            ikev2.setChildSecurityAssociationParameters(IKEv2.SecurityAssociationParameters.builder()
                    .diffieHellmanGroup((int) params.getOrDefault("diffieHellmanGroup", 14))
                    .encryptionAlgorithm((String) params.getOrDefault("encryptionAlgorithm", "AES-256"))
                    .integrityAlgorithm((String) params.getOrDefault("integrityAlgorithm", "SHA2-256"))
                    .lifeTimeInMinutes((int) params.getOrDefault("lifeTimeInMinutes", 1440))
                    .build());
        }

        AlwaysOn.AlwaysOnBuilder alwaysOnBuilder = AlwaysOn.builder()
                .uIToggleEnabled((int) params.getOrDefault("uIToggleEnabled", 0))
                .allowCaptiveWebSheet((int) params.getOrDefault("allowCaptiveWebSheet", 0))
                .allowAllCaptiveNetworkPlugins((int) params.getOrDefault("allowAllCaptiveNetworkPlugins", 0));

        // allowedCaptiveNetworkPlugins
        alwaysOnBuilder.allowedCaptiveNetworkPlugins(
                Arrays.stream((String[]) params.getOrDefault("bundleIdentifiers", new String[0]))
                        .map(b -> AlwaysOn.AllowedCaptiveNetworkPluginElement.builder().bundleIdentifier(b).build())
                        .toArray(AlwaysOn.AllowedCaptiveNetworkPluginElement[]::new)
        );

        // serviceExceptions
        Object seObj = params.get("serviceExceptions");
        AlwaysOn.ServiceExceptionElement[] serviceExceptionsArr;
        if (seObj instanceof java.util.List) {
            java.util.List<?> seList = (java.util.List<?>) seObj;
            serviceExceptionsArr = seList.stream().map(item -> {
                if (item instanceof Map) {
                    Map<?, String> m = (Map<?, String>) item;
                    return AlwaysOn.ServiceExceptionElement.builder()
                            .action(m.getOrDefault("Action", "Allow").toString())
                            .serviceName(m.getOrDefault("ServiceName", "").toString())
                            .build();
                }
                return null;
            }).filter(e -> e != null).toArray(AlwaysOn.ServiceExceptionElement[]::new);
            if (serviceExceptionsArr.length == 0) {
                serviceExceptionsArr = new AlwaysOn.ServiceExceptionElement[]{
                        AlwaysOn.ServiceExceptionElement.builder()
                                .action((String) params.getOrDefault("voiceMailAction", "Allow"))
                                .serviceName((String) params.getOrDefault("voiceMailServiceName", "VoiceMail"))
                                .build(),
                        AlwaysOn.ServiceExceptionElement.builder()
                                .action((String) params.getOrDefault("airPrintAction", "Allow"))
                                .serviceName((String) params.getOrDefault("airPrintServiceName", "AirPrint"))
                                .build(),
                        AlwaysOn.ServiceExceptionElement.builder()
                                .action((String) params.getOrDefault("cellularServicesAction", "Allow"))
                                .serviceName((String) params.getOrDefault("cellularServicesServiceName", "CellularServices"))
                                .build()
                };
            }
        } else {
            serviceExceptionsArr = new AlwaysOn.ServiceExceptionElement[]{
                    AlwaysOn.ServiceExceptionElement.builder()
                            .action((String) params.getOrDefault("voiceMailAction", "Allow"))
                            .serviceName((String) params.getOrDefault("voiceMailServiceName", "VoiceMail"))
                            .build(),
                    AlwaysOn.ServiceExceptionElement.builder()
                            .action((String) params.getOrDefault("airPrintAction", "Allow"))
                            .serviceName((String) params.getOrDefault("airPrintServiceName", "AirPrint"))
                            .build(),
                    AlwaysOn.ServiceExceptionElement.builder()
                            .action((String) params.getOrDefault("cellularServicesAction", "Allow"))
                            .serviceName((String) params.getOrDefault("cellularServicesServiceName", "CellularServices"))
                            .build()
            };
        }
        alwaysOnBuilder.serviceExceptions(serviceExceptionsArr);

        // tunnelConfigurations
        AlwaysOn.TunnelConfigurationElement[] tunnelConfigurations = new AlwaysOn.TunnelConfigurationElement[]{
                AlwaysOn.TunnelConfigurationElement.builder()
                        .interfaces(new String[2])
                        .protocolType("IKEv2")
                        .build()
        };
        if (params.containsKey("interfaceType")) {
            tunnelConfigurations[0].setInterfaces(new String[]{(String) params.get("interfaceType")});
        }
        alwaysOnBuilder.tunnelConfigurations(tunnelConfigurations);

        // applicationExceptions
        Object appExcObj = params.get("applicationExceptions");
        AlwaysOn.ApplicationExceptionElement[] applicationExceptionsArr = null;
        if (appExcObj instanceof java.util.List) {
            java.util.List<?> appExcList = (java.util.List<?>) appExcObj;
            applicationExceptionsArr = appExcList.stream().map(item -> {
                if (item instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) item;
                    String bundleIdentifier = m.get("bundleIdentifier") != null ? m.get("bundleIdentifier").toString() : null;
                    Object limitToProtocolsObj = m.get("limitToProtocols");
                    String[] limitToProtocolsArr = null;
                    if (limitToProtocolsObj instanceof String) {
                        limitToProtocolsArr = new String[]{(String) limitToProtocolsObj};
                    } else if (limitToProtocolsObj instanceof String[]) {
                        limitToProtocolsArr = (String[]) limitToProtocolsObj;
                    } else if (limitToProtocolsObj instanceof java.util.List) {
                        java.util.List<?> ltpList = (java.util.List<?>) limitToProtocolsObj;
                        limitToProtocolsArr = ltpList.stream().map(Object::toString).toArray(String[]::new);
                    }
                    return AlwaysOn.ApplicationExceptionElement.builder()
                            .bundleIdentifier(bundleIdentifier)
                            .limitToProtocols(limitToProtocolsArr)
                            .build();
                }
                return null;
            }).filter(e -> e != null && e.getBundleIdentifier() != null).toArray(AlwaysOn.ApplicationExceptionElement[]::new);
        }
        alwaysOnBuilder.applicationExceptions(applicationExceptionsArr);

        AlwaysOn alwaysOn = alwaysOnBuilder.build();


        Vpn.VpnBuilder vpnBuilder = Vpn.builder()
                .vpnType((String) params.getOrDefault("vpnType", "AlwaysOn"))
                .userDefinedName((String) params.getOrDefault("userDefinedName", "Always-On VPN"))
                .vpnAlwaysOn(alwaysOn)
                .vpnIkev2(ikev2);

        Proxies proxies = createProxyFromMap(params);
        if (proxies != null) {
            vpnBuilder.vpnProxies(proxies);
        }

        return vpnBuilder.build();
    }

    // L2TP VPN
    public static Vpn createL2TPVpnFromMap(Map<String, Object> params) {
        // Normalize incoming params for PPP/L2TP as per Apple spec
        int tokenCard = (params.get("tokenCard") instanceof Number) ? ((Number) params.get("tokenCard")).intValue() : 0;
        int ccpEnabled = (params.get("ccpEnabled") instanceof Number) ? ((Number) params.get("ccpEnabled")).intValue() : 0;
        int ccpMPPE40Enabled = (params.get("ccpMPPE40Enabled") instanceof Number) ? ((Number) params.get("ccpMPPE40Enabled")).intValue() : 0;
        int ccpMPPE128Enabled = (params.get("ccpMPPE128Enabled") instanceof Number) ? ((Number) params.get("ccpMPPE128Enabled")).intValue() : 0;
        int disconnectOnIdle = (params.get("disconnectOnIdle") instanceof Number) ? ((Number) params.get("disconnectOnIdle")).intValue() : 0;
        int disconnectOnIdleTimer = (params.get("disconnectOnIdleTimer") instanceof Number) ? ((Number) params.get("disconnectOnIdleTimer")).intValue() : 0;

        PPP ppp = PPP.builder()
                .authName((String) params.getOrDefault("authName", null))                  // AuthName
                .authPassword((String) params.getOrDefault("authPassword", null))          // AuthPassword
                .commRemoteAddress((String) params.getOrDefault("serverAddress", null))    // CommRemoteAddress
                .tokenCard(tokenCard)                                                      // TokenCard (0/1)
                .ccpEnabled(ccpEnabled)                                                    // CCPEnabled (0/1)
                .ccpMPPE40Enabled(ccpMPPE40Enabled)                                        // CCPMPPE40Enabled (0/1)
                .ccpMPPE128Enabled(ccpMPPE128Enabled)                                      // CCPMPPE128Enabled (0/1)
                .disconnectOnIdle(disconnectOnIdle)                                        // DisconnectOnIdle (0/1)
                .disconnectOnIdleTimer(disconnectOnIdleTimer)                              // DisconnectOnIdleTimer
                .build();

        // If TokenCard is used (RSA SecurID), Apple requires AuthEAPPlugins=["EAP-RSA"] and AuthProtocol=["EAP"]
        if (ppp.getTokenCard() == 1) {
            ppp.setAuthEAPPlugins(new String[]{"EAP-RSA"});
            ppp.setAuthProtocol(new String[]{"EAP"});
        } else {
            // Otherwise accept optional arrays from params if provided
            Object eapPlugins = params.get("authEAPPlugins");
            if (eapPlugins instanceof String[]) {
                ppp.setAuthEAPPlugins((String[]) eapPlugins);
            } else if (eapPlugins instanceof java.util.List) {
                java.util.List<?> lst = (java.util.List<?>) eapPlugins;
                ppp.setAuthEAPPlugins(lst.stream().map(Object::toString).toArray(String[]::new));
            }
            Object authProtocol = params.get("authProtocol");
            if (authProtocol instanceof String[]) {
                ppp.setAuthProtocol((String[]) authProtocol);
            } else if (authProtocol instanceof java.util.List) {
                java.util.List<?> lst = (java.util.List<?>) authProtocol;
                ppp.setAuthProtocol(lst.stream().map(Object::toString).toArray(String[]::new));
            }
        }

        IPv4 ipv4 = IPv4.builder()
                .overridePrimary((int) params.getOrDefault("sendAllTraffic", 0))  // 0: false, 1: true
                .build();

        IpSec ipSec = IpSec.builder()
                .sharedSecret(((String) params.getOrDefault("sharedSecret", "")).getBytes())
                .authenticationMethod("SharedSecret")
                .build();

        Vpn.VpnBuilder vpnBuilder = Vpn.builder()
                .vpnType((String) params.getOrDefault("vpnType", "L2TP"))
                .userDefinedName((String) params.getOrDefault("userDefinedName", "L2TP VPN"))
                .vpnPPP(ppp)
                .vpnIpv4(ipv4)
                .vpnSec(ipSec);

        Proxies proxies = createProxyFromMap(params);
        if (proxies != null) {
            vpnBuilder.vpnProxies(proxies);
        }

        return vpnBuilder.build();
    }

    private static Proxies createProxyFromMap(Map<String, Object> params) {
        String proxyType = (String) params.getOrDefault("proxyType", "None");

        // Normalize UI keys if present
        if ((proxyType == null || proxyType.isEmpty()) && params.containsKey("proxyMode")) {
            String mode = String.valueOf(params.get("proxyMode"));
            if ("MANUAL".equalsIgnoreCase(mode)) proxyType = "Manual";
            else if ("PAC".equalsIgnoreCase(mode)) proxyType = "Automatic";
            else proxyType = "None";
        }
        if (!params.containsKey("proxyServerUrl") && params.containsKey("proxyAutoConfigUrl")) {
            params.put("proxyServerUrl", params.get("proxyAutoConfigUrl"));
        }

        Proxies.ProxiesBuilder proxiesBuilder = Proxies.builder();

        switch (proxyType) {
            case "Manual":
                proxiesBuilder.httpEnable(1)
                        .httpProxy((String) params.getOrDefault("proxyServer", null))
                        .httpPort((int) params.getOrDefault("proxyPort", 0))
                        .httpProxyUsername((String) params.getOrDefault("proxyUsername", null))
                        .httpProxyPassword((String) params.getOrDefault("proxyPassword", null));
                break;
            case "Automatic":
                proxiesBuilder.proxyAutoConfigEnable(1)
                        .proxyAutoConfigURLString((String) params.getOrDefault("proxyServerUrl", null));
                break;
            case "None":
            default:
                proxiesBuilder.httpEnable(0)
                        .httpsEnable(0)
                        .proxyAutoConfigEnable(0);
                break;
        }

        return proxiesBuilder.build();
    }

    public NSDictionary convertIKEv2VpnToNSDictionary() {
        NSDictionary dict = new NSDictionary();
        NSDictionary ikeSettings = new NSDictionary();

        IKEv2 ike = this.getVpnIkev2();
        if (ike != null) {
            ikeSettings.put("RemoteAddress", new NSString(ike.getRemoteAddress() != null ? ike.getRemoteAddress() : ""));
            ikeSettings.put("RemoteIdentifier", new NSString(ike.getRemoteIdentifier() != null ? ike.getRemoteIdentifier() : ""));
            ikeSettings.put("LocalIdentifier", new NSString(ike.getLocalIdentifier() != null ? ike.getLocalIdentifier() : ""));
            ikeSettings.put("AuthenticationMethod", new NSString(ike.getAuthenticationMethod()));
            ikeSettings.put("ExtendedAuthEnabled", new NSNumber(ike.getExtendedAuthEnabled()));
            ikeSettings.put("NATKeepAliveOffloadEnable", new NSNumber(ike.getNatKeepAliveOffloadEnable()));
            ikeSettings.put("NATKeepAliveInterval", new NSNumber(ike.getNatKeepAliveInterval()));
            ikeSettings.put("DeadPeerDetectionRate", new NSString(ike.getDeadPeerDetectionRate()));
            ikeSettings.put("EnablePFS", new NSNumber(ike.getEnablePFS()));
            ikeSettings.put("EnableCertificateRevocationCheck", new NSNumber(ike.getEnableCertificateRevocationCheck()));
            ikeSettings.put("UseConfigurationAttributeInternalIPSubnet", new NSNumber(ike.getUseConfigurationAttributeInternalIPSubnet()));
            ikeSettings.put("DisableMOBIKE", new NSNumber(ike.getDisableMOBIKE()));
            ikeSettings.put("DisableRedirect", new NSNumber(ike.getDisableRedirect()));
            ikeSettings.put("SecurityParameters", new NSString(ike.getSecurityParameters()));
            ikeSettings.put("SharedSecret", new NSString(ike.getSharedSecret() != null ? ike.getSharedSecret() : ""));
            ikeSettings.put("CertificateType", new NSString(ike.getCertificateType()));
            ikeSettings.put("PayloadCertificateUUID", new NSString(ike.getPayloadCertificateUUID() != null ? ike.getPayloadCertificateUUID() : ""));
            ikeSettings.put("ServerCertificateIssuerCommonName", new NSString(ike.getServerCertificateIssuerCommonName() != null ? ike.getServerCertificateIssuerCommonName() : ""));
            ikeSettings.put("ServerCertificateCommonName", new NSString(ike.getServerCertificateCommonName() != null ? ike.getServerCertificateCommonName() : ""));

            // Optional Apple IKEv2 fields
            if (ike.getAllowPostQuantumKeyExchangeFallback() != null) {
                ikeSettings.put("AllowPostQuantumKeyExchangeFallback", new NSNumber(ike.getAllowPostQuantumKeyExchangeFallback()));
            }
            if (ike.getEnforceRoutes() != null) {
                ikeSettings.put("EnforceRoutes", new NSNumber(ike.getEnforceRoutes()));
            }
            if (ike.getExcludeAPNs() != null) {
                ikeSettings.put("ExcludeAPNs", new NSNumber(ike.getExcludeAPNs()));
            }
            if (ike.getExcludeCellularServices() != null) {
                ikeSettings.put("ExcludeCellularServices", new NSNumber(ike.getExcludeCellularServices()));
            }
            if (ike.getExcludeDeviceCommunication() != null) {
                ikeSettings.put("ExcludeDeviceCommunication", new NSNumber(ike.getExcludeDeviceCommunication()));
            }
            if (ike.getExcludeLocalNetworks() != null) {
                ikeSettings.put("ExcludeLocalNetworks", new NSNumber(ike.getExcludeLocalNetworks()));
            }
            if (ike.getIncludeAllNetworks() != null) {
                ikeSettings.put("IncludeAllNetworks", new NSNumber(ike.getIncludeAllNetworks()));
            }
            if (ike.getDisconnectOnIdle() != null) {
                ikeSettings.put("DisconnectOnIdle", new NSNumber(ike.getDisconnectOnIdle()));
            }
            if (ike.getDisconnectOnIdleTimer() != null) {
                ikeSettings.put("DisconnectOnIdleTimer", new NSNumber(ike.getDisconnectOnIdleTimer()));
            }
            if (ike.getOnDemandEnabled() != null) {
                ikeSettings.put("OnDemandEnabled", new NSNumber(ike.getOnDemandEnabled()));
            }
            if (ike.getOnDemandUserOverrideDisabled() != null) {
                ikeSettings.put("OnDemandUserOverrideDisabled", new NSNumber(ike.getOnDemandUserOverrideDisabled()));
            }
            if (ike.getPassword() != null) {
                ikeSettings.put("Password", new NSString(ike.getPassword()));
            }
            if (ike.getTlsMaximumVersion() != null) {
                ikeSettings.put("TLSMaximumVersion", new NSString(ike.getTlsMaximumVersion()));
            }
            if (ike.getTlsMinimumVersion() != null) {
                ikeSettings.put("TLSMinimumVersion", new NSString(ike.getTlsMinimumVersion()));
            }
            if (ike.getMtu() != null) {
                ikeSettings.put("MTU", new NSNumber(ike.getMtu()));
            }
            if (ike.getPpk() != null) {
                ikeSettings.put("PPK", new NSString(ike.getPpk()));
            }
            if (ike.getPpkIdentifier() != null) {
                ikeSettings.put("PPKIdentifier", new NSString(ike.getPpkIdentifier()));
            }
            if (ike.getPpkMandatory() != null) {
                ikeSettings.put("PPKMandatory", new NSNumber(ike.getPpkMandatory()));
            }
            if (ike.getProviderBundleIdentifier() != null) {
                ikeSettings.put("ProviderBundleIdentifier", new NSString(ike.getProviderBundleIdentifier()));
            }
            if (ike.getProviderDesignatedRequirement() != null) {
                ikeSettings.put("ProviderDesignatedRequirement", new NSString(ike.getProviderDesignatedRequirement()));
            }
            if (ike.getProviderType() != null) {
                ikeSettings.put("ProviderType", new NSString(ike.getProviderType()));
            }

            // SecurityAssociationParameters MUST live inside IKEv2 dictionary
            if (ike.getIKESecurityAssociationParameters() != null) {
                ikeSettings.put("IKESecurityAssociationParameters",
                        convertSecurityAssociationParametersToNSDictionary(ike.getIKESecurityAssociationParameters()));
            }
            if (ike.getChildSecurityAssociationParameters() != null) {
                ikeSettings.put("ChildSecurityAssociationParameters",
                        convertSecurityAssociationParametersToNSDictionary(ike.getChildSecurityAssociationParameters()));
            }
        }

        // Top-level fields
        dict.put("VPNType", new NSString(this.getVpnType() != null ? this.getVpnType() : ""));
        dict.put("UserDefinedName", new NSString(this.getUserDefinedName() != null ? this.getUserDefinedName() : ""));
        dict.put("IKEv2", ikeSettings);

        // Proxies (if present)
        Proxies p = this.getVpnProxies();
        if (p != null) {
            NSDictionary proxies = new NSDictionary();
            proxies.put("HTTPEnable", new NSNumber(p.getHttpEnable()));
            if (p.getHttpProxy() != null) proxies.put("HTTPProxy", new NSString(p.getHttpProxy()));
            if (p.getHttpPort() != 0) proxies.put("HTTPPort", new NSNumber(p.getHttpPort()));
            if (p.getHttpProxyUsername() != null)
                proxies.put("HTTPProxyUsername", new NSString(p.getHttpProxyUsername()));
            if (p.getHttpProxyPassword() != null)
                proxies.put("HTTPProxyPassword", new NSString(p.getHttpProxyPassword()));

            proxies.put("HTTPSEnable", new NSNumber(p.getHttpsEnable()));
            if (p.getHttpsProxy() != null) proxies.put("HTTPSProxy", new NSString(p.getHttpsProxy()));
            if (p.getHttpsPort() != 0) proxies.put("HTTPSPort", new NSNumber(p.getHttpsPort()));

            proxies.put("ProxyAutoConfigEnable", new NSNumber(p.getProxyAutoConfigEnable()));
            if (p.getProxyAutoConfigURLString() != null)
                proxies.put("ProxyAutoConfigURLString", new NSString(p.getProxyAutoConfigURLString()));
            proxies.put("ProxyAutoDiscoveryEnable", new NSNumber(p.getProxyAutoDiscoveryEnable()));

            if (p.getSupplementalMatchDomains() != null) {
                NSString[] arr = Arrays.stream(p.getSupplementalMatchDomains()).map(NSString::new).toArray(NSString[]::new);
                proxies.put("SupplementalMatchDomains", new NSArray(arr));
            }
            dict.put("Proxies", proxies);
        }

        return dict;
    }

    public NSDictionary convertAlwaysOnVpnToNSDictionary() {
        NSDictionary dict = new NSDictionary();
        NSDictionary alwaysOnSettings = new NSDictionary();

        AlwaysOn alwaysOn = this.getVpnAlwaysOn();
        if (alwaysOn != null) {
            alwaysOnSettings.put("UIToggleEnabled", new NSNumber(alwaysOn.getUIToggleEnabled()));
            alwaysOnSettings.put("AllowCaptiveWebSheet", new NSNumber(alwaysOn.getAllowCaptiveWebSheet()));
            alwaysOnSettings.put("AllowAllCaptiveNetworkPlugins", new NSNumber(alwaysOn.getAllowAllCaptiveNetworkPlugins()));

            // AllowedCaptiveNetworkPlugins
            if (alwaysOn.getAllowedCaptiveNetworkPlugins() != null) {
                NSDictionary[] allowedPlugins = new NSDictionary[alwaysOn.getAllowedCaptiveNetworkPlugins().length];
                for (int i = 0; i < alwaysOn.getAllowedCaptiveNetworkPlugins().length; i++) {
                    AlwaysOn.AllowedCaptiveNetworkPluginElement el = alwaysOn.getAllowedCaptiveNetworkPlugins()[i];
                    NSDictionary elDict = new NSDictionary();
                    elDict.put("BundleIdentifier", new NSString(el.getBundleIdentifier()));
                    allowedPlugins[i] = elDict;
                }
                alwaysOnSettings.put("AllowedCaptiveNetworkPlugins", new NSArray(allowedPlugins));
            }

            // ApplicationExceptions
            if (alwaysOn.getApplicationExceptions() != null) {
                NSDictionary[] appExcs = new NSDictionary[alwaysOn.getApplicationExceptions().length];
                for (int i = 0; i < alwaysOn.getApplicationExceptions().length; i++) {
                    AlwaysOn.ApplicationExceptionElement appExc = alwaysOn.getApplicationExceptions()[i];
                    NSDictionary appExcDict = new NSDictionary();
                    appExcDict.put("BundleIdentifier", new NSString(appExc.getBundleIdentifier()));
                    if (appExc.getLimitToProtocols() != null && appExc.getLimitToProtocols().length > 0) {
                        NSString[] protocols = Arrays.stream(appExc.getLimitToProtocols()).map(NSString::new).toArray(NSString[]::new);
                        appExcDict.put("LimitToProtocols", new NSArray(protocols));
                    }
                    appExcs[i] = appExcDict;
                }
                alwaysOnSettings.put("ApplicationExceptions", new NSArray(appExcs));
            }

            // ServiceExceptions
            if (alwaysOn.getServiceExceptions() != null) {
                NSDictionary[] serviceExceptions = new NSDictionary[alwaysOn.getServiceExceptions().length];
                for (int i = 0; i < alwaysOn.getServiceExceptions().length; i++) {
                    AlwaysOn.ServiceExceptionElement serviceException = alwaysOn.getServiceExceptions()[i];
                    NSDictionary serviceExceptionDict = new NSDictionary();
                    serviceExceptionDict.put("Action", new NSString(serviceException.getAction()));
                    serviceExceptionDict.put("ServiceName", new NSString(serviceException.getServiceName()));
                    // Do not output InterfaceType unless present in the class and needed by spec
                    serviceExceptions[i] = serviceExceptionDict;
                }
                alwaysOnSettings.put("ServiceExceptions", new NSArray(serviceExceptions));
            }

            // TunnelConfigurations
            if (alwaysOn.getTunnelConfigurations() != null) {
                NSDictionary[] tunnelConfigurations = new NSDictionary[alwaysOn.getTunnelConfigurations().length];
                for (int i = 0; i < alwaysOn.getTunnelConfigurations().length; i++) {
                    AlwaysOn.TunnelConfigurationElement tunnelConfigurationElement = alwaysOn.getTunnelConfigurations()[i];
                    NSDictionary tunnelConfigurationElementDict = new NSDictionary();
                    if (tunnelConfigurationElement.getInterfaces() != null) {
                        tunnelConfigurationElementDict.put("Interfaces", new NSArray(Arrays.stream(tunnelConfigurationElement.getInterfaces()).map(NSString::new).toArray(NSString[]::new)));
                    }
                    tunnelConfigurationElementDict.put("ProtocolType", new NSString(tunnelConfigurationElement.getProtocolType()));
                    tunnelConfigurations[i] = tunnelConfigurationElementDict;
                }
                alwaysOnSettings.put("TunnelConfigurations", new NSArray(tunnelConfigurations));
            }
        }

        NSDictionary ikeSettings = new NSDictionary();

        IKEv2 ike = this.getVpnIkev2();
        if (ike != null) {
            ikeSettings.put("RemoteAddress", new NSString(ike.getRemoteAddress() != null ? ike.getRemoteAddress() : ""));
            ikeSettings.put("RemoteIdentifier", new NSString(ike.getRemoteIdentifier() != null ? ike.getRemoteIdentifier() : ""));
            ikeSettings.put("LocalIdentifier", new NSString(ike.getLocalIdentifier() != null ? ike.getLocalIdentifier() : ""));
            ikeSettings.put("AuthenticationMethod", new NSString(ike.getAuthenticationMethod()));
            ikeSettings.put("ExtendedAuthEnabled", new NSNumber(ike.getExtendedAuthEnabled()));
            ikeSettings.put("NATKeepAliveOffloadEnable", new NSNumber(ike.getNatKeepAliveOffloadEnable()));
            ikeSettings.put("NATKeepAliveInterval", new NSNumber(ike.getNatKeepAliveInterval()));
            ikeSettings.put("DeadPeerDetectionRate", new NSString(ike.getDeadPeerDetectionRate()));
            ikeSettings.put("EnablePFS", new NSNumber(ike.getEnablePFS()));
            ikeSettings.put("EnableCertificateRevocationCheck", new NSNumber(ike.getEnableCertificateRevocationCheck()));
            ikeSettings.put("UseConfigurationAttributeInternalIPSubnet", new NSNumber(ike.getUseConfigurationAttributeInternalIPSubnet()));
            ikeSettings.put("DisableMOBIKE", new NSNumber(ike.getDisableMOBIKE()));
            ikeSettings.put("DisableRedirect", new NSNumber(ike.getDisableRedirect()));
            ikeSettings.put("SecurityParameters", new NSString(ike.getSecurityParameters()));
            ikeSettings.put("SharedSecret", new NSString(ike.getSharedSecret() != null ? ike.getSharedSecret() : ""));
            ikeSettings.put("CertificateType", new NSString(ike.getCertificateType()));
            ikeSettings.put("PayloadCertificateUUID", new NSString(ike.getPayloadCertificateUUID() != null ? ike.getPayloadCertificateUUID() : ""));
            ikeSettings.put("ServerCertificateIssuerCommonName", new NSString(ike.getServerCertificateIssuerCommonName() != null ? ike.getServerCertificateIssuerCommonName() : ""));
            ikeSettings.put("ServerCertificateCommonName", new NSString(ike.getServerCertificateCommonName() != null ? ike.getServerCertificateCommonName() : ""));

            // Optional Apple IKEv2 fields
            if (ike.getAllowPostQuantumKeyExchangeFallback() != null) {
                ikeSettings.put("AllowPostQuantumKeyExchangeFallback", new NSNumber(ike.getAllowPostQuantumKeyExchangeFallback()));
            }
            if (ike.getEnforceRoutes() != null) {
                ikeSettings.put("EnforceRoutes", new NSNumber(ike.getEnforceRoutes()));
            }
            if (ike.getExcludeAPNs() != null) {
                ikeSettings.put("ExcludeAPNs", new NSNumber(ike.getExcludeAPNs()));
            }
            if (ike.getExcludeCellularServices() != null) {
                ikeSettings.put("ExcludeCellularServices", new NSNumber(ike.getExcludeCellularServices()));
            }
            if (ike.getExcludeDeviceCommunication() != null) {
                ikeSettings.put("ExcludeDeviceCommunication", new NSNumber(ike.getExcludeDeviceCommunication()));
            }
            if (ike.getExcludeLocalNetworks() != null) {
                ikeSettings.put("ExcludeLocalNetworks", new NSNumber(ike.getExcludeLocalNetworks()));
            }
            if (ike.getIncludeAllNetworks() != null) {
                ikeSettings.put("IncludeAllNetworks", new NSNumber(ike.getIncludeAllNetworks()));
            }
            if (ike.getDisconnectOnIdle() != null) {
                ikeSettings.put("DisconnectOnIdle", new NSNumber(ike.getDisconnectOnIdle()));
            }
            if (ike.getDisconnectOnIdleTimer() != null) {
                ikeSettings.put("DisconnectOnIdleTimer", new NSNumber(ike.getDisconnectOnIdleTimer()));
            }
            if (ike.getOnDemandEnabled() != null) {
                ikeSettings.put("OnDemandEnabled", new NSNumber(ike.getOnDemandEnabled()));
            }
            if (ike.getOnDemandUserOverrideDisabled() != null) {
                ikeSettings.put("OnDemandUserOverrideDisabled", new NSNumber(ike.getOnDemandUserOverrideDisabled()));
            }
            if (ike.getPassword() != null) {
                ikeSettings.put("Password", new NSString(ike.getPassword()));
            }
            if (ike.getTlsMaximumVersion() != null) {
                ikeSettings.put("TLSMaximumVersion", new NSString(ike.getTlsMaximumVersion()));
            }
            if (ike.getTlsMinimumVersion() != null) {
                ikeSettings.put("TLSMinimumVersion", new NSString(ike.getTlsMinimumVersion()));
            }
            if (ike.getMtu() != null) {
                ikeSettings.put("MTU", new NSNumber(ike.getMtu()));
            }
            if (ike.getPpk() != null) {
                ikeSettings.put("PPK", new NSString(ike.getPpk()));
            }
            if (ike.getPpkIdentifier() != null) {
                ikeSettings.put("PPKIdentifier", new NSString(ike.getPpkIdentifier()));
            }
            if (ike.getPpkMandatory() != null) {
                ikeSettings.put("PPKMandatory", new NSNumber(ike.getPpkMandatory()));
            }
            if (ike.getProviderBundleIdentifier() != null) {
                ikeSettings.put("ProviderBundleIdentifier", new NSString(ike.getProviderBundleIdentifier()));
            }
            if (ike.getProviderDesignatedRequirement() != null) {
                ikeSettings.put("ProviderDesignatedRequirement", new NSString(ike.getProviderDesignatedRequirement()));
            }
            if (ike.getProviderType() != null) {
                ikeSettings.put("ProviderType", new NSString(ike.getProviderType()));
            }

            // SecurityAssociationParameters MUST live inside IKEv2 dictionary
            if (ike.getIKESecurityAssociationParameters() != null) {
                ikeSettings.put("IKESecurityAssociationParameters",
                        convertSecurityAssociationParametersToNSDictionary(ike.getIKESecurityAssociationParameters()));
            }
            if (ike.getChildSecurityAssociationParameters() != null) {
                ikeSettings.put("ChildSecurityAssociationParameters",
                        convertSecurityAssociationParametersToNSDictionary(ike.getChildSecurityAssociationParameters()));
            }
        }

        dict.put("VPNType", new NSString(this.getVpnType() != null ? this.getVpnType() : ""));
        dict.put("UserDefinedName", new NSString(this.getUserDefinedName() != null ? this.getUserDefinedName() : ""));
        dict.put("AlwaysOn", alwaysOnSettings);
        dict.put("IKEv2", ikeSettings);

        return dict;
    }

    public NSDictionary convertL2TPVpnToNSDictionary() {
        NSDictionary dict = new NSDictionary();
        NSDictionary pppSettings = new NSDictionary();
        NSDictionary ipv4Settings = new NSDictionary();
        NSDictionary ipSecSettings = new NSDictionary();

        PPP ppp = this.getVpnPPP();
        if (ppp != null) {
            if (ppp.getAuthEAPPlugins() != null) {
                pppSettings.put("AuthEAPPlugins",
                        new NSArray(Arrays.stream(ppp.getAuthEAPPlugins()).map(NSString::new).toArray(NSString[]::new)));
            }
            if (ppp.getAuthName() != null) {
                pppSettings.put("AuthName", new NSString(ppp.getAuthName()));
            }
            if (ppp.getAuthPassword() != null) {
                pppSettings.put("AuthPassword", new NSString(ppp.getAuthPassword()));
            }
            if (ppp.getAuthProtocol() != null) {
                pppSettings.put("AuthProtocol",
                        new NSArray(Arrays.stream(ppp.getAuthProtocol()).map(NSString::new).toArray(NSString[]::new)));
            }
            pppSettings.put("CCPEnabled", new NSNumber(ppp.getCcpEnabled()));
            pppSettings.put("CCPMPPE128Enabled", new NSNumber(ppp.getCcpMPPE128Enabled()));
            pppSettings.put("CCPMPPE40Enabled", new NSNumber(ppp.getCcpMPPE40Enabled()));
            if (ppp.getCommRemoteAddress() != null) {
                pppSettings.put("CommRemoteAddress", new NSString(ppp.getCommRemoteAddress()));
            }
            pppSettings.put("DisconnectOnIdle", new NSNumber(ppp.getDisconnectOnIdle()));
            if (ppp.getDisconnectOnIdleTimer() > 0) {
                pppSettings.put("DisconnectOnIdleTimer", new NSNumber(ppp.getDisconnectOnIdleTimer()));
            }
            pppSettings.put("TokenCard", new NSNumber(ppp.getTokenCard()));
        }

        IPv4 ipv4 = this.getVpnIpv4();
        if (ipv4 != null) {
            ipv4Settings.put("OverridePrimary", new NSNumber(ipv4.getOverridePrimary()));
        }

        IpSec ipSec = this.getVpnSec();
        if (ipSec != null) {
            ipSecSettings.put("SharedSecret", new NSString(new String(ipSec.getSharedSecret())));
            ipSecSettings.put("AuthenticationMethod", new NSString(ipSec.getAuthenticationMethod()));
        }

        dict.put("VPNType", new NSString(this.getVpnType() != null ? this.getVpnType() : ""));

        dict.put("UserDefinedName", new NSString(this.getUserDefinedName() != null ? this.getUserDefinedName() : ""));

        dict.put("PPP", pppSettings);

        if (ipv4Settings.allKeys().length > 0) {
            dict.put("IPv4", ipv4Settings);
        }
        if (ipSecSettings.allKeys().length > 0) {
            dict.put("IPSec", ipSecSettings);
        }

        return dict;
    }


}

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
class AlwaysOn {

    @Builder.Default
    private int uIToggleEnabled = 0;

    @Builder.Default
    private int allowCaptiveWebSheet = 0;

    @Builder.Default
    private int allowAllCaptiveNetworkPlugins = 0;

    private ServiceExceptionElement[] serviceExceptions;

    private AllowedCaptiveNetworkPluginElement[] allowedCaptiveNetworkPlugins;

    private TunnelConfigurationElement[] tunnelConfigurations;

    private ApplicationExceptionElement[] applicationExceptions;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    static
    class ServiceExceptionElement {

        /*
        Possible Values: Allow, Drop
         */
        private String action;

        /*
        Possible Values: VoiceMail, AirPrint, CellularServices, DeviceCommunication
         */
        private String serviceName;

        // Apple spec omits InterfaceType, but keep for compatibility if needed
        //private int interfaceType = 0;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    static class AllowedCaptiveNetworkPluginElement {
        private String bundleIdentifier;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    static class TunnelConfigurationElement {

        /*
       Possible Values: Cellular, WiFi
         */
        private String[] interfaces;

        /*
        Value: IKEv2
         */
        @Builder.Default
        private String protocolType = "IKEv2";
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    static class ApplicationExceptionElement {
        private String bundleIdentifier; // required
        private String[] limitToProtocols; // optional; values like "UDP"
    }
}


@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
class VpnDns {

    private String dnsProtocol;

    private String domainName;

    private String payloadCertificateUUID;

    private String[] searchDomains;

    private String[] serverAddresses;

    private String serverName;

    private String serverURL;

    private String[] supplementalMatchDomains;

    @Builder.Default
    private int supplementalMatchDomainsNoSearch = 0;
}


@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
// ... existing code ...
class IKEv2 {

    private String remoteAddress;
    private String remoteIdentifier;
    private String localIdentifier;
    private String authenticationMethod = "None";
    @Builder.Default
    private int extendedAuthEnabled = 0;
    @Builder.Default
    private int natKeepAliveOffloadEnable = 1;
    @Builder.Default
    private int natKeepAliveInterval = 20;
    @Builder.Default
    private String deadPeerDetectionRate = "Medium";
    @Builder.Default
    private int enablePFS = 0;
    @Builder.Default
    private int enableCertificateRevocationCheck = 0;
    @Builder.Default
    private int useConfigurationAttributeInternalIPSubnet = 0;
    @Builder.Default
    private int disableMOBIKE = 0;
    @Builder.Default
    private int disableRedirect = 0;
    @Builder.Default
    private String securityParameters = "IKE SA";
    private SecurityAssociationParameters iKESecurityAssociationParameters;
    private SecurityAssociationParameters childSecurityAssociationParameters;

    // Shared Secret
    private String sharedSecret;

    // Certificate
    @Builder.Default
    private String certificateType = "RSA";
    private String payloadCertificateUUID;
    private String serverCertificateIssuerCommonName;
    private String serverCertificateCommonName;

    // New IKEv2 fields from Apple spec
    // Top-level configuration
    private Integer allowPostQuantumKeyExchangeFallback; // 0/1, default 0
    private Integer disconnectOnIdle;                     // 0/1
    private Integer disconnectOnIdleTimer;                // seconds
    private Integer enforceRoutes;                        // 0/1
    private Integer excludeAPNs;                          // 0/1, default 1
    private Integer excludeCellularServices;              // 0/1, default 1
    private Integer excludeDeviceCommunication;           // 0/1, default 1
    private Integer excludeLocalNetworks;                 // 0/1, platform default varies
    private Integer includeAllNetworks;                   // 0/1
    private Integer onDemandEnabled;                      // 0/1
    private Integer onDemandUserOverrideDisabled;         // 0/1
    private String password;                              // if AuthenticationMethod = Password
    private Integer mtu;                                  // 1280..1400, default 1280
    private String tlsMaximumVersion;                     // 1.0, 1.1, 1.2
    private String tlsMinimumVersion;                     // 1.0, 1.1, 1.2
    private String providerBundleIdentifier;
    private String providerDesignatedRequirement;
    @Builder.Default
    private String providerType = "packet-tunnel";        // packet-tunnel | app-proxy
    // Post-quantum PPK (RFC 8784)
    private String ppk;                                   // data (store as base64/string)
    private String ppkIdentifier;
    private Integer ppkMandatory;                         // 0/1, default 1

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    static
    class SecurityAssociationParameters {
        @Builder.Default
        private int diffieHellmanGroup = 14;

        @Builder.Default
        private String encryptionAlgorithm = "AES-256";

        @Builder.Default
        private String integrityAlgorithm = "SHA2-256";

        @Builder.Default
        private int lifeTimeInMinutes = 1440;

        // New: PostQuantumKeyExchangeMethods (ADDKE1..7 indexes)
        private int[] postQuantumKeyExchangeMethods; // allowed values: 0, 36, 37
    }
}
// ... existing code ...


@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
class IpSec {

    @Builder.Default
    private String authenticationMethod = "SharedSecret";

    @Builder.Default
    private int disconnectOnIdle = 0;

    private int disconnectOnIdleTimer;

    private String localIdentifier;

    private String localIdentifierType;

    @Builder.Default
    private int onDemandEnabled = 0;

    private VpnOnDemandRulesElement[] onDemandRules;

    private String payloadCertificateUUID;

    @Builder.Default
    private boolean promptForVPNPIN = false;

    private String remoteAddress;

    private byte[] sharedSecret;

    @Builder.Default
    private int xAuthEnabled = 0;
    private String xAuthName;
    private String xAuthPassword;

    private String xAuthPasswordEncryption;
}


@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
class IPv4 {

    private int overridePrimary;
}

//L2TP PPTP VPN
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PPP {

    private String[] authEAPPlugins;

    private String authName;

    private String authPassword;

    private String[] authProtocol;

    private int ccpEnabled;

    private int ccpMPPE128Enabled;

    private int ccpMPPE40Enabled;

    private String commRemoteAddress;

    @Builder.Default
    private int disconnectOnIdle = 0;

    private int disconnectOnIdleTimer;

    @Builder.Default
    private int tokenCard = 0;

    private String userAuthentication;
}

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
class Proxies {

    @Builder.Default
    private int httpEnable = 0;
    private int httpPort;
    private String httpProxy;
    private String httpProxyPassword;
    private String httpProxyUsername;

    @Builder.Default
    private int httpsEnable = 0;
    private int httpsPort;
    private String httpsProxy;

    private int proxyAutoConfigEnable;
    private String proxyAutoConfigURLString;

    @Builder.Default
    private int proxyAutoDiscoveryEnable = 1;

    private String[] supplementalMatchDomains;
}


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TransparentProxy {

    @Builder.Default
    private String authenticationMethod = "Password";

    @Builder.Default
    private int disconnectOnIdle = 0;

    private int disconnectOnIdleTimer;

    @Builder.Default
    private int enforceRoutes = 0;

    @Builder.Default
    private int onDemandEnabled = 0;

    private VpnOnDemandRulesElement[] onDemandRules;

    private int order;

    private String password;

    private String payloadCertificateUUID;

    private String providerBundleIdentifier;

    private String providerDesignatedRequirement;

    @Builder.Default
    private String providerType = "packet-tunnel";
}

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
class VendorConfig {
    private String group;

    private String loginGroupOrDomain;

    private String realm;

    private String role;
}

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
class VpnConfig {

    private String authName;

    private String authPassword;

    @Builder.Default
    private String authenticationMethod = "Password";

    @Builder.Default
    private int disconnectOnIdle = 0;

    private int disconnectOnIdleTimer;

    @Builder.Default
    private int enforceRoutes = 0;

    @Builder.Default
    private int excludeAPNs = 1;

    @Builder.Default
    private int excludeCellularServices = 1;

    @Builder.Default
    private int excludeDeviceCommunication = 1;

    private int excludeLocalNetworks;

    @Builder.Default
    private int includeAllNetworks = 0;

    @Builder.Default
    private int onDemandEnabled = 0;

    private VpnOnDemandRulesElement[] onDemandRules;

    @Builder.Default
    private int onDemandUserOverrideDisabled = 0;

    private String payloadCertificateUUID;

    private String providerBundleIdentifier;

    private String providerDesignatedRequirement;

    @Builder.Default
    private String providerType = "packet-tunnel";

    private String remoteAddress;
}

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
class VpnOnDemandRulesElement {

    private String action;

    private VpnOnDemandRulesElementActionParameter[] actionParameters;

    private String[] dnsDomainMatch;

    private String[] dnsServerAddressMatch;

    private String interfaceTypeMatch;
    private String[] ssidMatch;

    private String urlStringProbe;
}

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
class VpnOnDemandRulesElementActionParameter {

    private String domainAction;

    private String[] domains;

    private String[] requiredDNSServers;

    private String requiredURLStringProbe;
}

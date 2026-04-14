package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSString;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalHttpProxy extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.proxy.http.global";

    private String payloadDisplayName;

    // Proxy type: Manual or Automatic
    private String proxyType;

    // Manual fields
    private String server;
    private Integer port;
    private String username;
    private String password;

    // Automatic fields
    private String proxyPACURL;
    private Boolean allowDirectConnection;

    // Shared field
    private Boolean allowBypassingProxy;

    @SuppressWarnings("unchecked")
    public static GlobalHttpProxy createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        Object displayName = map.getOrDefault("payloadDisplayName", "Global HTTP Proxy");
        String proxyType = (String) map.getOrDefault("proxyType", "Manual");

        GlobalHttpProxyBuilder builder = GlobalHttpProxy.builder()
                .payloadDisplayName(displayName != null ? displayName.toString() : "Global HTTP Proxy")
                .proxyType(proxyType)
                .allowBypassingProxy(map.get("allowBypassingProxy") instanceof Boolean b ? b : false);

        if ("Manual".equals(proxyType)) {
            builder.server((String) map.get("server"))
                    .username((String) map.get("username"))
                    .password((String) map.get("password"));
            if (map.get("port") instanceof Number n) {
                builder.port(n.intValue());
            }
        } else if ("Automatic".equals(proxyType)) {
            builder.proxyPACURL((String) map.get("proxyPACURL"));
            builder.allowDirectConnection(map.get("allowDirectConnection") instanceof Boolean b ? b : false);
        }

        GlobalHttpProxy proxy = builder.build();
        proxy.setPayloadIdentifier("com.arcyintel.arcops.globalhttpproxy." + policyId);
        proxy.setPayloadType(PAYLOAD_TYPE);
        proxy.setPayloadUUID(UUID.randomUUID().toString());
        proxy.setPayloadVersion(1);

        return proxy;
    }

    public NSDictionary createPayload() {
        NSDictionary payload = new NSDictionary();

        // Standard payload fields from BasePayload
        payload.put("PayloadType", new NSString(getPayloadType()));
        payload.put("PayloadVersion", new NSNumber(getPayloadVersion()));
        payload.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        payload.put("PayloadUUID", new NSString(getPayloadUUID()));
        if (payloadDisplayName != null) {
            payload.put("PayloadDisplayName", new NSString(payloadDisplayName));
        }

        // ProxyType
        if ("Manual".equals(proxyType)) {
            payload.put("ProxyType", new NSString("Manual"));

            if (server != null && !server.isBlank()) {
                payload.put("ProxyServer", new NSString(server));
            }
            if (port != null) {
                payload.put("ProxyServerPort", new NSNumber(port));
            }
            if (username != null && !username.isBlank()) {
                payload.put("ProxyUsername", new NSString(username));
            }
            if (password != null && !password.isBlank()) {
                payload.put("ProxyPassword", new NSString(password));
            }
        } else if ("Automatic".equals(proxyType)) {
            payload.put("ProxyType", new NSString("Auto"));

            if (proxyPACURL != null && !proxyPACURL.isBlank()) {
                payload.put("ProxyPACURL", new NSString(proxyPACURL));
            }
            if (allowDirectConnection != null) {
                payload.put("ProxyPACFallbackAllowed", new NSNumber(allowDirectConnection));
            }
        }

        if (allowBypassingProxy != null) {
            payload.put("ProxyCaptiveLoginAllowed", new NSNumber(allowBypassingProxy));
        }

        return payload;
    }
}

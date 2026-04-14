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
public class DnsProxy extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.dnsProxy.managed";

    private String payloadDisplayName;

    // DNS Proxy-specific fields
    private String appBundleIdentifier;       // AppBundleIdentifier (required)
    private String dnsProxyUUID;              // DNSProxyUUID (optional)
    private String providerBundleIdentifier;  // ProviderBundleIdentifier (optional)
    private Map<String, Object> providerConfiguration; // ProviderConfiguration (dict)

    public static DnsProxy createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        Object displayName = map.getOrDefault("payloadDisplayName", "DNS Proxy");

        DnsProxy dnsProxy = DnsProxy.builder()
                .payloadDisplayName(displayName != null ? displayName.toString() : "DNS Proxy")
                .appBundleIdentifier((String) map.get("appBundleIdentifier"))
                .dnsProxyUUID((String) map.get("dnsProxyUUID"))
                .providerBundleIdentifier((String) map.get("providerBundleIdentifier"))
                .providerConfiguration((Map<String, Object>) map.get("providerConfiguration"))
                .build();

        dnsProxy.setPayloadIdentifier("com.arcyintel.arcops.dnsproxy." + policyId);
        dnsProxy.setPayloadType(PAYLOAD_TYPE);
        dnsProxy.setPayloadUUID(UUID.randomUUID().toString());
        dnsProxy.setPayloadVersion(1);

        return dnsProxy;
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

        // DNS Proxy specific fields
        if (appBundleIdentifier != null && !appBundleIdentifier.isBlank()) {
            payload.put("AppBundleIdentifier", new NSString(appBundleIdentifier));
        }

        if (dnsProxyUUID != null && !dnsProxyUUID.isBlank()) {
            payload.put("DNSProxyUUID", new NSString(dnsProxyUUID));
        }

        if (providerBundleIdentifier != null && !providerBundleIdentifier.isBlank()) {
            payload.put("ProviderBundleIdentifier", new NSString(providerBundleIdentifier));
        }

        if (providerConfiguration != null && !providerConfiguration.isEmpty()) {
            payload.put("ProviderConfiguration", mapToNSDictionary(providerConfiguration));
        }

        return payload;
    }

    @SuppressWarnings("unchecked")
    private NSDictionary mapToNSDictionary(Map<String, Object> map) {
        NSDictionary dict = new NSDictionary();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            if (val == null) {
                continue;
            }

            if (val instanceof String s) {
                dict.put(key, new NSString(s));
            } else if (val instanceof Number n) {
                dict.put(key, new NSNumber(n.doubleValue()));
            } else if (val instanceof Boolean b) {
                dict.put(key, new NSNumber(b));
            } else if (val instanceof Map<?, ?> subMap) {
                dict.put(key, mapToNSDictionary((Map<String, Object>) subMap));
            } else {
                dict.put(key, new NSString(val.toString()));
            }
        }
        return dict;
    }
}
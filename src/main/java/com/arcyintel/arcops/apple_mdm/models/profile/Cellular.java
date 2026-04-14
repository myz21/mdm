package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Apple Cellular payload (com.apple.cellular)
 * Mirrors the style of Wifi.java
 */
@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Cellular extends BasePayload {


    /**
     * Top-level fields
     */
    private AttachAPN attachAPN;               // "AttachAPN" : { Name: ... }
    private List<APNItem> apns;                // "APNs" : [ { ... }, ... ]

    /**
     * Build from a generic Map (typically deserialized JSON from UI)
     */
    @SuppressWarnings("unchecked")
    public static Cellular createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) return null;

        Cellular cellular = new Cellular();

        // AttachAPN
        if (map.containsKey("attachAPN")) {
            Map<String, Object> attach = (Map<String, Object>) map.get("attachAPN");
            if (attach != null) {
                cellular.setAttachAPN(AttachAPN.fromMap(attach));
            }
        }

        // APNs array
        if (map.containsKey("apns")) {
            Object raw = map.get("apns");
            if (raw instanceof List) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) raw;
                List<APNItem> items = new ArrayList<>();
                for (Map<String, Object> item : list) {
                    if (item != null) {
                        APNItem apn = APNItem.fromMap(item);
                        // "Name" is required by spec; skip if missing/blank
                        if (apn != null && apn.getName() != null && !apn.getName().isBlank()) {
                            items.add(apn);
                        }
                    }
                }
                cellular.setApns(items);
            }
        }

        // Payload envelope
        cellular.setPayloadIdentifier(String.format("policy_cellular-%s", policyId));
        cellular.setPayloadType("com.apple.cellular");
        cellular.setPayloadUUID(UUID.randomUUID().toString());
        cellular.setPayloadVersion(1);

        return cellular;
    }

    private static String asString(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    // ---------- Inner types ----------

    private static Integer asInteger(Object o, Integer def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    private static Boolean asBoolean(Object o, Boolean def) {
        if (o == null) return def;
        if (o instanceof Boolean) return (Boolean) o;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    // ---------- Small helpers (null-safe casts) ----------

    /**
     * Convert to plist dictionary (to be embedded under PayloadContent)
     */
    public NSDictionary createPayload() {
        NSDictionary dict = new NSDictionary();

        // AttachAPN
        if (this.attachAPN != null && this.attachAPN.getName() != null && !this.attachAPN.getName().isBlank()) {
            dict.put("AttachAPN", this.attachAPN.toPlist());
        }

        // APNs array
        if (this.apns != null && !this.apns.isEmpty()) {
            NSArray arr = new NSArray(this.apns.size());
            for (int i = 0; i < this.apns.size(); i++) {
                arr.setValue(i, this.apns.get(i).toPlist());
            }
            dict.put("APNs", arr);
        }

        // Standard payload envelope fields
        dict.put("PayloadIdentifier", this.getPayloadIdentifier());
        dict.put("PayloadType", this.getPayloadType());
        dict.put("PayloadUUID", this.getPayloadUUID());
        dict.put("PayloadVersion", this.getPayloadVersion());

        return dict;
    }

    @Setter
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AttachAPN {
        /**
         * Required: Name
         */
        private String name;                // Name
        private String username;            // Username
        private String password;            // Password
        /**
         * Default: PAP, Allowed: CHAP, PAP
         */
        private String authenticationType;  // AuthenticationType
        /**
         * AllowedProtocolMask: 1 IPv4, 2 IPv6, 3 Both
         */
        private Integer allowedProtocolMask;

        @SuppressWarnings("unchecked")
        public static AttachAPN fromMap(Map<String, Object> map) {
            if (map == null) return null;
            return AttachAPN.builder()
                    .name(asString(map.get("name"), null))
                    .username(asString(map.get("username"), null))
                    .password(asString(map.get("password"), null))
                    .authenticationType(asString(map.get("authenticationType"), "PAP"))
                    .allowedProtocolMask(asInteger(map.get("allowedProtocolMask"), null))
                    .build();
        }

        public NSDictionary toPlist() {
            NSDictionary d = new NSDictionary();
            // Required
            if (this.name != null && !this.name.isBlank()) {
                d.put("Name", this.name);
            }
            // Optional
            if (this.username != null) d.put("Username", this.username);
            if (this.password != null) d.put("Password", this.password);
            if (this.authenticationType != null) d.put("AuthenticationType", this.authenticationType);
            if (this.allowedProtocolMask != null) d.put("AllowedProtocolMask", this.allowedProtocolMask);
            return d;
        }
    }

    /**
     * Cellular.APNsItem
     * Mirrors Apple’s documented keys. Optional fields are nullable.
     */
    @Setter
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class APNItem {
        // Required
        private String name;                           // Name

        // Optional auth
        private String username;                       // Username
        private String password;                       // Password
        private String authenticationType;             // Default: PAP; Allowed: CHAP, PAP

        // Proxy
        private String proxyServer;                    // ProxyServer
        private Integer proxyPort;                     // ProxyPort

        // Protocol masks
        private Integer allowedProtocolMask;                   // AllowedProtocolMask (1 IPv4, 2 IPv6, 3 Both)
        private Integer allowedProtocolMaskInRoaming;          // AllowedProtocolMaskInRoaming
        private Integer allowedProtocolMaskInDomesticRoaming;  // AllowedProtocolMaskInDomesticRoaming
        private Integer defaultProtocolMask;                   // Deprecated (iOS 11+)

        // Flags
        private Boolean enableXLAT464;                 // EnableXLAT464 (iOS 16+/watchOS 9+)

        @SuppressWarnings("unchecked")
        public static APNItem fromMap(Map<String, Object> map) {
            if (map == null) return null;

            return APNItem.builder()
                    .name(asString(map.get("name"), null))
                    .username(asString(map.get("username"), null))
                    .password(asString(map.get("password"), null))
                    .authenticationType(asString(map.get("authenticationType"), "PAP"))
                    .proxyServer(asString(map.get("proxyServer"), null))
                    .proxyPort(asInteger(map.get("proxyPort"), null))
                    .allowedProtocolMask(asInteger(map.get("allowedProtocolMask"), null))
                    .allowedProtocolMaskInRoaming(asInteger(map.get("allowedProtocolMaskInRoaming"), null))
                    .allowedProtocolMaskInDomesticRoaming(asInteger(map.get("allowedProtocolMaskInDomesticRoaming"), null))
                    .defaultProtocolMask(asInteger(map.get("defaultProtocolMask"), null))
                    .enableXLAT464(asBoolean(map.get("enableXLAT464"), false))
                    .build();
        }

        public NSDictionary toPlist() {
            NSDictionary d = new NSDictionary();
            // Required
            if (this.name != null) d.put("Name", this.name);

            // Optional
            if (this.username != null) d.put("Username", this.username);
            if (this.password != null) d.put("Password", this.password);
            if (this.authenticationType != null) d.put("AuthenticationType", this.authenticationType);

            if (this.proxyServer != null) d.put("ProxyServer", this.proxyServer);
            if (this.proxyPort != null) d.put("ProxyPort", this.proxyPort);

            if (this.allowedProtocolMask != null) d.put("AllowedProtocolMask", this.allowedProtocolMask);
            if (this.allowedProtocolMaskInRoaming != null)
                d.put("AllowedProtocolMaskInRoaming", this.allowedProtocolMaskInRoaming);
            if (this.allowedProtocolMaskInDomesticRoaming != null)
                d.put("AllowedProtocolMaskInDomesticRoaming", this.allowedProtocolMaskInDomesticRoaming);
            if (this.defaultProtocolMask != null)
                d.put("DefaultProtocolMask", this.defaultProtocolMask); // deprecated but kept for completeness

            if (this.enableXLAT464 != null) d.put("EnableXLAT464", this.enableXLAT464);

            return d;
        }
    }
}
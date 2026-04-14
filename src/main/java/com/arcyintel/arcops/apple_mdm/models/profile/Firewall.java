package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSString;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Firewall extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.security.firewall";

    private boolean enableFirewall;
    private boolean blockAllIncoming;
    private boolean enableStealthMode;
    private boolean enableLogging;
    private String loggingOption;
    private List<Map<String, Object>> applications;

    @SuppressWarnings("unchecked")
    public static Firewall createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        Firewall firewall = Firewall.builder()
                .enableFirewall(toBool(map.get("enableFirewall"), false))
                .blockAllIncoming(toBool(map.get("blockAllIncoming"), false))
                .enableStealthMode(toBool(map.get("enableStealthMode"), false))
                .enableLogging(toBool(map.get("enableLogging"), false))
                .loggingOption((String) map.get("loggingOption"))
                .applications((List<Map<String, Object>>) map.get("applications"))
                .build();

        firewall.setPayloadIdentifier("com.arcyintel.arcops.firewall." + policyId);
        firewall.setPayloadType(PAYLOAD_TYPE);
        firewall.setPayloadUUID(UUID.randomUUID().toString());
        firewall.setPayloadVersion(1);
        firewall.setPayloadRemovalDisallowed(true);

        return firewall;
    }

    public NSDictionary createPayload() {
        NSDictionary payload = new NSDictionary();

        // Standard payload fields
        payload.put("PayloadType", new NSString(getPayloadType()));
        payload.put("PayloadVersion", new NSNumber(getPayloadVersion()));
        payload.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        payload.put("PayloadUUID", new NSString(getPayloadUUID()));
        payload.put("PayloadRemovalDisallowed", new NSNumber(getPayloadRemovalDisallowed()));

        // Firewall specific fields
        payload.put("EnableFirewall", new NSNumber(enableFirewall));
        payload.put("BlockAllIncoming", new NSNumber(blockAllIncoming));
        payload.put("EnableStealthMode", new NSNumber(enableStealthMode));
        payload.put("EnableLogging", new NSNumber(enableLogging));

        if (loggingOption != null && !loggingOption.isBlank()) {
            payload.put("LoggingOption", new NSString(loggingOption));
        }

        if (applications != null && !applications.isEmpty()) {
            payload.put("Applications", applicationsToNSArray(applications));
        }

        return payload;
    }

    private NSArray applicationsToNSArray(List<Map<String, Object>> apps) {
        NSArray array = new NSArray(apps.size());
        for (int i = 0; i < apps.size(); i++) {
            Map<String, Object> app = apps.get(i);
            NSDictionary appDict = new NSDictionary();

            if (app.get("bundleID") != null) {
                appDict.put("BundleID", new NSString(app.get("bundleID").toString()));
            }
            if (app.get("allowed") != null) {
                appDict.put("Allowed", new NSNumber((Boolean) app.get("allowed")));
            }

            array.setValue(i, appDict);
        }
        return array;
    }
}

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
public class TccPolicy extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.TCC.configuration-profile-policy";

    // service name → list of entries (each entry has identifier, identifierType, codeRequirement, authorization)
    private Map<String, List<Map<String, Object>>> services;

    @SuppressWarnings("unchecked")
    public static TccPolicy createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        TccPolicy tccPolicy = TccPolicy.builder()
                .services((Map<String, List<Map<String, Object>>>) map.get("services"))
                .build();

        tccPolicy.setPayloadIdentifier("com.arcyintel.arcops.tcc." + policyId);
        tccPolicy.setPayloadType(PAYLOAD_TYPE);
        tccPolicy.setPayloadUUID(UUID.randomUUID().toString());
        tccPolicy.setPayloadVersion(1);
        tccPolicy.setPayloadRemovalDisallowed(true);

        return tccPolicy;
    }

    public NSDictionary createPayload() {
        NSDictionary payload = new NSDictionary();

        // Standard payload fields
        payload.put("PayloadType", new NSString(getPayloadType()));
        payload.put("PayloadVersion", new NSNumber(getPayloadVersion()));
        payload.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        payload.put("PayloadUUID", new NSString(getPayloadUUID()));
        payload.put("PayloadRemovalDisallowed", new NSNumber(getPayloadRemovalDisallowed()));

        // TCC specific fields
        if (services != null && !services.isEmpty()) {
            payload.put("Services", servicesToNSDictionary(services));
        }

        return payload;
    }

    private NSDictionary servicesToNSDictionary(Map<String, List<Map<String, Object>>> servicesMap) {
        NSDictionary servicesDict = new NSDictionary();
        for (Map.Entry<String, List<Map<String, Object>>> entry : servicesMap.entrySet()) {
            List<Map<String, Object>> entries = entry.getValue();
            NSArray entriesArray = new NSArray(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                entriesArray.setValue(i, entryToNSDictionary(entries.get(i)));
            }
            servicesDict.put(entry.getKey(), entriesArray);
        }
        return servicesDict;
    }

    private NSDictionary entryToNSDictionary(Map<String, Object> entryMap) {
        NSDictionary dict = new NSDictionary();
        for (Map.Entry<String, Object> e : entryMap.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            if (val == null) {
                continue;
            }
            if (val instanceof String s) {
                dict.put(key, new NSString(s));
            } else if (val instanceof Number n) {
                dict.put(key, new NSNumber(n.doubleValue()));
            } else if (val instanceof Boolean b) {
                dict.put(key, new NSNumber(b));
            } else {
                dict.put(key, new NSString(val.toString()));
            }
        }
        return dict;
    }
}

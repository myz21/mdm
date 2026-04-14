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
public class SystemExtensionPolicy extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.system-extension-policy";

    private boolean allowUserOverrides;
    private Map<String, List<String>> allowedSystemExtensions;       // teamID → bundleIDs
    private Map<String, List<String>> allowedSystemExtensionTypes;   // teamID → types
    private Map<String, List<String>> removableSystemExtensions;     // teamID → bundleIDs

    @SuppressWarnings("unchecked")
    public static SystemExtensionPolicy createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        SystemExtensionPolicy policy = SystemExtensionPolicy.builder()
                .allowUserOverrides(toBool(map.get("allowUserOverrides"), false))
                .allowedSystemExtensions((Map<String, List<String>>) map.get("allowedSystemExtensions"))
                .allowedSystemExtensionTypes((Map<String, List<String>>) map.get("allowedSystemExtensionTypes"))
                .removableSystemExtensions((Map<String, List<String>>) map.get("removableSystemExtensions"))
                .build();

        policy.setPayloadIdentifier("com.arcyintel.arcops.systemextensions." + policyId);
        policy.setPayloadType(PAYLOAD_TYPE);
        policy.setPayloadUUID(UUID.randomUUID().toString());
        policy.setPayloadVersion(1);
        policy.setPayloadRemovalDisallowed(true);

        return policy;
    }

    public NSDictionary createPayload() {
        NSDictionary payload = new NSDictionary();

        // Standard payload fields
        payload.put("PayloadType", new NSString(getPayloadType()));
        payload.put("PayloadVersion", new NSNumber(getPayloadVersion()));
        payload.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        payload.put("PayloadUUID", new NSString(getPayloadUUID()));
        payload.put("PayloadRemovalDisallowed", new NSNumber(getPayloadRemovalDisallowed()));

        // SystemExtensionPolicy specific fields
        payload.put("AllowUserOverrides", new NSNumber(allowUserOverrides));

        if (allowedSystemExtensions != null && !allowedSystemExtensions.isEmpty()) {
            payload.put("AllowedSystemExtensions", teamMapToNSDictionary(allowedSystemExtensions));
        }

        if (allowedSystemExtensionTypes != null && !allowedSystemExtensionTypes.isEmpty()) {
            payload.put("AllowedSystemExtensionTypes", teamMapToNSDictionary(allowedSystemExtensionTypes));
        }

        if (removableSystemExtensions != null && !removableSystemExtensions.isEmpty()) {
            payload.put("RemovableSystemExtensions", teamMapToNSDictionary(removableSystemExtensions));
        }

        return payload;
    }

    private NSDictionary teamMapToNSDictionary(Map<String, List<String>> teamMap) {
        NSDictionary dict = new NSDictionary();
        for (Map.Entry<String, List<String>> entry : teamMap.entrySet()) {
            List<String> values = entry.getValue();
            NSArray array = new NSArray(values.size());
            for (int i = 0; i < values.size(); i++) {
                array.setValue(i, new NSString(values.get(i)));
            }
            dict.put(entry.getKey(), array);
        }
        return dict;
    }
}

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
public class KernelExtensionPolicy extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.syspolicy.kernel-extension-policy";

    private boolean allowUserOverrides;
    private List<String> allowedTeamIdentifiers;
    private Map<String, List<String>> allowedKernelExtensions;  // teamID → bundleIDs

    @SuppressWarnings("unchecked")
    public static KernelExtensionPolicy createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        KernelExtensionPolicy policy = KernelExtensionPolicy.builder()
                .allowUserOverrides(toBool(map.get("allowUserOverrides"), false))
                .allowedTeamIdentifiers((List<String>) map.get("allowedTeamIdentifiers"))
                .allowedKernelExtensions((Map<String, List<String>>) map.get("allowedKernelExtensions"))
                .build();

        policy.setPayloadIdentifier("com.arcyintel.arcops.kernelextensions." + policyId);
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

        // KernelExtensionPolicy specific fields
        payload.put("AllowUserOverrides", new NSNumber(allowUserOverrides));

        if (allowedTeamIdentifiers != null && !allowedTeamIdentifiers.isEmpty()) {
            NSArray teamArray = new NSArray(allowedTeamIdentifiers.size());
            for (int i = 0; i < allowedTeamIdentifiers.size(); i++) {
                teamArray.setValue(i, new NSString(allowedTeamIdentifiers.get(i)));
            }
            payload.put("AllowedTeamIdentifiers", teamArray);
        }

        if (allowedKernelExtensions != null && !allowedKernelExtensions.isEmpty()) {
            payload.put("AllowedKernelExtensions", teamMapToNSDictionary(allowedKernelExtensions));
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

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
public class SystemPreferencesPolicy extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.systempreferences";

    private List<String> enabledPreferencePanes;
    private List<String> disabledPreferencePanes;
    private List<String> hiddenPreferencePanes;

    @SuppressWarnings("unchecked")
    public static SystemPreferencesPolicy createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        SystemPreferencesPolicy policy = SystemPreferencesPolicy.builder()
                .enabledPreferencePanes((List<String>) map.get("enabledPreferencePanes"))
                .disabledPreferencePanes((List<String>) map.get("disabledPreferencePanes"))
                .hiddenPreferencePanes((List<String>) map.get("hiddenPreferencePanes"))
                .build();

        policy.setPayloadIdentifier("com.arcyintel.arcops.systempreferences." + policyId);
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

        // SystemPreferences specific fields
        if (enabledPreferencePanes != null && !enabledPreferencePanes.isEmpty()) {
            payload.put("EnabledPreferencePanes", stringListToNSArray(enabledPreferencePanes));
        }

        if (disabledPreferencePanes != null && !disabledPreferencePanes.isEmpty()) {
            payload.put("DisabledPreferencePanes", stringListToNSArray(disabledPreferencePanes));
        }

        if (hiddenPreferencePanes != null && !hiddenPreferencePanes.isEmpty()) {
            payload.put("HiddenPreferencePanes", stringListToNSArray(hiddenPreferencePanes));
        }

        return payload;
    }

    private NSArray stringListToNSArray(List<String> list) {
        NSArray array = new NSArray(list.size());
        for (int i = 0; i < list.size(); i++) {
            array.setValue(i, new NSString(list.get(i)));
        }
        return array;
    }
}

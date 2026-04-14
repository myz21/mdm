package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSDictionary;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LockScreenMessage extends BasePayload {
    private String assetTagInformation;
    private String lockScreenFootnote;

    public static LockScreenMessage createFromMap(Map<String, Object> map, UUID policyId) {
        LockScreenMessage payload = LockScreenMessage.builder()
                .assetTagInformation((String) map.get("AssetTagInformation"))
                .lockScreenFootnote((String) map.get("LockScreenFootnote"))
                .build();
        payload.setPayloadIdentifier("com.apple.lockscreenmessage." + policyId);
        payload.setPayloadType("com.apple.shareddeviceconfiguration");
        payload.setPayloadUUID(UUID.randomUUID().toString());
        payload.setPayloadVersion(1);
        return payload;
    }

    public NSDictionary createPayload() {
        NSDictionary d = new NSDictionary();
        if (assetTagInformation != null) d.put("AssetTagInformation", assetTagInformation);
        if (lockScreenFootnote != null) d.put("LockScreenFootnote", lockScreenFootnote);
        d.put("PayloadIdentifier", getPayloadIdentifier());
        d.put("PayloadType", getPayloadType());
        d.put("PayloadUUID", getPayloadUUID());
        d.put("PayloadVersion", getPayloadVersion());
        return d;
    }
}
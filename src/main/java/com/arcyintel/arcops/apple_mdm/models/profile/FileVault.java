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
public class FileVault extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.MCX.FileVault2";

    private boolean enable;
    private boolean deferForceEnabled;
    @Builder.Default
    private int deferMaxBypassAttempts = -1;
    private boolean showRecoveryKey;
    private boolean useRecoveryKey;
    private boolean useKeychain;

    @SuppressWarnings("unchecked")
    public static FileVault createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        FileVault fileVault = FileVault.builder()
                .enable(toBool(map.get("enable"), false))
                .deferForceEnabled(toBool(map.get("deferForceEnabled"), false))
                .deferMaxBypassAttempts(toInt(map.get("deferMaxBypassAttempts"), -1))
                .showRecoveryKey(toBool(map.get("showRecoveryKey"), false))
                .useRecoveryKey(toBool(map.get("useRecoveryKey"), false))
                .useKeychain(toBool(map.get("useKeychain"), false))
                .build();

        fileVault.setPayloadIdentifier("com.arcyintel.arcops.filevault." + policyId);
        fileVault.setPayloadType(PAYLOAD_TYPE);
        fileVault.setPayloadUUID(UUID.randomUUID().toString());
        fileVault.setPayloadVersion(1);
        fileVault.setPayloadRemovalDisallowed(true);

        return fileVault;
    }

    public NSDictionary createPayload() {
        NSDictionary payload = new NSDictionary();

        // Standard payload fields
        payload.put("PayloadType", new NSString(getPayloadType()));
        payload.put("PayloadVersion", new NSNumber(getPayloadVersion()));
        payload.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        payload.put("PayloadUUID", new NSString(getPayloadUUID()));
        payload.put("PayloadRemovalDisallowed", new NSNumber(getPayloadRemovalDisallowed()));

        // FileVault specific fields
        payload.put("Enable", new NSNumber(enable));
        payload.put("Defer", new NSNumber(deferForceEnabled));
        payload.put("DeferForceAtUserLoginMaxBypassAttempts", new NSNumber(deferMaxBypassAttempts));
        payload.put("ShowRecoveryKey", new NSNumber(showRecoveryKey));
        payload.put("UseRecoveryKey", new NSNumber(useRecoveryKey));
        payload.put("UseKeychain", new NSNumber(useKeychain));

        return payload;
    }
}

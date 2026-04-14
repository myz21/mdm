package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSDictionary;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileRemovalPassword extends BasePayload {


    private String removalPassword;

    public static ProfileRemovalPassword createFromMap(Map<String, Object> profileRemovalPasswordMap) {
        if (profileRemovalPasswordMap == null) {
            return null;
        }
        ProfileRemovalPassword profileRemovalPassword = ProfileRemovalPassword.builder()
                .removalPassword((String) profileRemovalPasswordMap.getOrDefault("removalPassword", ""))
                .build();

        profileRemovalPassword.setPayloadIdentifier(String.format("policy_remove_password-%s", profileRemovalPasswordMap.get("policyId")));
        profileRemovalPassword.setPayloadType("com.apple.profileRemovalPassword");
        profileRemovalPassword.setPayloadUUID(UUID.randomUUID().toString());
        profileRemovalPassword.setPayloadVersion(1);
        profileRemovalPassword.setPayloadRemovalDisallowed(true);
        return profileRemovalPassword;
    }

    public NSDictionary createPayload() {
        NSDictionary dictionary = new NSDictionary();
        dictionary.put("RemovalPassword", removalPassword);

        dictionary.put("PayloadIdentifier", this.getPayloadIdentifier());
        dictionary.put("PayloadType", this.getPayloadType());
        dictionary.put("PayloadUUID", this.getPayloadUUID());
        dictionary.put("PayloadVersion", this.getPayloadVersion());
        dictionary.put("PayloadRemovalDisallowed", this.getPayloadRemovalDisallowed());

        return dictionary;
    }
}

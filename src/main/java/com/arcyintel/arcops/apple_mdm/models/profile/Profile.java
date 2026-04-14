package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import lombok.*;

import java.util.UUID;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Profile {

    public static final String PROFILE_IDENTIFIER = "com.arcops";
    public static final String PROFILE_TYPE = "Configuration";

    // Getters and Setters
    private NSArray payloadContent = new NSArray();
    private String payloadDisplayName;
    private String payloadIdentifier;
    private String payloadOrganization;
    private Boolean payloadRemovalDisallowed;
    private String payloadType;
    private String payloadUUID;
    private Integer payloadVersion;

    public static Profile createProfile(NSDictionary... nsDictionaries) {
        return Profile.builder()
                .payloadContent(new NSArray(nsDictionaries))
                .payloadDisplayName("arcops-configuration-profile")
                .payloadIdentifier(PROFILE_IDENTIFIER)
                .payloadRemovalDisallowed(true)
                .payloadType("Configuration")
                .payloadUUID(UUID.randomUUID().toString())
                .payloadVersion(1)
                .build();
    }

    public NSDictionary createPayload() {
        NSDictionary payload = new NSDictionary();
        payload.put("PayloadContent", payloadContent);
        payload.put("PayloadDisplayName", payloadDisplayName);
        payload.put("PayloadIdentifier", payloadIdentifier);
        payload.put("PayloadOrganization", payloadOrganization);
        payload.put("PayloadRemovalDisallowed", payloadRemovalDisallowed);
        payload.put("PayloadType", payloadType);
        payload.put("PayloadUUID", payloadUUID);
        payload.put("PayloadVersion", payloadVersion);
        return payload;
    }

}

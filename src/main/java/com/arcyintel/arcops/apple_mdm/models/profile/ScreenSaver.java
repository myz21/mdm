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
public class ScreenSaver extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.screensaver";

    private int idleTime;
    private boolean askForPassword;
    private int askForPasswordDelay;
    private String modulePath;
    private int loginWindowIdleTime;

    @SuppressWarnings("unchecked")
    public static ScreenSaver createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        ScreenSaver screenSaver = ScreenSaver.builder()
                .idleTime(toInt(map.get("idleTime"), 0))
                .askForPassword(toBool(map.get("askForPassword"), false))
                .askForPasswordDelay(toInt(map.get("askForPasswordDelay"), 0))
                .modulePath((String) map.get("modulePath"))
                .loginWindowIdleTime(toInt(map.get("loginWindowIdleTime"), 0))
                .build();

        screenSaver.setPayloadIdentifier("com.arcyintel.arcops.screensaver." + policyId);
        screenSaver.setPayloadType(PAYLOAD_TYPE);
        screenSaver.setPayloadUUID(UUID.randomUUID().toString());
        screenSaver.setPayloadVersion(1);
        screenSaver.setPayloadRemovalDisallowed(true);

        return screenSaver;
    }

    public NSDictionary createPayload() {
        NSDictionary payload = new NSDictionary();

        // Standard payload fields
        payload.put("PayloadType", new NSString(getPayloadType()));
        payload.put("PayloadVersion", new NSNumber(getPayloadVersion()));
        payload.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        payload.put("PayloadUUID", new NSString(getPayloadUUID()));
        payload.put("PayloadRemovalDisallowed", new NSNumber(getPayloadRemovalDisallowed()));

        // ScreenSaver specific fields
        payload.put("idleTime", new NSNumber(idleTime));
        payload.put("askForPassword", new NSNumber(askForPassword));
        payload.put("askForPasswordDelay", new NSNumber(askForPasswordDelay));

        if (modulePath != null && !modulePath.isBlank()) {
            payload.put("modulePath", new NSString(modulePath));
        }

        payload.put("loginWindowIdleTime", new NSNumber(loginWindowIdleTime));

        return payload;
    }
}

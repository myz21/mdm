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
public class LoginWindow extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.loginwindow";

    private String loginWindowText;
    private boolean showFullName;
    private String adminHostInfo;
    private boolean disableConsoleAccess;
    private boolean showInputMenu;
    private boolean showPasswordHint;
    private boolean shutDownDisabled;
    private boolean restartDisabled;
    private boolean sleepDisabled;

    @SuppressWarnings("unchecked")
    public static LoginWindow createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        LoginWindow loginWindow = LoginWindow.builder()
                .loginWindowText((String) map.get("loginWindowText"))
                .showFullName(toBool(map.get("showFullName"), false))
                .adminHostInfo((String) map.get("adminHostInfo"))
                .disableConsoleAccess(toBool(map.get("disableConsoleAccess"), false))
                .showInputMenu(toBool(map.get("showInputMenu"), false))
                .showPasswordHint(toBool(map.get("showPasswordHint"), false))
                .shutDownDisabled(toBool(map.get("shutDownDisabled"), false))
                .restartDisabled(toBool(map.get("restartDisabled"), false))
                .sleepDisabled(toBool(map.get("sleepDisabled"), false))
                .build();

        loginWindow.setPayloadIdentifier("com.arcyintel.arcops.loginwindow." + policyId);
        loginWindow.setPayloadType(PAYLOAD_TYPE);
        loginWindow.setPayloadUUID(UUID.randomUUID().toString());
        loginWindow.setPayloadVersion(1);
        loginWindow.setPayloadRemovalDisallowed(true);

        return loginWindow;
    }

    public NSDictionary createPayload() {
        NSDictionary payload = new NSDictionary();

        // Standard payload fields
        payload.put("PayloadType", new NSString(getPayloadType()));
        payload.put("PayloadVersion", new NSNumber(getPayloadVersion()));
        payload.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        payload.put("PayloadUUID", new NSString(getPayloadUUID()));
        payload.put("PayloadRemovalDisallowed", new NSNumber(getPayloadRemovalDisallowed()));

        // LoginWindow specific fields
        if (loginWindowText != null && !loginWindowText.isBlank()) {
            payload.put("LoginwindowText", new NSString(loginWindowText));
        }

        payload.put("SHOWFULLNAME", new NSNumber(showFullName));

        if (adminHostInfo != null && !adminHostInfo.isBlank()) {
            payload.put("AdminHostInfo", new NSString(adminHostInfo));
        }

        payload.put("DisableConsoleAccess", new NSNumber(disableConsoleAccess));
        payload.put("showInputMenu", new NSNumber(showInputMenu));
        payload.put("RetriesUntilHint", new NSNumber(showPasswordHint ? 1 : 0));
        payload.put("ShutDownDisabled", new NSNumber(shutDownDisabled));
        payload.put("RestartDisabled", new NSNumber(restartDisabled));
        payload.put("SleepDisabled", new NSNumber(sleepDisabled));

        return payload;
    }
}

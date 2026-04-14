package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BasePayload {


    private int alertType = 1;
    private boolean badgesEnabled = true;
    private String bundleIdentifier;
    private boolean criticalAlertEnabled = false;
    private int groupingType = 0;
    private boolean notificationsEnabled = true;
    private int previewType = 0;
    private boolean showInCarPlay = true;
    private boolean showInLockScreen = true;
    private boolean showInNotificationCenter = true;
    private boolean soundsEnabled = true;

    public static Notification createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }
        Notification notification = Notification.builder()
                .alertType(toInt(map.get("alertType"), 1))
                .badgesEnabled(toBool(map.get("badgesEnabled"), true))
                .bundleIdentifier((String) map.get("bundleIdentifier"))
                .criticalAlertEnabled(toBool(map.get("criticalAlertEnabled"), false))
                .groupingType(toInt(map.get("groupingType"), 0))
                .notificationsEnabled(toBool(map.get("notificationsEnabled"), true))
                .previewType(toInt(map.get("previewType"), 0))
                .showInCarPlay(toBool(map.get("showInCarPlay"), false))
                .showInLockScreen(toBool(map.get("showInLockScreen"), true))
                .showInNotificationCenter(toBool(map.get("showInNotificationCenter"), true))
                .soundsEnabled(toBool(map.get("soundsEnabled"), true))
                .build();

        notification.setPayloadIdentifier(String.format("policy_notification-%s", policyId));
        notification.setPayloadType("com.apple.notificationsettings");
        notification.setPayloadUUID(UUID.randomUUID().toString());
        notification.setPayloadVersion(1);

        return notification;
    }

    /**
     * Creates a single com.apple.notificationsettings payload containing ALL notification items.
     * Apple expects one payload with a NotificationSettings array containing all app configs.
     */
    public static NSDictionary createCombinedPayload(List<Notification> notifications, UUID policyId) {
        NSDictionary payload = new NSDictionary();

        NSArray notificationSettingsArray = new NSArray(notifications.size());
        for (int i = 0; i < notifications.size(); i++) {
            notificationSettingsArray.setValue(i, notifications.get(i).toSettingsDict());
        }

        payload.put("NotificationSettings", notificationSettingsArray);
        payload.put("PayloadDisplayName", "Notification Settings");
        payload.put("PayloadIdentifier", String.format("policy_notification-%s", policyId));
        payload.put("PayloadType", "com.apple.notificationsettings");
        payload.put("PayloadUUID", UUID.randomUUID().toString());
        payload.put("PayloadVersion", 1);

        return payload;
    }

    /**
     * Converts this notification to an NSDictionary item for the NotificationSettings array.
     */
    private NSDictionary toSettingsDict() {
        NSDictionary dict = new NSDictionary();

        dict.put("AlertType", this.getAlertType());
        dict.put("BundleIdentifier", this.getBundleIdentifier());
        dict.put("NotificationsEnabled", this.isNotificationsEnabled());
        dict.put("SoundsEnabled", this.isSoundsEnabled());
        dict.put("BadgesEnabled", this.isBadgesEnabled());
        dict.put("ShowInNotificationCenter", this.isShowInNotificationCenter());
        dict.put("ShowInCarPlay", this.isShowInCarPlay());
        dict.put("CriticalAlertEnabled", this.isCriticalAlertEnabled());
        dict.put("GroupingType", this.getGroupingType());
        dict.put("PreviewType", this.getPreviewType());
        dict.put("ShowInLockScreen", this.isShowInLockScreen());

        return dict;
    }

    /**
     * @deprecated Use {@link #createCombinedPayload(List, UUID)} instead.
     * Creates a single-item payload. Kept for backwards compatibility.
     */
    @Deprecated
    public NSDictionary createPayload() {
        NSDictionary payload = new NSDictionary();

        NSArray notificationSettingsArray = new NSArray(1);
        notificationSettingsArray.setValue(0, toSettingsDict());

        payload.put("NotificationSettings", notificationSettingsArray);
        payload.put("PayloadIdentifier", this.getPayloadIdentifier());
        payload.put("PayloadType", this.getPayloadType());
        payload.put("PayloadUUID", this.getPayloadUUID());
        payload.put("PayloadVersion", this.getPayloadVersion());

        return payload;
    }
}

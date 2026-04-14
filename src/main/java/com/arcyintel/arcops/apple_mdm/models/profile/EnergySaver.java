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
public class EnergySaver extends BasePayload {

    private static final String PAYLOAD_TYPE = "com.apple.MCX";

    private int desktopSleepTimer;
    private int displaySleepTimer;
    private int diskSleepTimer;
    private boolean wakeOnLAN;
    private boolean automaticRestartOnPowerLoss;
    private int batterySleepTimer;
    private int batteryDisplaySleepTimer;

    @SuppressWarnings("unchecked")
    public static EnergySaver createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }

        EnergySaver energySaver = EnergySaver.builder()
                .desktopSleepTimer(toInt(map.get("desktopSleepTimer"), 0))
                .displaySleepTimer(toInt(map.get("displaySleepTimer"), 0))
                .diskSleepTimer(toInt(map.get("diskSleepTimer"), 0))
                .wakeOnLAN(toBool(map.get("wakeOnLAN"), false))
                .automaticRestartOnPowerLoss(toBool(map.get("automaticRestartOnPowerLoss"), false))
                .batterySleepTimer(toInt(map.get("batterySleepTimer"), 0))
                .batteryDisplaySleepTimer(toInt(map.get("batteryDisplaySleepTimer"), 0))
                .build();

        energySaver.setPayloadIdentifier("com.arcyintel.arcops.energysaver." + policyId);
        energySaver.setPayloadType(PAYLOAD_TYPE);
        energySaver.setPayloadUUID(UUID.randomUUID().toString());
        energySaver.setPayloadVersion(1);
        energySaver.setPayloadRemovalDisallowed(true);

        return energySaver;
    }

    public NSDictionary createPayload() {
        NSDictionary payload = new NSDictionary();

        // Standard payload fields
        payload.put("PayloadType", new NSString(getPayloadType()));
        payload.put("PayloadVersion", new NSNumber(getPayloadVersion()));
        payload.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        payload.put("PayloadUUID", new NSString(getPayloadUUID()));
        payload.put("PayloadRemovalDisallowed", new NSNumber(getPayloadRemovalDisallowed()));

        // Desktop power settings
        NSDictionary desktopDict = new NSDictionary();
        desktopDict.put("System Sleep Timer", new NSNumber(desktopSleepTimer));
        desktopDict.put("Display Sleep Timer", new NSNumber(displaySleepTimer));
        desktopDict.put("Disk Sleep Timer", new NSNumber(diskSleepTimer));
        desktopDict.put("Wake On LAN", new NSNumber(wakeOnLAN));
        desktopDict.put("Automatic Restart On Power Loss", new NSNumber(automaticRestartOnPowerLoss));
        payload.put("com.apple.EnergySaver.desktop.ACPower", desktopDict);

        // Portable (battery) power settings
        NSDictionary portableDict = new NSDictionary();
        portableDict.put("System Sleep Timer", new NSNumber(batterySleepTimer));
        portableDict.put("Display Sleep Timer", new NSNumber(batteryDisplaySleepTimer));
        portableDict.put("Disk Sleep Timer", new NSNumber(diskSleepTimer));
        portableDict.put("Wake On LAN", new NSNumber(wakeOnLAN));
        portableDict.put("Automatic Restart On Power Loss", new NSNumber(automaticRestartOnPowerLoss));
        payload.put("com.apple.EnergySaver.portable.BatteryPower", portableDict);

        return payload;
    }
}

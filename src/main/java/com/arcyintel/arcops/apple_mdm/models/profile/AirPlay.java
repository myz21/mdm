package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSString;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AirPlay extends BasePayload {

    private List<AllowListItem> allowList;
    private List<PasswordsItem> passwords;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AllowListItem {
        private String deviceID;
        private String deviceName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PasswordsItem {
        private String deviceName;
        private String password;
        private String deviceID;
    }

    @SuppressWarnings("unchecked")
    public static AirPlay createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) return null;

        List<AllowListItem> allowListItems = new ArrayList<>();
        List<Map<String, Object>> rawAllowList = (List<Map<String, Object>>) map.get("AllowList");
        if (rawAllowList != null) {
            for (Map<String, Object> item : rawAllowList) {
                allowListItems.add(AllowListItem.builder()
                        .deviceID((String) item.get("DeviceID"))
                        .deviceName((String) item.get("DeviceName"))
                        .build());
            }
        }

        List<PasswordsItem> passwordsItems = new ArrayList<>();
        List<Map<String, Object>> rawPasswords = (List<Map<String, Object>>) map.get("Passwords");
        if (rawPasswords != null) {
            for (Map<String, Object> item : rawPasswords) {
                passwordsItems.add(PasswordsItem.builder()
                        .deviceName((String) item.get("DeviceName"))
                        .password((String) item.get("Password"))
                        .deviceID((String) item.get("DeviceID"))
                        .build());
            }
        }

        AirPlay payload = AirPlay.builder()
                .allowList(allowListItems)
                .passwords(passwordsItems)
                .build();
        payload.setPayloadIdentifier("com.apple.airplay." + policyId);
        payload.setPayloadType("com.apple.airplay");
        payload.setPayloadUUID(UUID.randomUUID().toString());
        payload.setPayloadVersion(1);
        return payload;
    }

    public NSDictionary createPayload() {
        NSDictionary d = new NSDictionary();

        if (allowList != null && !allowList.isEmpty()) {
            NSArray arr = new NSArray(allowList.size());
            for (int i = 0; i < allowList.size(); i++) {
                NSDictionary item = new NSDictionary();
                AllowListItem ali = allowList.get(i);
                if (ali.getDeviceID() != null) item.put("DeviceID", new NSString(ali.getDeviceID()));
                if (ali.getDeviceName() != null) item.put("DeviceName", new NSString(ali.getDeviceName()));
                arr.setValue(i, item);
            }
            d.put("AllowList", arr);
        }

        if (passwords != null && !passwords.isEmpty()) {
            NSArray arr = new NSArray(passwords.size());
            for (int i = 0; i < passwords.size(); i++) {
                NSDictionary item = new NSDictionary();
                PasswordsItem pi = passwords.get(i);
                if (pi.getDeviceName() != null) item.put("DeviceName", new NSString(pi.getDeviceName()));
                if (pi.getPassword() != null) item.put("Password", new NSString(pi.getPassword()));
                if (pi.getDeviceID() != null) item.put("DeviceID", new NSString(pi.getDeviceID()));
                arr.setValue(i, item);
            }
            d.put("Passwords", arr);
        }

        d.put("PayloadIdentifier", new NSString(getPayloadIdentifier()));
        d.put("PayloadType", new NSString(getPayloadType()));
        d.put("PayloadUUID", new NSString(getPayloadUUID()));
        d.put("PayloadVersion", getPayloadVersion());
        return d;
    }
}
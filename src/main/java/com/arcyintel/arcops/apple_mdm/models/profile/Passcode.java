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
public class Passcode extends BasePayload {


    private boolean allowSimple = true;
    private boolean changeAtNextAuth = false;
    private CustomRegex customRegex;
    private boolean forcePIN = false;
    private int maxFailedAttempts = 11;
    private int maxGracePeriod = 0;
    private int maxInactivity;
    private int maxPINAgeInDays;
    private int minComplexChars = 0;
    private int minLength = 0;
    private int minutesUntilFailedLoginReset;
    private int pinHistory;
    private boolean requireAlphanumeric = false;

    @SuppressWarnings("unchecked")
    public static Passcode createFromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Passcode passcode = Passcode.builder()
                .allowSimple(toBool(map.get("allowSimple"), true))
                .changeAtNextAuth(toBool(map.get("changeAtNextAuth"), false))
                .forcePIN(toBool(map.get("forcePIN"), false))
                .maxFailedAttempts(toInt(map.get("maxFailedAttempts"), 11))
                .maxGracePeriod(toInt(map.get("maxGracePeriod"), 0))
                .maxInactivity(toInt(map.get("maxInactivity"), 0))
                .maxPINAgeInDays(toInt(map.get("maxPINAgeInDays"), 0))
                .minComplexChars(toInt(map.get("minComplexChars"), 0))
                .minLength(toInt(map.get("minLength"), 0))
                .minutesUntilFailedLoginReset(toInt(map.get("minutesUntilFailedLoginReset"), 0))
                .pinHistory(toInt(map.get("pinHistory"), 0))
                .requireAlphanumeric(toBool(map.get("requireAlphanumeric"), false))
                .build();

        if (map.containsKey("customRegex")) {
            Map<String, String> customRegexMap = (Map<String, String>) map.get("customRegex");
            CustomRegex customRegex = new CustomRegex();
            customRegex.setRegex(customRegexMap.get("regex"));
            customRegex.setDescription(customRegexMap.get("description"));
            passcode.setCustomRegex(customRegex);
        }

        passcode.setPayloadIdentifier(String.format("policy_password-%s", map.get("policyId")));
        passcode.setPayloadType("com.apple.mobiledevice.passwordpolicy");
        passcode.setPayloadUUID(UUID.randomUUID().toString());
        passcode.setPayloadVersion(1);
        passcode.setPayloadRemovalDisallowed(true);

        return passcode;
    }

    public NSDictionary createPayload() {
        NSDictionary passcodeDict = new NSDictionary();
        passcodeDict.put("allowSimple", this.isAllowSimple());
        passcodeDict.put("changeAtNextAuth", this.isChangeAtNextAuth());
        if (this.getCustomRegex() != null) {
            NSDictionary customRegexDict = new NSDictionary();
            customRegexDict.put("regex", this.getCustomRegex().getRegex());
            customRegexDict.put("description", this.getCustomRegex().getDescription());
            passcodeDict.put("customRegex", customRegexDict);
        }
        passcodeDict.put("forcePIN", this.isForcePIN());
        passcodeDict.put("maxFailedAttempts", this.getMaxFailedAttempts());
        passcodeDict.put("maxGracePeriod", this.getMaxGracePeriod());
        passcodeDict.put("maxInactivity", this.getMaxInactivity());
        passcodeDict.put("maxPINAgeInDays", this.getMaxPINAgeInDays());
        passcodeDict.put("minComplexChars", this.getMinComplexChars());
        passcodeDict.put("minLength", this.getMinLength());
        passcodeDict.put("minutesUntilFailedLoginReset", this.getMinutesUntilFailedLoginReset());
        passcodeDict.put("pinHistory", this.getPinHistory());
        passcodeDict.put("requireAlphanumeric", this.isRequireAlphanumeric());

        passcodeDict.put("PayloadIdentifier", this.getPayloadIdentifier());
        passcodeDict.put("PayloadType", this.getPayloadType());
        passcodeDict.put("PayloadUUID", this.getPayloadUUID());
        passcodeDict.put("PayloadVersion", this.getPayloadVersion());
        passcodeDict.put("PayloadRemovalDisallowed", this.getPayloadRemovalDisallowed());
        return passcodeDict;
    }

    @Data
    public static class CustomRegex {
        private String regex;
        private String description;
    }
}
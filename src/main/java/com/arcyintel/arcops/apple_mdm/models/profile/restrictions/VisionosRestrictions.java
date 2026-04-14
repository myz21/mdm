package com.arcyintel.arcops.apple_mdm.models.profile.restrictions;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.models.profile.Restrictions;

import java.util.Map;
import java.util.Set;

/**
 * visionOS restriction keys.
 * Apple documentation: https://developer.apple.com/documentation/devicemanagement/restrictions
 *
 * visionOS is a relatively new platform with limited restriction support.
 * TODO: Verify and complete from Apple documentation.
 */
public class VisionosRestrictions extends Restrictions {

    private static final Set<String> SUPPORTED_KEYS = Set.of(
            "allowCamera", "allowScreenShot",
            "allowAssistant", "allowAssistantWhileLocked",
            "allowAppInstallation", "allowAppRemoval",
            "allowUIAppInstallation", "allowAutomaticAppDownloads",
            "allowInAppPurchases",
            "allowSafari", "safariAllowAutoFill", "safariAllowJavaScript",
            "safariAllowPopups", "safariForceFraudWarning",
            "allowCloudBackup", "allowCloudDocumentSync", "allowCloudPhotoLibrary",
            "allowCloudKeychainSync",
            "allowAirDrop",
            "allowExplicitContent",
            "allowPasswordAutoFill", "allowPasswordSharing",
            "allowDiagnosticSubmission",
            "forceEncryptedBackup",
            "allowAccountModification", "allowPasscodeModification",
            "allowFingerprintForUnlock",
            "allowGenmoji", "allowImagePlayground", "allowWritingTools",
            "enforcedSoftwareUpdateDelay",
            "forceDelayedSoftwareUpdates",
            "allowRapidSecurityResponseInstallation", "allowRapidSecurityResponseRemoval"
    );

    public static VisionosRestrictions createFromMap(Map<String, Object> map) {
        if (map == null) return null;
        Restrictions base = Restrictions.createFromMap(map);
        VisionosRestrictions visionos = new VisionosRestrictions();
        copyFields(base, visionos);
        return visionos;
    }

    @Override
    public NSDictionary createPayload() {
        NSDictionary dictionary = new NSDictionary();
        Map<String, Object> allKeys = buildAllKeyValues();

        for (Map.Entry<String, Object> entry : allKeys.entrySet()) {
            if (SUPPORTED_KEYS.contains(entry.getKey())) {
                putValue(dictionary, entry.getKey(), entry.getValue());
            }
        }

        addPayloadEnvelope(dictionary);
        return dictionary;
    }
}

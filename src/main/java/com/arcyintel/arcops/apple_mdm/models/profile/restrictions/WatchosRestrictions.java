package com.arcyintel.arcops.apple_mdm.models.profile.restrictions;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.models.profile.Restrictions;

import java.util.Map;
import java.util.Set;

/**
 * watchOS restriction keys.
 * Apple documentation: https://developer.apple.com/documentation/devicemanagement/restrictions
 *
 * watchOS provides very limited restriction support.
 * TODO: Verify and complete from Apple documentation.
 */
public class WatchosRestrictions extends Restrictions {

    private static final Set<String> SUPPORTED_KEYS = Set.of(
            "allowScreenShot",
            "allowAssistant",
            "allowCloudBackup",
            "allowExplicitContent",
            "ratingRegion", "ratingApps",
            "allowPasscodeModification",
            "allowEraseContentAndSettings",
            "enforcedSoftwareUpdateDelay",
            "forceDelayedSoftwareUpdates",
            "allowRapidSecurityResponseInstallation", "allowRapidSecurityResponseRemoval"
    );

    public static WatchosRestrictions createFromMap(Map<String, Object> map) {
        if (map == null) return null;
        Restrictions base = Restrictions.createFromMap(map);
        WatchosRestrictions watchos = new WatchosRestrictions();
        copyFields(base, watchos);
        return watchos;
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

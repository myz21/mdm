package com.arcyintel.arcops.apple_mdm.models.profile.restrictions;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.models.profile.Restrictions;

import java.util.Map;
import java.util.Set;

/**
 * tvOS restriction keys.
 * Apple documentation: https://developer.apple.com/documentation/devicemanagement/restrictions
 *
 * tvOS supports a limited set of restrictions.
 * TODO: Verify and complete from Apple documentation.
 */
public class TvosRestrictions extends Restrictions {

    private static final Set<String> SUPPORTED_KEYS = Set.of(
            "allowAirPlayIncomingRequests",
            "forceAirPlayIncomingRequestsPairingPassword",
            "forceAirPlayOutgoingRequestsPairingPassword",
            "allowRemoteScreenObservation",
            "forceClassroomUnpromptedScreenObservation",
            "allowUIConfigurationProfileInstallation",
            "allowScreenShot",
            "allowExplicitContent",
            "ratingRegion", "ratingApps", "ratingMovies", "ratingTVShows",
            "blockedAppBundleIDs", "allowListedAppBundleIDs",
            "enforcedSoftwareUpdateDelay",
            "forceDelayedSoftwareUpdates",
            "forceDelayedAppSoftwareUpdates",
            "allowRapidSecurityResponseInstallation",
            "allowRapidSecurityResponseRemoval"
    );

    public static TvosRestrictions createFromMap(Map<String, Object> map) {
        if (map == null) return null;
        Restrictions base = Restrictions.createFromMap(map);
        TvosRestrictions tvos = new TvosRestrictions();
        copyFields(base, tvos);
        return tvos;
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

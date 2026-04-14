package com.arcyintel.arcops.apple_mdm.models.profile.restrictions;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.models.profile.Restrictions;

import java.util.Map;
import java.util.Set;

/**
 * macOS restriction keys.
 * Apple documentation: https://developer.apple.com/documentation/devicemanagement/restrictions
 *
 * NOTE: blockedAppBundleIDs and allowListedAppBundleIDs are not supported via
 * macOS MDM profiles. These capabilities will be provided via agent in the future.
 */
public class MacosRestrictions extends Restrictions {

    private static final Set<String> SUPPORTED_KEYS = Set.of(
            // Shared with iOS
            "allowCamera", "allowScreenShot", "allowRemoteScreenObservation",
            "allowAssistant", "allowAssistantWhileLocked",
            "allowAirDrop", "allowAirPrint", "allowAirPrintCredentialsStorage",
            "allowAirPrintiBeaconDiscovery", "forceAirPrintTrustedTLSRequirement",
            "allowCloudBackup", "allowCloudDocumentSync", "allowCloudPhotoLibrary",
            "allowCloudKeychainSync",
            "allowDiagnosticSubmission", "forceEncryptedBackup",
            "allowExplicitContent",
            "allowFingerprintForUnlock", "allowFingerprintModification",
            "allowGameCenter", "allowMultiplayerGaming", "allowAddingGameCenterFriends",
            "allowPasswordAutoFill", "allowPasswordSharing", "allowPasswordProximityRequests",
            "allowManagedAppsCloudSync",
            "allowEraseContentAndSettings",
            "allowSpotlightInternetResults",
            "allowDefinitionLookup",
            "forceAssistantProfanityFilter",
            "allowActivityContinuation",
            "allowEnterpriseAppTrust",
            "allowAppInstallation", "allowAppRemoval", "allowSystemAppRemoval",
            "allowUIAppInstallation", "allowAutomaticAppDownloads",
            "allowVPNCreation",
            "allowBluetoothModification",
            "allowAccountModification", "allowPasscodeModification",
            "allowDeviceNameModification", "allowWallpaperModification",
            "allowNotificationsModification",
            "forceAutomaticDateAndTime",

            // Apple Intelligence (shared)
            "allowGenmoji", "allowImagePlayground", "allowImageWand",
            "allowWritingTools", "allowMailSummary",
            "allowPersonalizedHandwritingResults",

            // macOS-only keys
            "allowContentCaching",
            "allowCloudDesktopAndDocuments",
            "allowFileSharingModification",
            "allowInternetSharingModification",
            "allowLocalUserCreation",
            "allowPrinterSharingModification",
            "allowRemoteAppleEventsModification",
            "allowStartupDiskModification",
            "allowTimeMachineBackup",
            "allowUniversalControl",
            "allowARDRemoteManagementModification",
            "allowAutomaticScreenSaver",
            "allowBluetoothSharingModification",
            "allowiPhoneWidgetsOnMac",
            "allowiTunesFileSharing",
            "allowMediaSharingModification",
            "allowDeviceSleep",
            "allowAutoUnlock",
            "allowUnpairedExternalBootToRecovery",

            // Shared security/privacy
            "allowUntrustedTLSPrompt",
            "allowDiagnosticSubmissionModification",
            "allowUSBRestrictedMode",
            "forceWiFiPowerOn",

            // iCloud (shared + macOS)
            "allowCloudAddressBook", "allowCloudBookmarks", "allowCloudCalendar",
            "allowCloudFreeform", "allowCloudMail", "allowCloudNotes",
            "allowCloudPrivateRelay", "allowCloudReminders",

            // Software update
            "enforcedSoftwareUpdateDelay", "enforcedSoftwareUpdateMinorOSDeferredInstallDelay",
            "enforcedSoftwareUpdateMajorOSDeferredInstallDelay", "enforcedSoftwareUpdateNonOSDeferredInstallDelay",
            "forceDelayedAppSoftwareUpdates", "forceDelayedMajorSoftwareUpdates", "forceDelayedSoftwareUpdates",
            "allowRapidSecurityResponseInstallation", "allowRapidSecurityResponseRemoval",

            // Intelligence
            "allowAppleIntelligenceReport",
            "allowExternalIntelligenceIntegrations", "allowExternalIntelligenceIntegrationsSignIn",
            "allowedExternalIntelligenceWorkspaceIDs",

            // Mail
            "allowMailPrivacyProtection", "allowMailSmartReplies",

            // Managed open-in
            "allowOpenFromManagedToUnmanaged", "allowOpenFromUnmanagedToManaged",
            "allowManagedToWriteUnmanagedContacts", "allowUnmanagedToReadManagedContacts",
            "forceAirDropUnmanaged",
            "requireManagedPasteboard",

            // Classroom
            "forceClassroomAutomaticallyJoinClasses", "forceClassroomRequestPermissionToLeaveClasses",
            "forceClassroomUnpromptedAppAndDeviceLock", "forceClassroomUnpromptedScreenObservation",

            // Misc
            "allowDictation", "forceOnDeviceOnlyDictation", "forceOnDeviceOnlyTranslation",
            "enforcedFingerprintTimeout",
            "forceAuthenticationBeforeAutoFill",
            "allowSafari", "safariAllowAutoFill", "safariAllowJavaScript",
            "safariAllowPopups", "safariForceFraudWarning", "safariAcceptCookies",
            "allowSafariSummary",
            "allowNotesTranscription", "allowNotesTranscriptionSummary",
            "forceAirPlayIncomingRequestsPairingPassword", "forceAirPlayOutgoingRequestsPairingPassword",
            "allowAirPlayIncomingRequests",
            "allowFindMyDevice",
            "allowVisualIntelligenceSummary",
            "forceBypassScreenCaptureAlert",
            "allowApplePersonalizedAdvertising",
            "forceLimitAdTracking",
            "ratingRegion", "ratingApps", "ratingMovies", "ratingTVShows"

            // NOT INCLUDED: blockedAppBundleIDs, allowListedAppBundleIDs
            // Not supported via macOS MDM profiles - will be handled via agent
    );

    public static MacosRestrictions createFromMap(Map<String, Object> map) {
        if (map == null) return null;
        Restrictions base = Restrictions.createFromMap(map);
        MacosRestrictions macos = new MacosRestrictions();
        copyFields(base, macos);
        return macos;
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

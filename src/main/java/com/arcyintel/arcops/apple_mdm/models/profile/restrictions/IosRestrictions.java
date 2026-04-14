package com.arcyintel.arcops.apple_mdm.models.profile.restrictions;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.models.profile.Restrictions;

import java.util.Map;
import java.util.Set;

/**
 * iOS/iPadOS restriction keys.
 * Apple documentation: https://developer.apple.com/documentation/devicemanagement/restrictions
 *
 * SUPPORTED_KEYS contains all keys marked as "Supported" for iOS
 * in the Apple documentation.
 */
public class IosRestrictions extends Restrictions {

    private static final Set<String> SUPPORTED_KEYS = Set.of(
            // Device Functionality
            "allowCamera", "allowVideoConferencing", "allowScreenShot", "allowRemoteScreenObservation",
            "allowFingerprintForUnlock", "allowAssistant", "allowAssistantWhileLocked",
            "allowVoiceDialing", "allowGlobalBackgroundFetchWhenRoaming",

            // Application Settings
            "allowAppInstallation", "allowiTunes", "forceITunesStorePasswordEntry",
            "allowInAppPurchases", "allowEnterpriseAppTrust", "allowEnterpriseBookBackup",
            "allowManagedAppsCloudSync", "allowSafari", "safariForceFraudWarning",
            "safariAllowAutoFill", "safariAllowJavaScript", "safariAllowPopups",
            "safariAcceptCookies", "allowPassbookWhileLocked", "allowAddingGameCenterFriends",

            // iCloud Settings
            "allowCloudBackup", "allowCloudDocumentSync", "allowPhotoStream",
            "allowSharedStream", "allowCloudPhotoLibrary", "allowEnterpriseBookMetadataSync",

            // Security and Privacy Settings
            "allowLockScreenNotificationsView", "allowLockScreenTodayView",
            "allowLockScreenControlCenter", "allowOTAPKIUpdates", "forceLimitAdTracking",
            "allowDiagnosticSubmission", "allowUntrustedTLSPrompt", "forceEncryptedBackup",

            // Explicit Content
            "allowExplicitContent", "allowBookstoreErotica",
            "ratingRegion", "ratingApps", "ratingMovies", "ratingTVShows",

            // Advanced - Device Functionality
            "allowAirDrop", "allowAppCellularDataModification", "allowFingerprintModification",
            "allowChat", "allowRCSMessaging", "allowGameCenter", "allowMultiplayerGaming",
            "allowUIConfigurationProfileInstallation", "allowActivityContinuation",
            "allowDefinitionLookup", "allowPredictiveKeyboard", "allowAutoCorrection",
            "allowSpellCheck", "allowContinuousPathKeyboard", "allowKeyboardShortcuts",
            "allowFilesUSBDriveAccess", "allowFilesNetworkDriveAccess",
            "allowPairedWatch", "allowDiagnosticSubmissionModification",
            "allowBluetoothModification", "forceWiFiPowerOn", "forceWiFiToAllowedNetworksOnly",
            "allowPersonalHotspotModification", "allowVPNCreation",
            "allowAirPrint", "allowAirPrintCredentialsStorage",
            "allowAirPrintiBeaconDiscovery", "forceAirPrintTrustedTLSRequirement",
            "allowCellularPlanModification", "allowESIMModification",
            "allowESIMOutgoingTransfers", "allowLiveVoicemail",
            "forcePreserveESIMOnErase", "allowAutoDim",
            "allowiPhoneMirroring", "allowCallRecording",

            // Advanced - Application Settings
            "allowUIAppInstallation", "allowMarketplaceAppInstallation",
            "allowWebDistributionAppInstallation", "allowAppRemoval",
            "allowSystemAppRemoval", "allowBookstore", "allowMusicService",
            "allowRadioService", "allowNews", "allowPodcasts",
            "allowAutomaticAppDownloads", "allowAppsToBeHidden", "allowAppsToBeLocked",

            // Advanced - Security and Privacy
            "allowAccountModification", "allowEraseContentAndSettings",
            "allowAssistantUserGeneratedContent", "allowFindMyFriends",
            "allowFindMyFriendsModification", "forceAssistantProfanityFilter",
            "allowSpotlightInternetResults", "allowEnablingRestrictions",
            "allowPasscodeModification", "allowDeviceNameModification",
            "allowWallpaperModification", "allowDefaultBrowserModification",
            "allowNotificationsModification", "forceAutomaticDateAndTime",
            "allowPasswordAutoFill", "allowPasswordProximityRequests",
            "allowPasswordSharing", "allowUSBRestrictedMode",
            "allowHostPairing", "allowSharedDeviceTemporarySession",

            // Apple Intelligence
            "allowGenmoji", "allowImagePlayground", "allowImageWand",
            "allowPersonalizedHandwritingResults", "allowWritingTools", "allowMailSummary",

            // App Restrictions (lists)
            "allowListedAppBundleIDs", "autonomousSingleAppModePermittedAppIDs", "blockedAppBundleIDs",

            // Additional Apple Doc Fields
            "forceBypassScreenCaptureAlert", "allowAirPlayIncomingRequests",
            "allowAppClips", "allowAppleIntelligenceReport",
            "allowApplePersonalizedAdvertising",
            "allowCloudAddressBook", "allowCloudBookmarks", "allowCloudCalendar",
            "allowCloudFreeform", "allowCloudKeychainSync",
            "allowCloudMail", "allowCloudNotes", "allowCloudPrivateRelay",
            "allowCloudReminders",
            "allowDefaultCallingAppModification", "allowDefaultMessagingAppModification",
            "allowDictation",
            "allowExternalIntelligenceIntegrations", "allowExternalIntelligenceIntegrationsSignIn",
            "allowFindMyDevice",
            "allowMailPrivacyProtection", "allowMailSmartReplies",
            "allowManagedToWriteUnmanagedContacts",
            "allowNFC", "allowNotesTranscription", "allowNotesTranscriptionSummary",
            "allowOpenFromManagedToUnmanaged", "allowOpenFromUnmanagedToManaged",
            "allowProximitySetupToNewDevice",
            "allowRapidSecurityResponseInstallation", "allowRapidSecurityResponseRemoval",
            "allowRemoteAppPairing", "allowSafariSummary",
            "allowSatelliteConnection",
            "allowVisualIntelligenceSummary",
            "allowedExternalIntelligenceWorkspaceIDs",
            "enforcedFingerprintTimeout",
            "enforcedSoftwareUpdateDelay", "enforcedSoftwareUpdateMinorOSDeferredInstallDelay",
            "enforcedSoftwareUpdateMajorOSDeferredInstallDelay", "enforcedSoftwareUpdateNonOSDeferredInstallDelay",
            "forceAirDropUnmanaged",
            "forceAirPlayIncomingRequestsPairingPassword", "forceAirPlayOutgoingRequestsPairingPassword",
            "forceAuthenticationBeforeAutoFill",
            "forceClassroomAutomaticallyJoinClasses", "forceClassroomRequestPermissionToLeaveClasses",
            "forceClassroomUnpromptedAppAndDeviceLock", "forceClassroomUnpromptedScreenObservation",
            "forceDelayedAppSoftwareUpdates", "forceDelayedMajorSoftwareUpdates", "forceDelayedSoftwareUpdates",
            "forceOnDeviceOnlyDictation", "forceOnDeviceOnlyTranslation",
            "forceWatchWristDetection",
            "requireManagedPasteboard",
            "allowAutoUnlock"
    );

    public static IosRestrictions createFromMap(Map<String, Object> map) {
        if (map == null) return null;
        Restrictions base = Restrictions.createFromMap(map);
        IosRestrictions ios = new IosRestrictions();
        copyFields(base, ios);
        return ios;
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

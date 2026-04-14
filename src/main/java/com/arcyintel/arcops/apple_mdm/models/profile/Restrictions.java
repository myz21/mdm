package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSDictionary;
import lombok.*;

import org.springframework.beans.BeanUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restrictions extends BasePayload {



    /*
     *         1           Basic Restrictions
     */

    /*******************************
     *                              *
     *    Basic Restrictions        *
     *                              *
     *******************************/

    // *********** Device Functionality ***********

    private boolean allowCamera;
    private boolean allowVideoConferencing;

    private boolean allowScreenShot;
    private boolean allowRemoteScreenObservation;

    private boolean allowFingerprintForUnlock;

    private boolean allowAssistant;
    private boolean allowAssistantWhileLocked;

    private boolean allowVoiceDialing;

    private boolean allowGlobalBackgroundFetchWhenRoaming;

    // *********** Application Settings ***********

    private boolean allowAppInstallation;

    private boolean allowiTunes;
    private boolean forceITunesStorePasswordEntry;

    private boolean allowInAppPurchases;

    private boolean allowEnterpriseAppTrust;
    //Users can modify enterprise app trust options are missing in the UI

    private boolean allowEnterpriseBookBackup;

    private boolean allowManagedAppsCloudSync;

    private boolean allowSafari;
    private boolean safariForceFraudWarning;
    private boolean safariAllowAutoFill;
    private boolean safariAllowJavaScript;
    private boolean safariAllowPopups;
    private int safariAcceptCookies;

    private boolean allowPassbookWhileLocked;

    private boolean allowAddingGameCenterFriends;

    // *********** iCloud Settings ***********

    private boolean allowCloudBackup;

    private boolean allowCloudDocumentSync;

    private boolean allowPhotoStream;

    private boolean allowSharedStream;

    private boolean allowCloudPhotoLibrary;

    private boolean allowEnterpriseBookMetadataSync;

    // *********** Security and Privacy Settings ***********

    private boolean allowLockScreenNotificationsView;

    private boolean allowLockScreenTodayView;

    private boolean allowLockScreenControlCenter;

    private boolean allowOTAPKIUpdates;

    private boolean forceLimitAdTracking;

    private boolean allowDiagnosticSubmission;

    private boolean allowUntrustedTLSPrompt;

    private boolean forceEncryptedBackup;

    //Show notification on Apple Watch if worn is missing

    // ************ Explicit Content ************

    private boolean allowExplicitContent;

    private boolean allowBookstoreErotica;

    private String ratingRegion;

    private int ratingApps;
    private int ratingMovies;
    private int ratingTVShows;

    /*******************************
     *                               *
     *    Advanced Restrictions      *
     *                               *
     *******************************/

    // *********** Device Functionality ***********

    private boolean allowAirDrop;

    private boolean allowAppCellularDataModification;

    private boolean allowFingerprintModification;

    private boolean allowChat;

    private boolean allowRCSMessaging;

    private boolean allowGameCenter;

    private boolean allowMultiplayerGaming;

    private boolean allowUIConfigurationProfileInstallation;

    // Handoff is missing in the UI, this is handoff ?
    private boolean allowActivityContinuation;

    private boolean allowDefinitionLookup;

    private boolean allowPredictiveKeyboard;

    private boolean allowAutoCorrection;

    private boolean allowSpellCheck;

    private boolean allowContinuousPathKeyboard;

    private boolean allowKeyboardShortcuts;

    private boolean allowFilesUSBDriveAccess;

    private boolean allowFilesNetworkDriveAccess;

    private boolean allowPairedWatch;

    private boolean allowDiagnosticSubmissionModification;

    private boolean allowBluetoothModification;

    // Use voice to type is missing in the UI

    private boolean forceWiFiPowerOn;

    private boolean forceWiFiToAllowedNetworksOnly;

    private boolean allowPersonalHotspotModification;

    private boolean allowVPNCreation;

    private boolean allowAirPrint;
    private boolean allowAirPrintCredentialsStorage;
    private boolean allowAirPrintiBeaconDiscovery;
    private boolean forceAirPrintTrustedTLSRequirement;

    private boolean allowCellularPlanModification;

    private boolean allowESIMModification;

    private boolean allowESIMOutgoingTransfers;

    private boolean allowLiveVoicemail;

    private boolean forcePreserveESIMOnErase;

    private boolean allowAutoDim;

    private boolean allowiPhoneMirroring;

    private boolean allowCallRecording;

    // *********** Application Settings ***********

    private boolean allowUIAppInstallation;

    private boolean allowMarketplaceAppInstallation;

    private boolean allowWebDistributionAppInstallation;

    private boolean allowAppRemoval;

    private boolean allowSystemAppRemoval;

    private boolean allowBookstore;

    private boolean allowMusicService;

    private boolean allowRadioService;

    private boolean allowNews;

    private boolean allowPodcasts;

    private boolean allowAutomaticAppDownloads;

    private boolean allowAppsToBeHidden;

    private boolean allowAppsToBeLocked;

    // *********** Security and Privacy Settings ***********

    //https://developer.apple.com/documentation/devicemanagement/activationlockrequest Automatic Activation Lock

    private boolean allowAccountModification;

    private boolean allowEraseContentAndSettings;

    private boolean allowAssistantUserGeneratedContent;

    private boolean allowFindMyFriends;

    private boolean allowFindMyFriendsModification;

    private boolean forceAssistantProfanityFilter;

    private boolean allowSpotlightInternetResults;

    private boolean allowEnablingRestrictions;

    private boolean allowPasscodeModification;

    private boolean allowDeviceNameModification;

    private boolean allowWallpaperModification;

    private boolean allowDefaultBrowserModification;

    private boolean allowNotificationsModification;

    private boolean forceAutomaticDateAndTime;

    private boolean allowPasswordAutoFill;

    private boolean allowPasswordProximityRequests;

    private boolean allowPasswordSharing;

    private boolean allowUSBRestrictedMode;

    private boolean allowHostPairing;

    private boolean allowSharedDeviceTemporarySession;

    // *********** Apple Intelligence ***********

    private boolean allowGenmoji;

    private boolean allowImagePlayground;

    private boolean allowImageWand;

    private boolean allowPersonalizedHandwritingResults;

    private boolean allowWritingTools;

    private boolean allowMailSummary;

    // ChatGPT integration is missing in the UI

    // *********** App Restrictions ***********

    private List<String> allowListedAppBundleIDs;
    private List<String> autonomousSingleAppModePermittedAppIDs;
    private List<String> blockedAppBundleIDs;

    // *********** Additional Apple Doc Fields ***********

    private boolean forceBypassScreenCaptureAlert;
    private boolean allowAirPlayIncomingRequests;
    private boolean allowAppClips;
    private boolean allowAppleIntelligenceReport;
    private boolean allowApplePersonalizedAdvertising;
    private boolean allowARDRemoteManagementModification;
    private boolean allowAutomaticScreenSaver;
    private boolean allowAutoUnlock;
    private boolean allowBluetoothSharingModification;
    private boolean allowCloudAddressBook;
    private boolean allowCloudBookmarks;
    private boolean allowCloudCalendar;
    private boolean allowCloudDesktopAndDocuments;
    private boolean allowCloudFreeform;
    private boolean allowCloudKeychainSync;
    private boolean allowCloudMail;
    private boolean allowCloudNotes;
    private boolean allowCloudPrivateRelay;
    private boolean allowCloudReminders;
    private boolean allowContentCaching;
    private boolean allowDefaultCallingAppModification;
    private boolean allowDefaultMessagingAppModification;
    private boolean allowDeviceSleep;
    private boolean allowDictation;
    private boolean allowExternalIntelligenceIntegrations;
    private boolean allowExternalIntelligenceIntegrationsSignIn;
    private boolean allowFileSharingModification;
    private boolean allowFindMyDevice;
    private boolean allowInternetSharingModification;
    private boolean allowiPhoneWidgetsOnMac;
    private boolean allowiTunesFileSharing;
    private boolean allowLocalUserCreation;
    private boolean allowMailPrivacyProtection;
    private boolean allowMailSmartReplies;
    private boolean allowManagedToWriteUnmanagedContacts;
    private boolean allowMediaSharingModification;
    private boolean allowNFC;
    private boolean allowNotesTranscription;
    private boolean allowNotesTranscriptionSummary;
    private boolean allowOpenFromManagedToUnmanaged;
    private boolean allowOpenFromUnmanagedToManaged;
    private boolean allowPrinterSharingModification;
    private boolean allowProximitySetupToNewDevice;
    private boolean allowRapidSecurityResponseInstallation;
    private boolean allowRapidSecurityResponseRemoval;
    private boolean allowRemoteAppleEventsModification;
    private boolean allowRemoteAppPairing;
    private boolean allowSafariSummary;
    private boolean allowSatelliteConnection;
    private boolean allowStartupDiskModification;
    private boolean allowTimeMachineBackup;
    private boolean allowUniversalControl;
    private boolean allowUnmanagedToReadManagedContacts;
    private boolean allowUnpairedExternalBootToRecovery;
    private boolean allowVisualIntelligenceSummary;
    private List<String> allowedExternalIntelligenceWorkspaceIDs;
    private int enforcedFingerprintTimeout;
    private int enforcedSoftwareUpdateDelay;
    private int enforcedSoftwareUpdateMinorOSDeferredInstallDelay;
    private int enforcedSoftwareUpdateMajorOSDeferredInstallDelay;
    private int enforcedSoftwareUpdateNonOSDeferredInstallDelay;
    private boolean forceAirDropUnmanaged;
    private boolean forceAirPlayIncomingRequestsPairingPassword;
    private boolean forceAirPlayOutgoingRequestsPairingPassword;
    private boolean forceAuthenticationBeforeAutoFill;
    private boolean forceClassroomAutomaticallyJoinClasses;
    private boolean forceClassroomRequestPermissionToLeaveClasses;
    private boolean forceClassroomUnpromptedAppAndDeviceLock;
    private boolean forceClassroomUnpromptedScreenObservation;
    private boolean forceDelayedAppSoftwareUpdates;
    private boolean forceDelayedMajorSoftwareUpdates;
    private boolean forceDelayedSoftwareUpdates;
    private boolean forceOnDeviceOnlyDictation;
    private boolean forceOnDeviceOnlyTranslation;
    private boolean forceWatchWristDetection;
    private boolean requireManagedPasteboard;


    /**
     * Creates a minimal Restrictions payload that only allows the given bundle IDs (kiosk allowlist).
     */
    public static NSDictionary createForKioskAllowList(List<String> allowedBundleIds, UUID policyId) {
        NSDictionary dict = new NSDictionary();
        dict.put("allowListedAppBundleIDs", allowedBundleIds.toArray(new String[0]));
        dict.put("PayloadIdentifier", "policy_restrictions-kiosk-" + (policyId != null ? policyId : UUID.randomUUID()));
        dict.put("PayloadType", "com.apple.applicationaccess");
        dict.put("PayloadUUID", UUID.randomUUID().toString());
        dict.put("PayloadVersion", 1);
        return dict;
    }

    @SuppressWarnings("unchecked")
    public static Restrictions createFromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Restrictions restrictions = Restrictions.builder()
                .forceITunesStorePasswordEntry(toBool(map.get("forceITunesStorePasswordEntry"), false))
                .allowVoiceDialing(toBool(map.get("allowVoiceDialing"), true))
                .allowAccountModification(toBool(map.get("allowAccountModification"), true))
                .allowActivityContinuation(toBool(map.get("allowActivityContinuation"), true))
                .allowAddingGameCenterFriends(toBool(map.get("allowAddingGameCenterFriends"), true))
                .allowAirDrop(toBool(map.get("allowAirDrop"), true))
                .allowAirPrint(toBool(map.get("allowAirPrint"), true))
                .allowAirPrintCredentialsStorage(toBool(map.get("allowAirPrintCredentialsStorage"), true))
                .allowAirPrintiBeaconDiscovery(toBool(map.get("allowAirPrintiBeaconDiscovery"), true))
                .allowAppCellularDataModification(toBool(map.get("allowAppCellularDataModification"), true))
                .allowAppInstallation(toBool(map.get("allowAppInstallation"), true))
                .allowAppRemoval(toBool(map.get("allowAppRemoval"), true))
                .allowAppsToBeHidden(toBool(map.get("allowAppsToBeHidden"), true))
                .allowAppsToBeLocked(toBool(map.get("allowAppsToBeLocked"), true))
                .allowAssistant(toBool(map.get("allowAssistant"), true))
                .allowAssistantUserGeneratedContent(toBool(map.get("allowAssistantUserGeneratedContent"), true))
                .allowAssistantWhileLocked(toBool(map.get("allowAssistantWhileLocked"), true))
                .allowAutoCorrection(toBool(map.get("allowAutoCorrection"), true))
                .allowAutoDim(toBool(map.get("allowAutoDim"), true))
                .allowAutomaticAppDownloads(toBool(map.get("allowAutomaticAppDownloads"), true))
                .allowBluetoothModification(toBool(map.get("allowBluetoothModification"), true))
                .allowBookstore(toBool(map.get("allowBookstore"), true))
                .allowBookstoreErotica(toBool(map.get("allowBookstoreErotica"), true))
                .allowCallRecording(toBool(map.get("allowCallRecording"), true))
                .allowCamera(toBool(map.get("allowCamera"), true))
                .allowCellularPlanModification(toBool(map.get("allowCellularPlanModification"), true))
                .allowChat(toBool(map.get("allowChat"), true))
                .allowCloudBackup(toBool(map.get("allowCloudBackup"), true))
                .allowCloudDocumentSync(toBool(map.get("allowCloudDocumentSync"), true))
                .allowCloudPhotoLibrary(toBool(map.get("allowCloudPhotoLibrary"), true))
                .allowContinuousPathKeyboard(toBool(map.get("allowContinuousPathKeyboard"), true))
                .allowDefaultBrowserModification(toBool(map.get("allowDefaultBrowserModification"), true))
                .allowDefinitionLookup(toBool(map.get("allowDefinitionLookup"), true))
                .allowDeviceNameModification(toBool(map.get("allowDeviceNameModification"), true))
                .allowDiagnosticSubmission(toBool(map.get("allowDiagnosticSubmission"), true))
                .allowDiagnosticSubmissionModification(toBool(map.get("allowDiagnosticSubmissionModification"), true))
                .allowEnablingRestrictions(toBool(map.get("allowEnablingRestrictions"), true))
                .allowEnterpriseAppTrust(toBool(map.get("allowEnterpriseAppTrust"), true))
                .allowEnterpriseBookBackup(toBool(map.get("allowEnterpriseBookBackup"), true))
                .allowEnterpriseBookMetadataSync(toBool(map.get("allowEnterpriseBookMetadataSync"), true))
                .allowEraseContentAndSettings(toBool(map.get("allowEraseContentAndSettings"), true))
                .allowESIMModification(toBool(map.get("allowESIMModification"), true))
                .allowESIMOutgoingTransfers(toBool(map.get("allowESIMOutgoingTransfers"), true))
                .allowExplicitContent(toBool(map.get("allowExplicitContent"), true))
                .allowFilesNetworkDriveAccess(toBool(map.get("allowFilesNetworkDriveAccess"), true))
                .allowFilesUSBDriveAccess(toBool(map.get("allowFilesUSBDriveAccess"), true))
                .allowFindMyFriends(toBool(map.get("allowFindMyFriends"), true))
                .allowFindMyFriendsModification(toBool(map.get("allowFindMyFriendsModification"), true))
                .allowFingerprintForUnlock(toBool(map.get("allowFingerprintForUnlock"), true))
                .allowFingerprintModification(toBool(map.get("allowFingerprintModification"), true))
                .allowGameCenter(toBool(map.get("allowGameCenter"), true))
                .allowGenmoji(toBool(map.get("allowGenmoji"), true))
                .allowGlobalBackgroundFetchWhenRoaming(toBool(map.get("allowGlobalBackgroundFetchWhenRoaming"), true))
                .allowHostPairing(toBool(map.get("allowHostPairing"), true))
                .allowImagePlayground(toBool(map.get("allowImagePlayground"), true))
                .allowImageWand(toBool(map.get("allowImageWand"), true))
                .allowInAppPurchases(toBool(map.get("allowInAppPurchases"), true))
                .allowiPhoneMirroring(toBool(map.get("allowiPhoneMirroring"), true))
                .allowiTunes(toBool(map.get("allowiTunes"), true))
                .allowKeyboardShortcuts(toBool(map.get("allowKeyboardShortcuts"), true))
                .allowLiveVoicemail(toBool(map.get("allowLiveVoicemail"), true))
                .allowLockScreenControlCenter(toBool(map.get("allowLockScreenControlCenter"), true))
                .allowLockScreenNotificationsView(toBool(map.get("allowLockScreenNotificationsView"), true))
                .allowLockScreenTodayView(toBool(map.get("allowLockScreenTodayView"), true))
                .allowMailSummary(toBool(map.get("allowMailSummary"), true))
                .allowManagedAppsCloudSync(toBool(map.get("allowManagedAppsCloudSync"), true))
                .allowMarketplaceAppInstallation(toBool(map.get("allowMarketplaceAppInstallation"), true))
                .allowMultiplayerGaming(toBool(map.get("allowMultiplayerGaming"), true))
                .allowMusicService(toBool(map.get("allowMusicService"), true))
                .allowNews(toBool(map.get("allowNews"), true))
                .allowNotificationsModification(toBool(map.get("allowNotificationsModification"), true))
                .allowOTAPKIUpdates(toBool(map.get("allowOTAPKIUpdates"), true))
                .allowPairedWatch(toBool(map.get("allowPairedWatch"), true))
                .allowPasscodeModification(toBool(map.get("allowPasscodeModification"), true))
                .allowPassbookWhileLocked(toBool(map.get("allowPassbookWhileLocked"), true))
                .allowPasswordAutoFill(toBool(map.get("allowPasswordAutoFill"), true))
                .allowPasswordProximityRequests(toBool(map.get("allowPasswordProximityRequests"), true))
                .allowPasswordSharing(toBool(map.get("allowPasswordSharing"), true))
                .allowPersonalHotspotModification(toBool(map.get("allowPersonalHotspotModification"), true))
                .allowPersonalizedHandwritingResults(toBool(map.get("allowPersonalizedHandwritingResults"), true))
                .allowPodcasts(toBool(map.get("allowPodcasts"), true))
                .allowPredictiveKeyboard(toBool(map.get("allowPredictiveKeyboard"), true))
                .allowRadioService(toBool(map.get("allowRadioService"), true))
                .allowRCSMessaging(toBool(map.get("allowRCSMessaging"), true))
                .allowRemoteScreenObservation(toBool(map.get("allowRemoteScreenObservation"), true))
                .allowSafari(toBool(map.get("allowSafari"), true))
                .allowScreenShot(toBool(map.get("allowScreenShot"), true))
                .allowSharedDeviceTemporarySession(toBool(map.get("allowSharedDeviceTemporarySession"), true))
                .allowSharedStream(toBool(map.get("allowSharedStream"), true))
                .allowSpellCheck(toBool(map.get("allowSpellCheck"), true))
                .allowSpotlightInternetResults(toBool(map.get("allowSpotlightInternetResults"), true))
                .allowSystemAppRemoval(toBool(map.get("allowSystemAppRemoval"), true))
                .allowUIAppInstallation(toBool(map.get("allowUIAppInstallation"), true))
                .allowUIConfigurationProfileInstallation(toBool(map.get("allowUIConfigurationProfileInstallation"), true))
                .allowUntrustedTLSPrompt(toBool(map.get("allowUntrustedTLSPrompt"), true))
                .allowUSBRestrictedMode(toBool(map.get("allowUSBRestrictedMode"), false))
                .allowVideoConferencing(toBool(map.get("allowVideoConferencing"), true))
                .allowVPNCreation(toBool(map.get("allowVPNCreation"), true))
                .allowWallpaperModification(toBool(map.get("allowWallpaperModification"), true))
                .allowWebDistributionAppInstallation(toBool(map.get("allowWebDistributionAppInstallation"), true))
                .allowWritingTools(toBool(map.get("allowWritingTools"), true))
                .forceAirPrintTrustedTLSRequirement(toBool(map.get("forceAirPrintTrustedTLSRequirement"), false))
                .forceAssistantProfanityFilter(toBool(map.get("forceAssistantProfanityFilter"), false))
                .forceAutomaticDateAndTime(toBool(map.get("forceAutomaticDateAndTime"), false))
                .forceEncryptedBackup(toBool(map.get("forceEncryptedBackup"), false))
                .forceLimitAdTracking(toBool(map.get("forceLimitAdTracking"), false))
                .forcePreserveESIMOnErase(toBool(map.get("forcePreserveESIMOnErase"), false))
                .forceWiFiToAllowedNetworksOnly(toBool(map.get("forceWiFiToAllowedNetworksOnly"), false))
                .forceWiFiPowerOn(toBool(map.get("forceWiFiPowerOn"), false))
                .ratingApps(toInt(map.get("ratingApps"), 1000))
                .ratingMovies(toInt(map.get("ratingMovies"), 1000))
                .ratingRegion((String) map.getOrDefault("ratingRegion", "us"))
                .ratingTVShows(toInt(map.get("ratingTVShows"), 1000))
                .safariAcceptCookies(toInt(map.get("safariAcceptCookies"), 2))
                .safariAllowAutoFill(toBool(map.get("safariAllowAutoFill"), true))
                .safariAllowJavaScript(toBool(map.get("safariAllowJavaScript"), true))
                .safariAllowPopups(toBool(map.get("safariAllowPopups"), true))
                .safariForceFraudWarning(toBool(map.get("safariForceFraudWarning"), false))
                .allowPhotoStream(toBool(map.get("allowPhotoStream"), true))
                .autonomousSingleAppModePermittedAppIDs((List<String>) map.getOrDefault("autonomousSingleAppModePermittedAppIDs", List.of()))
                .blockedAppBundleIDs((List<String>) map.getOrDefault("blockedAppBundleIDs", List.of()))
                .allowListedAppBundleIDs((List<String>) map.getOrDefault("allowListedAppBundleIDs", List.of()))
                // Additional Apple Doc Fields
                .forceBypassScreenCaptureAlert(toBool(map.get("forceBypassScreenCaptureAlert"), false))
                .allowAirPlayIncomingRequests(toBool(map.get("allowAirPlayIncomingRequests"), true))
                .allowAppClips(toBool(map.get("allowAppClips"), true))
                .allowAppleIntelligenceReport(toBool(map.get("allowAppleIntelligenceReport"), true))
                .allowApplePersonalizedAdvertising(toBool(map.get("allowApplePersonalizedAdvertising"), true))
                .allowARDRemoteManagementModification(toBool(map.get("allowARDRemoteManagementModification"), true))
                .allowAutomaticScreenSaver(toBool(map.get("allowAutomaticScreenSaver"), true))
                .allowAutoUnlock(toBool(map.get("allowAutoUnlock"), true))
                .allowBluetoothSharingModification(toBool(map.get("allowBluetoothSharingModification"), true))
                .allowCloudAddressBook(toBool(map.get("allowCloudAddressBook"), true))
                .allowCloudBookmarks(toBool(map.get("allowCloudBookmarks"), true))
                .allowCloudCalendar(toBool(map.get("allowCloudCalendar"), true))
                .allowCloudDesktopAndDocuments(toBool(map.get("allowCloudDesktopAndDocuments"), true))
                .allowCloudFreeform(toBool(map.get("allowCloudFreeform"), true))
                .allowCloudKeychainSync(toBool(map.get("allowCloudKeychainSync"), true))
                .allowCloudMail(toBool(map.get("allowCloudMail"), true))
                .allowCloudNotes(toBool(map.get("allowCloudNotes"), true))
                .allowCloudPrivateRelay(toBool(map.get("allowCloudPrivateRelay"), true))
                .allowCloudReminders(toBool(map.get("allowCloudReminders"), true))
                .allowContentCaching(toBool(map.get("allowContentCaching"), true))
                .allowDefaultCallingAppModification(toBool(map.get("allowDefaultCallingAppModification"), true))
                .allowDefaultMessagingAppModification(toBool(map.get("allowDefaultMessagingAppModification"), true))
                .allowDeviceSleep(toBool(map.get("allowDeviceSleep"), true))
                .allowDictation(toBool(map.get("allowDictation"), true))
                .allowExternalIntelligenceIntegrations(toBool(map.get("allowExternalIntelligenceIntegrations"), true))
                .allowExternalIntelligenceIntegrationsSignIn(toBool(map.get("allowExternalIntelligenceIntegrationsSignIn"), true))
                .allowFileSharingModification(toBool(map.get("allowFileSharingModification"), true))
                .allowFindMyDevice(toBool(map.get("allowFindMyDevice"), true))
                .allowInternetSharingModification(toBool(map.get("allowInternetSharingModification"), true))
                .allowiPhoneWidgetsOnMac(toBool(map.get("allowiPhoneWidgetsOnMac"), true))
                .allowiTunesFileSharing(toBool(map.get("allowiTunesFileSharing"), true))
                .allowLocalUserCreation(toBool(map.get("allowLocalUserCreation"), true))
                .allowMailPrivacyProtection(toBool(map.get("allowMailPrivacyProtection"), true))
                .allowMailSmartReplies(toBool(map.get("allowMailSmartReplies"), true))
                .allowManagedToWriteUnmanagedContacts(toBool(map.get("allowManagedToWriteUnmanagedContacts"), false))
                .allowMediaSharingModification(toBool(map.get("allowMediaSharingModification"), true))
                .allowNFC(toBool(map.get("allowNFC"), true))
                .allowNotesTranscription(toBool(map.get("allowNotesTranscription"), true))
                .allowNotesTranscriptionSummary(toBool(map.get("allowNotesTranscriptionSummary"), true))
                .allowOpenFromManagedToUnmanaged(toBool(map.get("allowOpenFromManagedToUnmanaged"), true))
                .allowOpenFromUnmanagedToManaged(toBool(map.get("allowOpenFromUnmanagedToManaged"), true))
                .allowPrinterSharingModification(toBool(map.get("allowPrinterSharingModification"), true))
                .allowProximitySetupToNewDevice(toBool(map.get("allowProximitySetupToNewDevice"), true))
                .allowRapidSecurityResponseInstallation(toBool(map.get("allowRapidSecurityResponseInstallation"), true))
                .allowRapidSecurityResponseRemoval(toBool(map.get("allowRapidSecurityResponseRemoval"), true))
                .allowRemoteAppleEventsModification(toBool(map.get("allowRemoteAppleEventsModification"), true))
                .allowRemoteAppPairing(toBool(map.get("allowRemoteAppPairing"), true))
                .allowSafariSummary(toBool(map.get("allowSafariSummary"), true))
                .allowSatelliteConnection(toBool(map.get("allowSatelliteConnection"), true))
                .allowStartupDiskModification(toBool(map.get("allowStartupDiskModification"), true))
                .allowTimeMachineBackup(toBool(map.get("allowTimeMachineBackup"), true))
                .allowUniversalControl(toBool(map.get("allowUniversalControl"), true))
                .allowUnmanagedToReadManagedContacts(toBool(map.get("allowUnmanagedToReadManagedContacts"), false))
                .allowUnpairedExternalBootToRecovery(toBool(map.get("allowUnpairedExternalBootToRecovery"), true))
                .allowVisualIntelligenceSummary(toBool(map.get("allowVisualIntelligenceSummary"), true))
                .allowedExternalIntelligenceWorkspaceIDs((List<String>) map.getOrDefault("allowedExternalIntelligenceWorkspaceIDs", List.of()))
                .enforcedFingerprintTimeout(toInt(map.get("enforcedFingerprintTimeout"), 48))
                .enforcedSoftwareUpdateDelay(toInt(map.get("enforcedSoftwareUpdateDelay"), 30))
                .enforcedSoftwareUpdateMinorOSDeferredInstallDelay(toInt(map.get("enforcedSoftwareUpdateMinorOSDeferredInstallDelay"), 30))
                .enforcedSoftwareUpdateMajorOSDeferredInstallDelay(toInt(map.get("enforcedSoftwareUpdateMajorOSDeferredInstallDelay"), 30))
                .enforcedSoftwareUpdateNonOSDeferredInstallDelay(toInt(map.get("enforcedSoftwareUpdateNonOSDeferredInstallDelay"), 30))
                .forceAirDropUnmanaged(toBool(map.get("forceAirDropUnmanaged"), false))
                .forceAirPlayIncomingRequestsPairingPassword(toBool(map.get("forceAirPlayIncomingRequestsPairingPassword"), false))
                .forceAirPlayOutgoingRequestsPairingPassword(toBool(map.get("forceAirPlayOutgoingRequestsPairingPassword"), false))
                .forceAuthenticationBeforeAutoFill(toBool(map.get("forceAuthenticationBeforeAutoFill"), false))
                .forceClassroomAutomaticallyJoinClasses(toBool(map.get("forceClassroomAutomaticallyJoinClasses"), false))
                .forceClassroomRequestPermissionToLeaveClasses(toBool(map.get("forceClassroomRequestPermissionToLeaveClasses"), false))
                .forceClassroomUnpromptedAppAndDeviceLock(toBool(map.get("forceClassroomUnpromptedAppAndDeviceLock"), false))
                .forceClassroomUnpromptedScreenObservation(toBool(map.get("forceClassroomUnpromptedScreenObservation"), false))
                .forceDelayedAppSoftwareUpdates(toBool(map.get("forceDelayedAppSoftwareUpdates"), false))
                .forceDelayedMajorSoftwareUpdates(toBool(map.get("forceDelayedMajorSoftwareUpdates"), false))
                .forceDelayedSoftwareUpdates(toBool(map.get("forceDelayedSoftwareUpdates"), false))
                .forceOnDeviceOnlyDictation(toBool(map.get("forceOnDeviceOnlyDictation"), false))
                .forceOnDeviceOnlyTranslation(toBool(map.get("forceOnDeviceOnlyTranslation"), false))
                .forceWatchWristDetection(toBool(map.get("forceWatchWristDetection"), false))
                .requireManagedPasteboard(toBool(map.get("requireManagedPasteboard"), false))
                .build();

        restrictions.setPayloadIdentifier(String.format("policy_restrictions-%s", map.get("policyId")));
        restrictions.setPayloadType("com.apple.applicationaccess");
        restrictions.setPayloadUUID(UUID.randomUUID().toString());
        restrictions.setPayloadVersion(1);
        restrictions.setPayloadRemovalDisallowed(true);

        return restrictions;
    }


    public NSDictionary createPayload() {

        NSDictionary dictionary = new NSDictionary();
        dictionary.put("allowPhotoStream", this.allowPhotoStream);
        dictionary.put("forceITunesStorePasswordEntry", this.forceITunesStorePasswordEntry);
        dictionary.put("allowVoiceDialing", this.allowVoiceDialing);
        dictionary.put("allowAccountModification", this.isAllowAccountModification());
        dictionary.put("allowActivityContinuation", this.isAllowActivityContinuation());
        dictionary.put("allowAddingGameCenterFriends", this.isAllowAddingGameCenterFriends());
        dictionary.put("allowAirDrop", this.isAllowAirDrop());
        dictionary.put("allowAirPrint", this.isAllowAirPrint());
        dictionary.put("allowAirPrintCredentialsStorage", this.isAllowAirPrintCredentialsStorage());
        dictionary.put("allowAirPrintiBeaconDiscovery", this.isAllowAirPrintiBeaconDiscovery());
        dictionary.put("allowAppCellularDataModification", this.isAllowAppCellularDataModification());
        dictionary.put("allowAppInstallation", this.isAllowAppInstallation());
        dictionary.put("allowAppRemoval", this.isAllowAppRemoval());
        dictionary.put("allowAppsToBeHidden", this.isAllowAppsToBeHidden());
        dictionary.put("allowAppsToBeLocked", this.isAllowAppsToBeLocked());
        dictionary.put("allowAssistant", this.isAllowAssistant());
        dictionary.put("allowAssistantUserGeneratedContent", this.isAllowAssistantUserGeneratedContent());
        dictionary.put("allowAssistantWhileLocked", this.isAllowAssistantWhileLocked());
        dictionary.put("allowAutoCorrection", this.isAllowAutoCorrection());
        dictionary.put("allowAutoDim", this.isAllowAutoDim());
        dictionary.put("allowAutomaticAppDownloads", this.isAllowAutomaticAppDownloads());
        dictionary.put("allowBluetoothModification", this.isAllowBluetoothModification());
        dictionary.put("allowBookstore", this.isAllowBookstore());
        dictionary.put("allowBookstoreErotica", this.isAllowBookstoreErotica());
        dictionary.put("allowCallRecording", this.isAllowCallRecording());
        dictionary.put("allowCamera", this.isAllowCamera());
        dictionary.put("allowCellularPlanModification", this.isAllowCellularPlanModification());
        dictionary.put("allowChat", this.isAllowChat());
        dictionary.put("allowCloudBackup", this.isAllowCloudBackup());
        dictionary.put("allowCloudDocumentSync", this.isAllowCloudDocumentSync());
        dictionary.put("allowCloudPhotoLibrary", this.isAllowCloudPhotoLibrary());
        dictionary.put("allowContinuousPathKeyboard", this.isAllowContinuousPathKeyboard());
        dictionary.put("allowDefaultBrowserModification", this.isAllowDefaultBrowserModification());
        dictionary.put("allowDefinitionLookup", this.isAllowDefinitionLookup());
        dictionary.put("allowDeviceNameModification", this.isAllowDeviceNameModification());
        dictionary.put("allowDiagnosticSubmission", this.isAllowDiagnosticSubmission());
        dictionary.put("allowDiagnosticSubmissionModification", this.isAllowDiagnosticSubmissionModification());
        dictionary.put("allowEnablingRestrictions", this.isAllowEnablingRestrictions());
        dictionary.put("allowEnterpriseAppTrust", this.isAllowEnterpriseAppTrust());
        dictionary.put("allowEnterpriseBookBackup", this.isAllowEnterpriseBookBackup());
        dictionary.put("allowEnterpriseBookMetadataSync", this.isAllowEnterpriseBookMetadataSync());
        dictionary.put("allowEraseContentAndSettings", this.isAllowEraseContentAndSettings());
        dictionary.put("allowESIMModification", this.isAllowESIMModification());
        dictionary.put("allowESIMOutgoingTransfers", this.isAllowESIMOutgoingTransfers());
        dictionary.put("allowExplicitContent", this.isAllowExplicitContent());
        dictionary.put("allowFilesNetworkDriveAccess", this.isAllowFilesNetworkDriveAccess());
        dictionary.put("allowFilesUSBDriveAccess", this.isAllowFilesUSBDriveAccess());
        dictionary.put("allowFindMyFriends", this.isAllowFindMyFriends());
        dictionary.put("allowFindMyFriendsModification", this.isAllowFindMyFriendsModification());
        dictionary.put("allowFingerprintForUnlock", this.isAllowFingerprintForUnlock());
        dictionary.put("allowFingerprintModification", this.isAllowFingerprintModification());
        dictionary.put("allowGameCenter", this.isAllowGameCenter());
        dictionary.put("allowGenmoji", this.isAllowGenmoji());
        dictionary.put("allowGlobalBackgroundFetchWhenRoaming", this.isAllowGlobalBackgroundFetchWhenRoaming());
        dictionary.put("allowHostPairing", this.isAllowHostPairing());
        dictionary.put("allowImagePlayground", this.isAllowImagePlayground());
        dictionary.put("allowImageWand", this.isAllowImageWand());
        dictionary.put("allowInAppPurchases", this.isAllowInAppPurchases());
        dictionary.put("allowiPhoneMirroring", this.isAllowiPhoneMirroring());
        dictionary.put("allowiTunes", this.isAllowiTunes());
        dictionary.put("allowKeyboardShortcuts", this.isAllowKeyboardShortcuts());
        dictionary.put("allowLiveVoicemail", this.isAllowLiveVoicemail());
        dictionary.put("allowLockScreenControlCenter", this.isAllowLockScreenControlCenter());
        dictionary.put("allowLockScreenNotificationsView", this.isAllowLockScreenNotificationsView());
        dictionary.put("allowLockScreenTodayView", this.isAllowLockScreenTodayView());
        dictionary.put("allowMailSummary", this.isAllowMailSummary());
        dictionary.put("allowManagedAppsCloudSync", this.isAllowManagedAppsCloudSync());
        dictionary.put("allowMarketplaceAppInstallation", this.isAllowMarketplaceAppInstallation());
        dictionary.put("allowMultiplayerGaming", this.isAllowMultiplayerGaming());
        dictionary.put("allowMusicService", this.isAllowMusicService());
        dictionary.put("allowNews", this.isAllowNews());
        dictionary.put("allowNotificationsModification", this.isAllowNotificationsModification());
        dictionary.put("allowOTAPKIUpdates", this.isAllowOTAPKIUpdates());
        dictionary.put("allowPairedWatch", this.isAllowPairedWatch());
        dictionary.put("allowPasscodeModification", this.isAllowPasscodeModification());
        dictionary.put("allowPassbookWhileLocked", this.isAllowPassbookWhileLocked());
        dictionary.put("allowPasswordAutoFill", this.isAllowPasswordAutoFill());
        dictionary.put("allowPasswordProximityRequests", this.isAllowPasswordProximityRequests());
        dictionary.put("allowPasswordSharing", this.isAllowPasswordSharing());
        dictionary.put("allowPersonalHotspotModification", this.isAllowPersonalHotspotModification());
        dictionary.put("allowPersonalizedHandwritingResults", this.isAllowPersonalizedHandwritingResults());
        dictionary.put("allowPodcasts", this.isAllowPodcasts());
        dictionary.put("allowPredictiveKeyboard", this.isAllowPredictiveKeyboard());
        dictionary.put("allowRadioService", this.isAllowRadioService());
        dictionary.put("allowRCSMessaging", this.isAllowRCSMessaging());
        dictionary.put("allowRemoteScreenObservation", this.isAllowRemoteScreenObservation());
        dictionary.put("allowSafari", this.isAllowSafari());
        dictionary.put("allowScreenShot", this.isAllowScreenShot());
        dictionary.put("allowSharedDeviceTemporarySession", this.isAllowSharedDeviceTemporarySession());
        dictionary.put("allowSharedStream", this.isAllowSharedStream());
        dictionary.put("allowSpellCheck", this.isAllowSpellCheck());
        dictionary.put("allowSpotlightInternetResults", this.isAllowSpotlightInternetResults());
        dictionary.put("allowSystemAppRemoval", this.isAllowSystemAppRemoval());
        dictionary.put("allowUIAppInstallation", this.isAllowUIAppInstallation());
        dictionary.put("allowUIConfigurationProfileInstallation", this.isAllowUIConfigurationProfileInstallation());
        dictionary.put("allowUntrustedTLSPrompt", this.isAllowUntrustedTLSPrompt());
        dictionary.put("allowUSBRestrictedMode", this.isAllowUSBRestrictedMode());
        dictionary.put("allowVideoConferencing", this.isAllowVideoConferencing());
        dictionary.put("allowVPNCreation", this.isAllowVPNCreation());
        dictionary.put("allowWallpaperModification", this.isAllowWallpaperModification());
        dictionary.put("allowWebDistributionAppInstallation", this.isAllowWebDistributionAppInstallation());
        dictionary.put("allowWritingTools", this.isAllowWritingTools());
        dictionary.put("forceAirPrintTrustedTLSRequirement", this.isForceAirPrintTrustedTLSRequirement());
        dictionary.put("forceAssistantProfanityFilter", this.isForceAssistantProfanityFilter());
        dictionary.put("forceAutomaticDateAndTime", this.isForceAutomaticDateAndTime());
        dictionary.put("forceEncryptedBackup", this.isForceEncryptedBackup());
        dictionary.put("forceLimitAdTracking", this.isForceLimitAdTracking());
        dictionary.put("forcePreserveESIMOnErase", this.isForcePreserveESIMOnErase());
        dictionary.put("forceWiFiToAllowedNetworksOnly", this.isForceWiFiToAllowedNetworksOnly());
        dictionary.put("forceWiFiPowerOn", this.isForceWiFiPowerOn());
        dictionary.put("ratingApps", this.getRatingApps());
        dictionary.put("ratingMovies", this.getRatingMovies());
        dictionary.put("ratingRegion", this.getRatingRegion());
        dictionary.put("ratingTVShows", this.getRatingTVShows());
        dictionary.put("safariAcceptCookies", this.getSafariAcceptCookies());
        dictionary.put("safariAllowAutoFill", this.isSafariAllowAutoFill());
        dictionary.put("safariAllowJavaScript", this.isSafariAllowJavaScript());
        dictionary.put("safariAllowPopups", this.isSafariAllowPopups());
        dictionary.put("safariForceFraudWarning", this.isSafariForceFraudWarning());

        // Additional Apple Doc Fields
        dictionary.put("forceBypassScreenCaptureAlert", this.isForceBypassScreenCaptureAlert());
        dictionary.put("allowAirPlayIncomingRequests", this.isAllowAirPlayIncomingRequests());
        dictionary.put("allowAppClips", this.isAllowAppClips());
        dictionary.put("allowAppleIntelligenceReport", this.isAllowAppleIntelligenceReport());
        dictionary.put("allowApplePersonalizedAdvertising", this.isAllowApplePersonalizedAdvertising());
        dictionary.put("allowARDRemoteManagementModification", this.isAllowARDRemoteManagementModification());
        dictionary.put("allowAutomaticScreenSaver", this.isAllowAutomaticScreenSaver());
        dictionary.put("allowAutoUnlock", this.isAllowAutoUnlock());
        dictionary.put("allowBluetoothSharingModification", this.isAllowBluetoothSharingModification());
        dictionary.put("allowCloudAddressBook", this.isAllowCloudAddressBook());
        dictionary.put("allowCloudBookmarks", this.isAllowCloudBookmarks());
        dictionary.put("allowCloudCalendar", this.isAllowCloudCalendar());
        dictionary.put("allowCloudDesktopAndDocuments", this.isAllowCloudDesktopAndDocuments());
        dictionary.put("allowCloudFreeform", this.isAllowCloudFreeform());
        dictionary.put("allowCloudKeychainSync", this.isAllowCloudKeychainSync());
        dictionary.put("allowCloudMail", this.isAllowCloudMail());
        dictionary.put("allowCloudNotes", this.isAllowCloudNotes());
        dictionary.put("allowCloudPrivateRelay", this.isAllowCloudPrivateRelay());
        dictionary.put("allowCloudReminders", this.isAllowCloudReminders());
        dictionary.put("allowContentCaching", this.isAllowContentCaching());
        dictionary.put("allowDefaultCallingAppModification", this.isAllowDefaultCallingAppModification());
        dictionary.put("allowDefaultMessagingAppModification", this.isAllowDefaultMessagingAppModification());
        dictionary.put("allowDeviceSleep", this.isAllowDeviceSleep());
        dictionary.put("allowDictation", this.isAllowDictation());
        dictionary.put("allowExternalIntelligenceIntegrations", this.isAllowExternalIntelligenceIntegrations());
        dictionary.put("allowExternalIntelligenceIntegrationsSignIn", this.isAllowExternalIntelligenceIntegrationsSignIn());
        dictionary.put("allowFileSharingModification", this.isAllowFileSharingModification());
        dictionary.put("allowFindMyDevice", this.isAllowFindMyDevice());
        dictionary.put("allowInternetSharingModification", this.isAllowInternetSharingModification());
        dictionary.put("allowiPhoneWidgetsOnMac", this.isAllowiPhoneWidgetsOnMac());
        dictionary.put("allowiTunesFileSharing", this.isAllowiTunesFileSharing());
        dictionary.put("allowLocalUserCreation", this.isAllowLocalUserCreation());
        dictionary.put("allowMailPrivacyProtection", this.isAllowMailPrivacyProtection());
        dictionary.put("allowMailSmartReplies", this.isAllowMailSmartReplies());
        dictionary.put("allowManagedToWriteUnmanagedContacts", this.isAllowManagedToWriteUnmanagedContacts());
        dictionary.put("allowMediaSharingModification", this.isAllowMediaSharingModification());
        dictionary.put("allowNFC", this.isAllowNFC());
        dictionary.put("allowNotesTranscription", this.isAllowNotesTranscription());
        dictionary.put("allowNotesTranscriptionSummary", this.isAllowNotesTranscriptionSummary());
        dictionary.put("allowOpenFromManagedToUnmanaged", this.isAllowOpenFromManagedToUnmanaged());
        dictionary.put("allowOpenFromUnmanagedToManaged", this.isAllowOpenFromUnmanagedToManaged());
        dictionary.put("allowPrinterSharingModification", this.isAllowPrinterSharingModification());
        dictionary.put("allowProximitySetupToNewDevice", this.isAllowProximitySetupToNewDevice());
        dictionary.put("allowRapidSecurityResponseInstallation", this.isAllowRapidSecurityResponseInstallation());
        dictionary.put("allowRapidSecurityResponseRemoval", this.isAllowRapidSecurityResponseRemoval());
        dictionary.put("allowRemoteAppleEventsModification", this.isAllowRemoteAppleEventsModification());
        dictionary.put("allowRemoteAppPairing", this.isAllowRemoteAppPairing());
        dictionary.put("allowSafariSummary", this.isAllowSafariSummary());
        dictionary.put("allowSatelliteConnection", this.isAllowSatelliteConnection());
        dictionary.put("allowStartupDiskModification", this.isAllowStartupDiskModification());
        dictionary.put("allowTimeMachineBackup", this.isAllowTimeMachineBackup());
        dictionary.put("allowUniversalControl", this.isAllowUniversalControl());
        dictionary.put("allowUnmanagedToReadManagedContacts", this.isAllowUnmanagedToReadManagedContacts());
        dictionary.put("allowUnpairedExternalBootToRecovery", this.isAllowUnpairedExternalBootToRecovery());
        dictionary.put("allowVisualIntelligenceSummary", this.isAllowVisualIntelligenceSummary());
        dictionary.put("enforcedFingerprintTimeout", this.getEnforcedFingerprintTimeout());
        dictionary.put("enforcedSoftwareUpdateDelay", this.getEnforcedSoftwareUpdateDelay());
        dictionary.put("enforcedSoftwareUpdateMinorOSDeferredInstallDelay", this.getEnforcedSoftwareUpdateMinorOSDeferredInstallDelay());
        dictionary.put("enforcedSoftwareUpdateMajorOSDeferredInstallDelay", this.getEnforcedSoftwareUpdateMajorOSDeferredInstallDelay());
        dictionary.put("enforcedSoftwareUpdateNonOSDeferredInstallDelay", this.getEnforcedSoftwareUpdateNonOSDeferredInstallDelay());
        dictionary.put("forceAirDropUnmanaged", this.isForceAirDropUnmanaged());
        dictionary.put("forceAirPlayIncomingRequestsPairingPassword", this.isForceAirPlayIncomingRequestsPairingPassword());
        dictionary.put("forceAirPlayOutgoingRequestsPairingPassword", this.isForceAirPlayOutgoingRequestsPairingPassword());
        dictionary.put("forceAuthenticationBeforeAutoFill", this.isForceAuthenticationBeforeAutoFill());
        dictionary.put("forceClassroomAutomaticallyJoinClasses", this.isForceClassroomAutomaticallyJoinClasses());
        dictionary.put("forceClassroomRequestPermissionToLeaveClasses", this.isForceClassroomRequestPermissionToLeaveClasses());
        dictionary.put("forceClassroomUnpromptedAppAndDeviceLock", this.isForceClassroomUnpromptedAppAndDeviceLock());
        dictionary.put("forceClassroomUnpromptedScreenObservation", this.isForceClassroomUnpromptedScreenObservation());
        dictionary.put("forceDelayedAppSoftwareUpdates", this.isForceDelayedAppSoftwareUpdates());
        dictionary.put("forceDelayedMajorSoftwareUpdates", this.isForceDelayedMajorSoftwareUpdates());
        dictionary.put("forceDelayedSoftwareUpdates", this.isForceDelayedSoftwareUpdates());
        dictionary.put("forceOnDeviceOnlyDictation", this.isForceOnDeviceOnlyDictation());
        dictionary.put("forceOnDeviceOnlyTranslation", this.isForceOnDeviceOnlyTranslation());
        dictionary.put("forceWatchWristDetection", this.isForceWatchWristDetection());
        dictionary.put("requireManagedPasteboard", this.isRequireManagedPasteboard());

        if (this.allowedExternalIntelligenceWorkspaceIDs != null && !this.allowedExternalIntelligenceWorkspaceIDs.isEmpty()) {
            dictionary.put("allowedExternalIntelligenceWorkspaceIDs", this.getAllowedExternalIntelligenceWorkspaceIDs().toArray(new String[0]));
        }

        if (this.allowListedAppBundleIDs != null && !this.allowListedAppBundleIDs.isEmpty()) {
            dictionary.put("allowListedAppBundleIDs", this.getAllowListedAppBundleIDs().toArray(new String[0]));
        }
        if (this.autonomousSingleAppModePermittedAppIDs != null && !this.autonomousSingleAppModePermittedAppIDs.isEmpty()) {
            dictionary.put("autonomousSingleAppModePermittedAppIDs", this.getAutonomousSingleAppModePermittedAppIDs().toArray(new String[0]));
        }
        if (this.blockedAppBundleIDs != null && !this.blockedAppBundleIDs.isEmpty()) {
            dictionary.put("blockedAppBundleIDs", this.getBlockedAppBundleIDs().toArray(new String[0]));
        }

        addPayloadEnvelope(dictionary);

        return dictionary;
    }

    protected Map<String, Object> buildAllKeyValues() {
        Map<String, Object> m = new LinkedHashMap<>();

        m.put("allowPhotoStream", this.allowPhotoStream);
        m.put("forceITunesStorePasswordEntry", this.forceITunesStorePasswordEntry);
        m.put("allowVoiceDialing", this.allowVoiceDialing);
        m.put("allowAccountModification", this.allowAccountModification);
        m.put("allowActivityContinuation", this.allowActivityContinuation);
        m.put("allowAddingGameCenterFriends", this.allowAddingGameCenterFriends);
        m.put("allowAirDrop", this.allowAirDrop);
        m.put("allowAirPrint", this.allowAirPrint);
        m.put("allowAirPrintCredentialsStorage", this.allowAirPrintCredentialsStorage);
        m.put("allowAirPrintiBeaconDiscovery", this.allowAirPrintiBeaconDiscovery);
        m.put("allowAppCellularDataModification", this.allowAppCellularDataModification);
        m.put("allowAppInstallation", this.allowAppInstallation);
        m.put("allowAppRemoval", this.allowAppRemoval);
        m.put("allowAppsToBeHidden", this.allowAppsToBeHidden);
        m.put("allowAppsToBeLocked", this.allowAppsToBeLocked);
        m.put("allowAssistant", this.allowAssistant);
        m.put("allowAssistantUserGeneratedContent", this.allowAssistantUserGeneratedContent);
        m.put("allowAssistantWhileLocked", this.allowAssistantWhileLocked);
        m.put("allowAutoCorrection", this.allowAutoCorrection);
        m.put("allowAutoDim", this.allowAutoDim);
        m.put("allowAutomaticAppDownloads", this.allowAutomaticAppDownloads);
        m.put("allowBluetoothModification", this.allowBluetoothModification);
        m.put("allowBookstore", this.allowBookstore);
        m.put("allowBookstoreErotica", this.allowBookstoreErotica);
        m.put("allowCallRecording", this.allowCallRecording);
        m.put("allowCamera", this.allowCamera);
        m.put("allowCellularPlanModification", this.allowCellularPlanModification);
        m.put("allowChat", this.allowChat);
        m.put("allowCloudBackup", this.allowCloudBackup);
        m.put("allowCloudDocumentSync", this.allowCloudDocumentSync);
        m.put("allowCloudPhotoLibrary", this.allowCloudPhotoLibrary);
        m.put("allowContinuousPathKeyboard", this.allowContinuousPathKeyboard);
        m.put("allowDefaultBrowserModification", this.allowDefaultBrowserModification);
        m.put("allowDefinitionLookup", this.allowDefinitionLookup);
        m.put("allowDeviceNameModification", this.allowDeviceNameModification);
        m.put("allowDiagnosticSubmission", this.allowDiagnosticSubmission);
        m.put("allowDiagnosticSubmissionModification", this.allowDiagnosticSubmissionModification);
        m.put("allowEnablingRestrictions", this.allowEnablingRestrictions);
        m.put("allowEnterpriseAppTrust", this.allowEnterpriseAppTrust);
        m.put("allowEnterpriseBookBackup", this.allowEnterpriseBookBackup);
        m.put("allowEnterpriseBookMetadataSync", this.allowEnterpriseBookMetadataSync);
        m.put("allowEraseContentAndSettings", this.allowEraseContentAndSettings);
        m.put("allowESIMModification", this.allowESIMModification);
        m.put("allowESIMOutgoingTransfers", this.allowESIMOutgoingTransfers);
        m.put("allowExplicitContent", this.allowExplicitContent);
        m.put("allowFilesNetworkDriveAccess", this.allowFilesNetworkDriveAccess);
        m.put("allowFilesUSBDriveAccess", this.allowFilesUSBDriveAccess);
        m.put("allowFindMyFriends", this.allowFindMyFriends);
        m.put("allowFindMyFriendsModification", this.allowFindMyFriendsModification);
        m.put("allowFingerprintForUnlock", this.allowFingerprintForUnlock);
        m.put("allowFingerprintModification", this.allowFingerprintModification);
        m.put("allowGameCenter", this.allowGameCenter);
        m.put("allowGenmoji", this.allowGenmoji);
        m.put("allowGlobalBackgroundFetchWhenRoaming", this.allowGlobalBackgroundFetchWhenRoaming);
        m.put("allowHostPairing", this.allowHostPairing);
        m.put("allowImagePlayground", this.allowImagePlayground);
        m.put("allowImageWand", this.allowImageWand);
        m.put("allowInAppPurchases", this.allowInAppPurchases);
        m.put("allowiPhoneMirroring", this.allowiPhoneMirroring);
        m.put("allowiTunes", this.allowiTunes);
        m.put("allowKeyboardShortcuts", this.allowKeyboardShortcuts);
        m.put("allowLiveVoicemail", this.allowLiveVoicemail);
        m.put("allowLockScreenControlCenter", this.allowLockScreenControlCenter);
        m.put("allowLockScreenNotificationsView", this.allowLockScreenNotificationsView);
        m.put("allowLockScreenTodayView", this.allowLockScreenTodayView);
        m.put("allowMailSummary", this.allowMailSummary);
        m.put("allowManagedAppsCloudSync", this.allowManagedAppsCloudSync);
        m.put("allowMarketplaceAppInstallation", this.allowMarketplaceAppInstallation);
        m.put("allowMultiplayerGaming", this.allowMultiplayerGaming);
        m.put("allowMusicService", this.allowMusicService);
        m.put("allowNews", this.allowNews);
        m.put("allowNotificationsModification", this.allowNotificationsModification);
        m.put("allowOTAPKIUpdates", this.allowOTAPKIUpdates);
        m.put("allowPairedWatch", this.allowPairedWatch);
        m.put("allowPasscodeModification", this.allowPasscodeModification);
        m.put("allowPassbookWhileLocked", this.allowPassbookWhileLocked);
        m.put("allowPasswordAutoFill", this.allowPasswordAutoFill);
        m.put("allowPasswordProximityRequests", this.allowPasswordProximityRequests);
        m.put("allowPasswordSharing", this.allowPasswordSharing);
        m.put("allowPersonalHotspotModification", this.allowPersonalHotspotModification);
        m.put("allowPersonalizedHandwritingResults", this.allowPersonalizedHandwritingResults);
        m.put("allowPodcasts", this.allowPodcasts);
        m.put("allowPredictiveKeyboard", this.allowPredictiveKeyboard);
        m.put("allowRadioService", this.allowRadioService);
        m.put("allowRCSMessaging", this.allowRCSMessaging);
        m.put("allowRemoteScreenObservation", this.allowRemoteScreenObservation);
        m.put("allowSafari", this.allowSafari);
        m.put("allowScreenShot", this.allowScreenShot);
        m.put("allowSharedDeviceTemporarySession", this.allowSharedDeviceTemporarySession);
        m.put("allowSharedStream", this.allowSharedStream);
        m.put("allowSpellCheck", this.allowSpellCheck);
        m.put("allowSpotlightInternetResults", this.allowSpotlightInternetResults);
        m.put("allowSystemAppRemoval", this.allowSystemAppRemoval);
        m.put("allowUIAppInstallation", this.allowUIAppInstallation);
        m.put("allowUIConfigurationProfileInstallation", this.allowUIConfigurationProfileInstallation);
        m.put("allowUntrustedTLSPrompt", this.allowUntrustedTLSPrompt);
        m.put("allowUSBRestrictedMode", this.allowUSBRestrictedMode);
        m.put("allowVideoConferencing", this.allowVideoConferencing);
        m.put("allowVPNCreation", this.allowVPNCreation);
        m.put("allowWallpaperModification", this.allowWallpaperModification);
        m.put("allowWebDistributionAppInstallation", this.allowWebDistributionAppInstallation);
        m.put("allowWritingTools", this.allowWritingTools);
        m.put("forceAirPrintTrustedTLSRequirement", this.forceAirPrintTrustedTLSRequirement);
        m.put("forceAssistantProfanityFilter", this.forceAssistantProfanityFilter);
        m.put("forceAutomaticDateAndTime", this.forceAutomaticDateAndTime);
        m.put("forceEncryptedBackup", this.forceEncryptedBackup);
        m.put("forceLimitAdTracking", this.forceLimitAdTracking);
        m.put("forcePreserveESIMOnErase", this.forcePreserveESIMOnErase);
        m.put("forceWiFiToAllowedNetworksOnly", this.forceWiFiToAllowedNetworksOnly);
        m.put("forceWiFiPowerOn", this.forceWiFiPowerOn);
        m.put("ratingApps", this.ratingApps);
        m.put("ratingMovies", this.ratingMovies);
        m.put("ratingRegion", this.ratingRegion);
        m.put("ratingTVShows", this.ratingTVShows);
        m.put("safariAcceptCookies", this.safariAcceptCookies);
        m.put("safariAllowAutoFill", this.safariAllowAutoFill);
        m.put("safariAllowJavaScript", this.safariAllowJavaScript);
        m.put("safariAllowPopups", this.safariAllowPopups);
        m.put("safariForceFraudWarning", this.safariForceFraudWarning);

        m.put("forceBypassScreenCaptureAlert", this.forceBypassScreenCaptureAlert);
        m.put("allowAirPlayIncomingRequests", this.allowAirPlayIncomingRequests);
        m.put("allowAppClips", this.allowAppClips);
        m.put("allowAppleIntelligenceReport", this.allowAppleIntelligenceReport);
        m.put("allowApplePersonalizedAdvertising", this.allowApplePersonalizedAdvertising);
        m.put("allowARDRemoteManagementModification", this.allowARDRemoteManagementModification);
        m.put("allowAutomaticScreenSaver", this.allowAutomaticScreenSaver);
        m.put("allowAutoUnlock", this.allowAutoUnlock);
        m.put("allowBluetoothSharingModification", this.allowBluetoothSharingModification);
        m.put("allowCloudAddressBook", this.allowCloudAddressBook);
        m.put("allowCloudBookmarks", this.allowCloudBookmarks);
        m.put("allowCloudCalendar", this.allowCloudCalendar);
        m.put("allowCloudDesktopAndDocuments", this.allowCloudDesktopAndDocuments);
        m.put("allowCloudFreeform", this.allowCloudFreeform);
        m.put("allowCloudKeychainSync", this.allowCloudKeychainSync);
        m.put("allowCloudMail", this.allowCloudMail);
        m.put("allowCloudNotes", this.allowCloudNotes);
        m.put("allowCloudPrivateRelay", this.allowCloudPrivateRelay);
        m.put("allowCloudReminders", this.allowCloudReminders);
        m.put("allowContentCaching", this.allowContentCaching);
        m.put("allowDefaultCallingAppModification", this.allowDefaultCallingAppModification);
        m.put("allowDefaultMessagingAppModification", this.allowDefaultMessagingAppModification);
        m.put("allowDeviceSleep", this.allowDeviceSleep);
        m.put("allowDictation", this.allowDictation);
        m.put("allowExternalIntelligenceIntegrations", this.allowExternalIntelligenceIntegrations);
        m.put("allowExternalIntelligenceIntegrationsSignIn", this.allowExternalIntelligenceIntegrationsSignIn);
        m.put("allowFileSharingModification", this.allowFileSharingModification);
        m.put("allowFindMyDevice", this.allowFindMyDevice);
        m.put("allowInternetSharingModification", this.allowInternetSharingModification);
        m.put("allowiPhoneWidgetsOnMac", this.allowiPhoneWidgetsOnMac);
        m.put("allowiTunesFileSharing", this.allowiTunesFileSharing);
        m.put("allowLocalUserCreation", this.allowLocalUserCreation);
        m.put("allowMailPrivacyProtection", this.allowMailPrivacyProtection);
        m.put("allowMailSmartReplies", this.allowMailSmartReplies);
        m.put("allowManagedToWriteUnmanagedContacts", this.allowManagedToWriteUnmanagedContacts);
        m.put("allowMediaSharingModification", this.allowMediaSharingModification);
        m.put("allowNFC", this.allowNFC);
        m.put("allowNotesTranscription", this.allowNotesTranscription);
        m.put("allowNotesTranscriptionSummary", this.allowNotesTranscriptionSummary);
        m.put("allowOpenFromManagedToUnmanaged", this.allowOpenFromManagedToUnmanaged);
        m.put("allowOpenFromUnmanagedToManaged", this.allowOpenFromUnmanagedToManaged);
        m.put("allowPrinterSharingModification", this.allowPrinterSharingModification);
        m.put("allowProximitySetupToNewDevice", this.allowProximitySetupToNewDevice);
        m.put("allowRapidSecurityResponseInstallation", this.allowRapidSecurityResponseInstallation);
        m.put("allowRapidSecurityResponseRemoval", this.allowRapidSecurityResponseRemoval);
        m.put("allowRemoteAppleEventsModification", this.allowRemoteAppleEventsModification);
        m.put("allowRemoteAppPairing", this.allowRemoteAppPairing);
        m.put("allowSafariSummary", this.allowSafariSummary);
        m.put("allowSatelliteConnection", this.allowSatelliteConnection);
        m.put("allowStartupDiskModification", this.allowStartupDiskModification);
        m.put("allowTimeMachineBackup", this.allowTimeMachineBackup);
        m.put("allowUniversalControl", this.allowUniversalControl);
        m.put("allowUnmanagedToReadManagedContacts", this.allowUnmanagedToReadManagedContacts);
        m.put("allowUnpairedExternalBootToRecovery", this.allowUnpairedExternalBootToRecovery);
        m.put("allowVisualIntelligenceSummary", this.allowVisualIntelligenceSummary);
        m.put("enforcedFingerprintTimeout", this.enforcedFingerprintTimeout);
        m.put("enforcedSoftwareUpdateDelay", this.enforcedSoftwareUpdateDelay);
        m.put("enforcedSoftwareUpdateMinorOSDeferredInstallDelay", this.enforcedSoftwareUpdateMinorOSDeferredInstallDelay);
        m.put("enforcedSoftwareUpdateMajorOSDeferredInstallDelay", this.enforcedSoftwareUpdateMajorOSDeferredInstallDelay);
        m.put("enforcedSoftwareUpdateNonOSDeferredInstallDelay", this.enforcedSoftwareUpdateNonOSDeferredInstallDelay);
        m.put("forceAirDropUnmanaged", this.forceAirDropUnmanaged);
        m.put("forceAirPlayIncomingRequestsPairingPassword", this.forceAirPlayIncomingRequestsPairingPassword);
        m.put("forceAirPlayOutgoingRequestsPairingPassword", this.forceAirPlayOutgoingRequestsPairingPassword);
        m.put("forceAuthenticationBeforeAutoFill", this.forceAuthenticationBeforeAutoFill);
        m.put("forceClassroomAutomaticallyJoinClasses", this.forceClassroomAutomaticallyJoinClasses);
        m.put("forceClassroomRequestPermissionToLeaveClasses", this.forceClassroomRequestPermissionToLeaveClasses);
        m.put("forceClassroomUnpromptedAppAndDeviceLock", this.forceClassroomUnpromptedAppAndDeviceLock);
        m.put("forceClassroomUnpromptedScreenObservation", this.forceClassroomUnpromptedScreenObservation);
        m.put("forceDelayedAppSoftwareUpdates", this.forceDelayedAppSoftwareUpdates);
        m.put("forceDelayedMajorSoftwareUpdates", this.forceDelayedMajorSoftwareUpdates);
        m.put("forceDelayedSoftwareUpdates", this.forceDelayedSoftwareUpdates);
        m.put("forceOnDeviceOnlyDictation", this.forceOnDeviceOnlyDictation);
        m.put("forceOnDeviceOnlyTranslation", this.forceOnDeviceOnlyTranslation);
        m.put("forceWatchWristDetection", this.forceWatchWristDetection);
        m.put("requireManagedPasteboard", this.requireManagedPasteboard);

        if (this.allowedExternalIntelligenceWorkspaceIDs != null && !this.allowedExternalIntelligenceWorkspaceIDs.isEmpty()) {
            m.put("allowedExternalIntelligenceWorkspaceIDs", this.allowedExternalIntelligenceWorkspaceIDs.toArray(new String[0]));
        }
        if (this.allowListedAppBundleIDs != null && !this.allowListedAppBundleIDs.isEmpty()) {
            m.put("allowListedAppBundleIDs", this.allowListedAppBundleIDs.toArray(new String[0]));
        }
        if (this.autonomousSingleAppModePermittedAppIDs != null && !this.autonomousSingleAppModePermittedAppIDs.isEmpty()) {
            m.put("autonomousSingleAppModePermittedAppIDs", this.autonomousSingleAppModePermittedAppIDs.toArray(new String[0]));
        }
        if (this.blockedAppBundleIDs != null && !this.blockedAppBundleIDs.isEmpty()) {
            m.put("blockedAppBundleIDs", this.blockedAppBundleIDs.toArray(new String[0]));
        }

        return m;
    }

    protected void putValue(NSDictionary dict, String key, Object value) {
        if (value instanceof String[] arr) {
            dict.put(key, arr);
        } else if (value instanceof Boolean b) {
            dict.put(key, b);
        } else if (value instanceof Integer i) {
            dict.put(key, i);
        } else if (value instanceof String s) {
            dict.put(key, s);
        }
    }

    protected void addPayloadEnvelope(NSDictionary dict) {
        dict.put("PayloadIdentifier", this.getPayloadIdentifier());
        dict.put("PayloadType", this.getPayloadType());
        dict.put("PayloadUUID", this.getPayloadUUID());
        dict.put("PayloadVersion", this.getPayloadVersion());
        dict.put("PayloadRemovalDisallowed", this.getPayloadRemovalDisallowed());
    }

    protected static void copyFields(Restrictions source, Restrictions target) {
        BeanUtils.copyProperties(source, target);
    }
}
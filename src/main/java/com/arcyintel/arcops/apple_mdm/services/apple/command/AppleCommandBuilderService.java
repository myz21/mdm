package com.arcyintel.arcops.apple_mdm.services.apple.command;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.models.profile.AppLock;

import java.io.IOException;
import java.util.List;

public interface AppleCommandBuilderService {
    NSDictionary deviceLock(String message, String phoneNumber, String commandUUID);

    NSDictionary queryDeviceInformation(String commandUUID);

    NSDictionary querySecurityInformation(String commandUUID);

    NSDictionary installApp(String identifier, boolean removable, String commandUUID);

    NSDictionary installApp(Integer identifier, boolean removable, String commandUUID);

    /**
     * Creates InstallApplication command for enterprise app installation via ManifestURL.
     * Used for in-house/enterprise apps that are not in the App Store.
     *
     * @param manifestUrl URL pointing to the enterprise app manifest plist
     * @param removable   whether the app can be removed by the user
     * @param commandUUID unique command identifier
     * @return NSDictionary command payload
     */
    NSDictionary installAppFromManifest(String manifestUrl, boolean removable, String commandUUID);

    /**
     * Creates InstallApplication command with managed app configuration.
     * The Configuration dict becomes the app's managed configuration
     * accessible via UserDefaults(suiteName: "com.apple.configuration.managed").
     *
     * @param manifestUrl    URL pointing to the enterprise app manifest plist
     * @param removable      whether the app can be removed by the user
     * @param commandUUID    unique command identifier
     * @param configuration  managed app configuration dictionary
     * @return NSDictionary command payload
     */
    NSDictionary installAppFromManifest(String manifestUrl, boolean removable, String commandUUID, NSDictionary configuration);

    /**
     * Creates InstallEnterpriseApplication command for macOS enterprise app installation via ManifestURL.
     * This command is only supported on macOS devices.
     *
     * @param manifestUrl URL pointing to the enterprise app manifest plist
     * @param commandUUID unique command identifier
     * @return NSDictionary command payload
     */
    NSDictionary installEnterpriseApp(String manifestUrl, String commandUUID);

    NSDictionary installProfile(String payload, String commandUUID) throws IOException;

    NSDictionary removeProfile(String identifier, String commandUUID);

    NSDictionary settings(List<NSDictionary> settings, String commandUUID);

    NSDictionary removeApplication(String identifier, String commandUUID);

    NSDictionary listInstalledProfiles(String commandUUID);

    NSDictionary singleAppMode(AppLock.SingleAppConfig singleAppConfig);

    NSDictionary declarativeManagement(String syncTokensBase64, String commandUUID) throws IOException;

    NSDictionary restartDevice(String commandUUID, Boolean notifyUser);

    NSDictionary shutDownDevice(String commandUUID);

    NSDictionary eraseDevice(String commandUUID, String pin, boolean preserveDataPlan);

    NSDictionary installedApplicationList(String commandUUID);

    NSDictionary managedApplicationList(String commandUUID);

    NSDictionary deviceConfigured(String commandUUID);

    NSDictionary clearPasscode(String unlockToken, String commandUUID);

    NSDictionary clearRestrictionsPassword(String commandUUID);

    NSDictionary enableLostMode(String message, String phoneNumber, String footnote, String commandUUID);

    NSDictionary disableLostMode(String commandUUID);

    NSDictionary deviceLocation(String commandUUID);

    NSDictionary playLostModeSound(String commandUUID);

    NSDictionary certificateList(String commandUUID);

    NSDictionary userList(String commandUUID);

    NSDictionary logOutUser(String commandUUID);

    NSDictionary deleteUser(String userName, boolean forceDeletion, String commandUUID);

    NSDictionary enableRemoteDesktop(String commandUUID);

    NSDictionary disableRemoteDesktop(String commandUUID);
}
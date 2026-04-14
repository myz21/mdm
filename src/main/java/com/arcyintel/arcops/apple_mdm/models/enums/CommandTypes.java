package com.arcyintel.arcops.apple_mdm.models.enums;

import lombok.Getter;

@Getter
public enum CommandTypes {

    DEVICE_LOCK_COMMAND("DeviceLock"),
    DEVICE_INFO_COMMAND("DeviceInformation"),
    DEVICE_INSTALL_PROFILE_COMMAND("InstallProfile"),
    DEVICE_INSTALLED_APPLICATION_LIST_COMMAND("InstalledApplicationList"),
    DEVICE_MANAGED_APPLICATION_LIST_COMMAND("ManagedApplicationList"),
    DEVICE_REMOVE_PROFILE_COMMAND("RemoveProfile"),
    DEVICE_LIST_INSTALLED_PROFILES_COMMAND("InstalledProfiles"),
    DEVICE_INSTALL_APP_COMMAND("InstallApplication"),
    DEVICE_INSTALL_ENTERPRISE_APP_COMMAND("InstallEnterpriseApplication"),
    DEVICE_REMOVE_APP_COMMAND("RemoveApplication"),
    DEVICE_SECURITY_INFO_COMMAND("SecurityInfo"),
    DEVICE_DECLARATIVE_MANAGEMENT_COMMAND("DeclarativeManagement"),
    DEVICE_SETTINGS_COMMAND("Settings"),
    DEVICE_RESTART_COMMAND("RestartDevice"),
    DEVICE_SHUTDOWN_COMMAND("ShutDownDevice"),
    DEVICE_ERASE_COMMAND("EraseDevice"),
    DEVICE_CONFIGURED_COMMAND("DeviceConfigured"),
    DEVICE_CLEAR_PASSCODE_COMMAND("ClearPasscode"),
    DEVICE_CLEAR_RESTRICTIONS_PASSWORD_COMMAND("ClearRestrictionsPassword"),
    DEVICE_ENABLE_LOST_MODE_COMMAND("EnableLostMode"),
    DEVICE_DISABLE_LOST_MODE_COMMAND("DisableLostMode"),
    DEVICE_LOCATION_COMMAND("DeviceLocation"),
    DEVICE_PLAY_LOST_MODE_SOUND_COMMAND("PlayLostModeSound"),
    DEVICE_USER_LIST_COMMAND("UserList"),
    DEVICE_LOG_OUT_USER_COMMAND("LogOutUser"),
    DEVICE_DELETE_USER_COMMAND("DeleteUser"),
    DEVICE_CERTIFICATE_LIST_COMMAND("CertificateList"),
    DEVICE_ENABLE_REMOTE_DESKTOP_COMMAND("EnableRemoteDesktop"),
    DEVICE_DISABLE_REMOTE_DESKTOP_COMMAND("DisableRemoteDesktop");


    private final String requestType;

    CommandTypes(String requestType) {
        this.requestType = requestType;
    }

}
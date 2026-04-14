package com.arcyintel.arcops.apple_mdm.services.apple.command;

import com.dd.plist.NSDictionary;

import java.util.List;
import java.util.UUID;

public interface AppleCommandSenderService {

    void installApp(String deviceUdid, Object identifier, boolean removable, boolean fromPolicy, UUID policyId) throws Exception;

    void removeApp(String deviceUdid, String identifier) throws Exception;

    void removeProfile(String deviceUdid, String identifier) throws Exception;

    void sendSettings(String deviceUdid, List<NSDictionary> settingsPayloads, boolean fromPolicy, UUID policyId) throws Exception;

    void queryDeviceInformation(String deviceUdid, boolean isSystem) throws Exception;

    void sendDeclarativeManagementCommand(String deviceUdid, String deterministicHash, UUID policyId) throws Exception;

    void restartDevice(String deviceUdid, Boolean notifyUser) throws Exception;

    void lockDevice(String deviceUdid, String message, String phoneNumber) throws Exception;

    void shutDownDevice(String deviceUdid) throws Exception;

    void eraseDevice(String deviceUdid, String pin, boolean preserveDataPlan) throws Exception;

    void syncAppInventory(String deviceUdid) throws Exception;

    void deviceConfigured(String deviceUdid) throws Exception;

    void securityInfo(String deviceUdid) throws Exception;

    void clearPasscode(String deviceUdid) throws Exception;

    void clearRestrictionsPassword(String deviceUdid) throws Exception;

    void enableLostMode(String deviceUdid, String message, String phoneNumber, String footnote) throws Exception;

    void requestDeviceLocation(String deviceUdid) throws Exception;

    void playLostModeSound(String deviceUdid) throws Exception;

    void disableLostMode(String deviceUdid) throws Exception;

    void renameDevice(String deviceUdid, String newName) throws Exception;

    void setBluetooth(String deviceUdid, boolean enabled) throws Exception;

    void setDataRoaming(String deviceUdid, boolean enabled) throws Exception;

    void setPersonalHotspot(String deviceUdid, boolean enabled) throws Exception;

    void setWallpaper(String deviceUdid, byte[] imageBytes, Integer where) throws Exception;

    void setAppAnalytics(String deviceUdid, boolean enabled) throws Exception;

    void setDiagnosticSubmission(String deviceUdid, boolean enabled) throws Exception;

    void setVoiceRoaming(String deviceUdid, boolean enabled) throws Exception;

    void setHostname(String deviceUdid, String hostname) throws Exception;

    void setTimeZone(String deviceUdid, String timeZoneName) throws Exception;

    void requestCertificateList(String deviceUdid) throws Exception;

    void requestUserList(String deviceUdid) throws Exception;

    void logOutUser(String deviceUdid) throws Exception;

    void deleteUser(String deviceUdid, String userName, boolean forceDeletion) throws Exception;
}
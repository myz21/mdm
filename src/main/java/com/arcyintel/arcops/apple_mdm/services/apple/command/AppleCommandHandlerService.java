package com.arcyintel.arcops.apple_mdm.services.apple.command;

import com.dd.plist.NSDictionary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface AppleCommandHandlerService {
    void updateDeviceInfo(String deviceUdid, HashMap<String, Object> deviceInformation);

    void handleCommandSuccess(String deviceUdid, String commandUuid, String commandType);

    void handleCommandFailure(String deviceUdid, String commandUuid, String commandType, String errorMessage);

    void handleInstallProfileCommand(String deviceUdid, String commandUuid);

    void handleFailedInstallProfileCommand(String deviceUdid, String commandUuid, String errorMessage);

    void handleInstalledApplicationList(String deviceUdid, Object[] appList);

    void handleManagedApplicationList(String deviceUdid, Map<String, Object> managedAppsMap);

    void handleSecurityInfoResponse(String deviceUdid, Map<String, Object> securityInfoMap);

    void handleDeviceLocationResponse(String udid, NSDictionary response);

    void handleCertificateListResponse(String udid, Object[] certificates);

    void handleUserListResponse(String udid, Object[] users);
}
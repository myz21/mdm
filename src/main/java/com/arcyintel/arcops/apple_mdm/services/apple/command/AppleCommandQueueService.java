package com.arcyintel.arcops.apple_mdm.services.apple.command;

import com.dd.plist.NSDictionary;

import java.util.Map;
import java.util.UUID;

public interface AppleCommandQueueService {
    Map.Entry<String, NSDictionary> popCommand(String udid) throws Exception;

    void pushCommand(String udid, String commandUUID, NSDictionary command, String commandType,
                     boolean isSystem, boolean fromPolicy, UUID policyId) throws Exception;

    void handleDeviceResponse(NSDictionary response);

    void handleDeviceErrorResponse(NSDictionary response);

    void handleDeviceNotNowResponse(NSDictionary response);
}
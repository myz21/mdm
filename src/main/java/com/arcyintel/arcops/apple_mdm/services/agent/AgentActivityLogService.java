package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.domains.AgentActivityLog;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AgentActivityLogService {

    AgentActivityLog logStart(String udid, String activityType, String sessionId,
                              Map<String, Object> details, String initiatedBy);

    void logComplete(String sessionId);

    AgentActivityLog logNotification(String udid, Map<String, Object> details,
                                      String channel, String initiatedBy);

    List<AgentActivityLog> getDeviceActivityLog(UUID deviceId, int limit);
}

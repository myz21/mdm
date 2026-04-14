package com.arcyintel.arcops.apple_mdm.repositories;

import java.util.List;
import java.util.Map;

public interface AgentPresenceHistoryRepositoryCustom {

    List<Map<String, Object>> getOnlineHeatmap(String dateFrom, String dateTo);

    List<Map<String, Object>> getDeviceUptime(String dateFrom, String dateTo);
}

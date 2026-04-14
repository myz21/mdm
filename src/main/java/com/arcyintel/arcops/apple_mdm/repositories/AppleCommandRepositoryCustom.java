package com.arcyintel.arcops.apple_mdm.repositories;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AppleCommandRepositoryCustom {

    List<Map<String, Object>> getCommandUserStats(String dateFrom, String dateTo, int limit);

    long countBulkCommandDevices(UUID bulkCommandId, String status, String search);

    List<Map<String, Object>> getBulkCommandDevices(UUID bulkCommandId, String status, String search, int size, int offset);
}

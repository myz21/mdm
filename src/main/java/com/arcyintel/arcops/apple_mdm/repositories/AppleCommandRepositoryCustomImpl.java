package com.arcyintel.arcops.apple_mdm.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.*;

public class AppleCommandRepositoryCustomImpl implements AppleCommandRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Map<String, Object>> getCommandUserStats(String dateFrom, String dateTo, int limit) {
        var params = new ArrayList<Object>();
        var conditions = new ArrayList<String>();
        conditions.add("u.username IS NOT NULL");

        if (dateFrom != null) {
            conditions.add("u.request_time >= CAST(?1 AS TIMESTAMP)");
        }
        if (dateTo != null) {
            int paramIdx = dateFrom != null ? 2 : 1;
            conditions.add("u.request_time < CAST(?" + paramIdx + " AS TIMESTAMP)");
        }
        String where = String.join(" AND ", conditions);

        // Build parameter index for limit
        int limitParamIdx = 1;
        if (dateFrom != null) limitParamIdx++;
        if (dateTo != null) limitParamIdx++;

        String sql = "SELECT u.username, COUNT(*) AS total, " +
                "COUNT(*) FILTER (WHERE u.status = 'COMPLETED') AS completed, " +
                "COUNT(*) FILTER (WHERE u.status IN ('FAILED', 'ERROR', 'TIMED_OUT')) AS failed, " +
                "COUNT(*) FILTER (WHERE u.status IN ('PENDING', 'EXECUTING')) AS pending " +
                "FROM (" +
                "  SELECT ac.created_by AS username, ac.status, ac.request_time FROM apple_command ac" +
                "  UNION ALL" +
                "  SELECT CAST(NULL AS VARCHAR) AS username, " +
                "    CASE WHEN agc.status = 'SENT' THEN 'PENDING' ELSE agc.status END AS status, " +
                "    agc.request_time " +
                "  FROM agent_command agc " +
                "  WHERE agc.command_type NOT IN ('WebRtcOffer','WebRtcIce','WebRtcAnswer','TerminalInput','TerminalResize','TerminalSignal','RemoteMouse','RemoteKeyboard')" +
                ") u WHERE " + where +
                " GROUP BY u.username ORDER BY total DESC LIMIT ?" + limitParamIdx;

        Query query = entityManager.createNativeQuery(sql);

        int paramIndex = 1;
        if (dateFrom != null) {
            query.setParameter(paramIndex++, dateFrom + "T00:00:00");
        }
        if (dateTo != null) {
            query.setParameter(paramIndex++, dateTo + "T23:59:59");
        }
        query.setParameter(paramIndex, limit);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("username", row[0]);
            map.put("total", row[1]);
            map.put("completed", row[2]);
            map.put("failed", row[3]);
            map.put("pending", row[4]);
            result.add(map);
        }
        return result;
    }

    @Override
    public long countBulkCommandDevices(UUID bulkCommandId, String status, String search) {
        var conditions = new ArrayList<String>();
        conditions.add("ac.bulk_command_id = ?1");

        int paramIdx = 2;
        Integer statusIdx = null;
        Integer searchIdx1 = null;
        Integer searchIdx2 = null;

        if (status != null && !status.isBlank()) {
            statusIdx = paramIdx++;
            conditions.add("ac.status = ?" + statusIdx);
        }
        if (search != null && !search.isBlank()) {
            searchIdx1 = paramIdx++;
            searchIdx2 = paramIdx++;
            conditions.add("(LOWER(COALESCE(di.device_name, ad.product_name)) LIKE ?" + searchIdx1 + " OR LOWER(ad.serial_number) LIKE ?" + searchIdx2 + ")");
        }

        String where = String.join(" AND ", conditions);
        String sql = "SELECT COUNT(*) FROM apple_command ac LEFT JOIN apple_device ad ON ad.udid = ac.apple_device_udid LEFT JOIN apple_device_information di ON di.id = ad.id WHERE " + where;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, bulkCommandId);

        if (statusIdx != null) {
            query.setParameter(statusIdx, status.toUpperCase());
        }
        if (searchIdx1 != null) {
            String like = "%" + search.toLowerCase() + "%";
            query.setParameter(searchIdx1, like);
            query.setParameter(searchIdx2, like);
        }

        return ((Number) query.getSingleResult()).longValue();
    }

    @Override
    public List<Map<String, Object>> getBulkCommandDevices(UUID bulkCommandId, String status, String search, int size, int offset) {
        var conditions = new ArrayList<String>();
        conditions.add("ac.bulk_command_id = ?1");

        int paramIdx = 2;
        Integer statusIdx = null;
        Integer searchIdx1 = null;
        Integer searchIdx2 = null;

        if (status != null && !status.isBlank()) {
            statusIdx = paramIdx++;
            conditions.add("ac.status = ?" + statusIdx);
        }
        if (search != null && !search.isBlank()) {
            searchIdx1 = paramIdx++;
            searchIdx2 = paramIdx++;
            conditions.add("(LOWER(COALESCE(di.device_name, ad.product_name)) LIKE ?" + searchIdx1 + " OR LOWER(ad.serial_number) LIKE ?" + searchIdx2 + ")");
        }

        int sizeIdx = paramIdx++;
        int offsetIdx = paramIdx;

        String where = String.join(" AND ", conditions);
        String sql = "SELECT ac.id, ac.command_type, ac.status, ac.apple_device_udid AS device_identifier, " +
                "ad.serial_number, COALESCE(di.device_name, ad.product_name) AS product_name, ac.request_time, ac.completion_time, ac.failure_reason " +
                "FROM apple_command ac LEFT JOIN apple_device ad ON ad.udid = ac.apple_device_udid " +
                "LEFT JOIN apple_device_information di ON di.id = ad.id " +
                "WHERE " + where + " ORDER BY ac.request_time ASC LIMIT ?" + sizeIdx + " OFFSET ?" + offsetIdx;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, bulkCommandId);

        if (statusIdx != null) {
            query.setParameter(statusIdx, status.toUpperCase());
        }
        if (searchIdx1 != null) {
            String like = "%" + search.toLowerCase() + "%";
            query.setParameter(searchIdx1, like);
            query.setParameter(searchIdx2, like);
        }
        query.setParameter(sizeIdx, size);
        query.setParameter(offsetIdx, (long) offset);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", row[0]);
            map.put("command_type", row[1]);
            map.put("status", row[2]);
            map.put("device_identifier", row[3]);
            map.put("serial_number", row[4]);
            map.put("product_name", row[5]);
            map.put("request_time", row[6]);
            map.put("completion_time", row[7]);
            map.put("failure_reason", row[8]);
            result.add(map);
        }
        return result;
    }
}

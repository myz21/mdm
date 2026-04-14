package com.arcyintel.arcops.apple_mdm.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.*;

public class AgentPresenceHistoryRepositoryCustomImpl implements AgentPresenceHistoryRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Map<String, Object>> getOnlineHeatmap(String dateFrom, String dateTo) {
        var conditions = new ArrayList<String>();
        conditions.add("h.event_type = 'ONLINE'");

        int paramIdx = 1;
        Integer fromIdx = null;
        Integer toIdx = null;

        if (dateFrom != null) {
            fromIdx = paramIdx++;
            conditions.add("h.connected_at >= CAST(?" + fromIdx + " AS TIMESTAMP)");
        }
        if (dateTo != null) {
            toIdx = paramIdx++;
            conditions.add("h.connected_at <= CAST(?" + toIdx + " AS TIMESTAMP)");
        }

        String where = String.join(" AND ", conditions);
        String sql = "SELECT TO_CHAR(h.connected_at, 'YYYY-MM-DD') AS day, " +
                "EXTRACT(HOUR FROM h.connected_at)::INT AS hour, " +
                "COUNT(DISTINCT h.device_identifier) AS online_count " +
                "FROM agent_presence_history h " +
                "WHERE " + where + " " +
                "GROUP BY day, hour ORDER BY day, hour";

        Query query = entityManager.createNativeQuery(sql);
        if (fromIdx != null) query.setParameter(fromIdx, dateFrom);
        if (toIdx != null) query.setParameter(toIdx, dateTo);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("day", row[0]);
            map.put("hour", row[1]);
            map.put("online_count", row[2]);
            result.add(map);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getDeviceUptime(String dateFrom, String dateTo) {
        var conditions = new ArrayList<String>();

        int paramIdx = 1;
        Integer fromIdx = null;
        Integer toIdx = null;

        if (dateFrom != null) {
            fromIdx = paramIdx++;
            conditions.add("h.connected_at >= CAST(?" + fromIdx + " AS TIMESTAMP)");
        }
        if (dateTo != null) {
            toIdx = paramIdx++;
            conditions.add("h.connected_at <= CAST(?" + toIdx + " AS TIMESTAMP)");
        }

        String where = conditions.isEmpty() ? "1=1" : String.join(" AND ", conditions);

        String sql = "SELECT h.device_identifier, " +
                "ad.serial_number, COALESCE(di.device_name, ad.product_name) AS product_name, " +
                "COALESCE(SUM(CASE WHEN h.event_type = 'OFFLINE' THEN h.duration_seconds ELSE 0 END), 0) AS total_online_seconds, " +
                "COUNT(*) FILTER (WHERE h.event_type = 'ONLINE') AS online_events, " +
                "COUNT(*) FILTER (WHERE h.event_type = 'OFFLINE') AS offline_events, " +
                "MAX(h.connected_at) AS last_event_at " +
                "FROM agent_presence_history h " +
                "LEFT JOIN apple_device ad ON ad.udid = h.device_identifier " +
                "LEFT JOIN apple_device_information di ON di.id = ad.id " +
                "WHERE " + where + " " +
                "GROUP BY h.device_identifier, ad.serial_number, di.device_name, ad.product_name " +
                "ORDER BY total_online_seconds DESC";

        Query query = entityManager.createNativeQuery(sql);
        if (fromIdx != null) query.setParameter(fromIdx, dateFrom);
        if (toIdx != null) query.setParameter(toIdx, dateTo);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("device_identifier", row[0]);
            map.put("serial_number", row[1]);
            map.put("product_name", row[2]);
            map.put("total_online_seconds", row[3]);
            map.put("online_events", row[4]);
            map.put("offline_events", row[5]);
            map.put("last_event_at", row[6]);
            result.add(map);
        }
        return result;
    }
}

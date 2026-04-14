package com.arcyintel.arcops.apple_mdm.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.*;

public class AgentLocationRepositoryCustomImpl implements AgentLocationRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Map<String, Object>> queryLocations(String dateFrom, String dateTo,
                                                     List<String> spatialConditions, List<Object> spatialParams,
                                                     int limit) {
        var allConditions = new ArrayList<String>();
        int paramIdx = 1;

        // Time range filters
        Integer fromIdx = null;
        Integer toIdx = null;
        if (dateFrom != null) {
            fromIdx = paramIdx++;
            allConditions.add("al.device_created_at >= CAST(?" + fromIdx + " AS TIMESTAMP)");
        }
        if (dateTo != null) {
            toIdx = paramIdx++;
            allConditions.add("al.device_created_at <= CAST(?" + toIdx + " AS TIMESTAMP)");
        }

        // Spatial conditions — rewrite placeholders with proper param indices
        List<Integer> spatialParamIndices = new ArrayList<>();
        for (String condition : spatialConditions) {
            // Replace each ? with the next paramIdx
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < condition.length(); i++) {
                if (condition.charAt(i) == '?') {
                    int idx = paramIdx++;
                    spatialParamIndices.add(idx);
                    sb.append('?').append(idx);
                } else {
                    sb.append(condition.charAt(i));
                }
            }
            allConditions.add(sb.toString());
        }

        int limitIdx = paramIdx;

        String where = allConditions.isEmpty() ? "1=1" : String.join(" AND ", allConditions);
        String sql = "SELECT al.id, al.latitude, al.longitude, al.altitude, al.speed, " +
                "al.horizontal_accuracy AS accuracy, al.device_created_at AS timestamp, " +
                "al.device_identifier, ad.serial_number, COALESCE(di.device_name, ad.product_name) AS product_name " +
                "FROM agent_location al " +
                "LEFT JOIN apple_device ad ON ad.udid = al.device_identifier " +
                "LEFT JOIN apple_device_information di ON di.id = ad.id " +
                "WHERE " + where + " ORDER BY al.device_created_at DESC LIMIT ?" + limitIdx;

        Query query = entityManager.createNativeQuery(sql);

        if (fromIdx != null) query.setParameter(fromIdx, dateFrom);
        if (toIdx != null) query.setParameter(toIdx, dateTo);

        // Set spatial parameters
        int spatialIdx = 0;
        for (int idx : spatialParamIndices) {
            query.setParameter(idx, spatialParams.get(spatialIdx++));
        }

        query.setParameter(limitIdx, limit);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", row[0]);
            map.put("latitude", row[1]);
            map.put("longitude", row[2]);
            map.put("altitude", row[3]);
            map.put("speed", row[4]);
            map.put("accuracy", row[5]);
            map.put("timestamp", row[6]);
            map.put("device_identifier", row[7]);
            map.put("serial_number", row[8]);
            map.put("product_name", row[9]);
            result.add(map);
        }
        return result;
    }
}

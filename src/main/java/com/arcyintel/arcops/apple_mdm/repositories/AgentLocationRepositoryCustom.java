package com.arcyintel.arcops.apple_mdm.repositories;

import java.util.List;
import java.util.Map;

public interface AgentLocationRepositoryCustom {

    /**
     * Query location data with dynamic spatial and temporal filters.
     *
     * @param dateFrom      optional start time filter
     * @param dateTo        optional end time filter
     * @param spatialConditions   additional SQL conditions for spatial filtering (already parameterized)
     * @param spatialParams       parameters for the spatial conditions, in order
     * @param limit         max rows to return
     * @return list of location rows as maps
     */
    List<Map<String, Object>> queryLocations(String dateFrom, String dateTo,
                                              List<String> spatialConditions, List<Object> spatialParams,
                                              int limit);
}

package com.arcyintel.arcops.apple_mdm.clients;

import com.arcyintel.arcops.apple_mdm.domains.OAuth2ProviderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST client for communicating with the back_core service.
 */
@Slf4j
@Service
public class BackCoreClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BackCoreClient(
            @Value("${services.back-core.url:http://localhost:8086/api/back-core}") String backCoreBaseUrl,
            ObjectMapper objectMapper
    ) {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(backCoreBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch OAuth2 provider config for a given domain from back_core.
     */
    public OAuth2ProviderConfig getProviderConfigByDomain(String domain) {
        try {
            log.debug("Fetching OAuth2 provider config for domain: {}", domain);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("/oauth2-providers/by-domain?domain={domain}", domain)
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> data = unwrapApiResponse(response);
            if (data == null) return null;

            OAuth2ProviderConfig config = objectMapper.convertValue(data, OAuth2ProviderConfig.class);
            log.debug("Successfully retrieved OAuth2 provider config for domain: {}", domain);
            return config;
        } catch (RestClientException e) {
            log.warn("Failed to fetch OAuth2 provider config for domain: {}. Error: {}", domain, e.getMessage());
            return null;
        }
    }

    /**
     * Check if an app group is referenced in any policy across all Apple platforms.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> checkAppGroupUsageInPolicies(String appGroupId) {
        List<Map<String, String>> allResults = new ArrayList<>();
        for (String platform : List.of("ios", "macos", "tvos", "visionos", "watchos")) {
            try {
                Map<String, Object> response = restClient.get()
                        .uri("/{platform}/policies/app-group-usage/{id}", platform, appGroupId)
                        .retrieve()
                        .body(Map.class);
                Map<String, Object> unwrapped = unwrapApiResponse(response);
                if (unwrapped != null) {
                    // When the data field is a list, it comes as the "data" value directly
                    Object dataField = response != null && response.containsKey("data") ? response.get("data") : response;
                    if (dataField instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> map) {
                                allResults.add((Map<String, String>) map);
                            }
                        }
                    }
                }
            } catch (RestClientException e) {
                log.warn("Failed to check app group usage for platform {}: {}", platform, e.getMessage());
            }
        }
        return allResults;
    }

    /**
     * Get the account group IDs for accounts linked to a given identity.
     */
    @SuppressWarnings("unchecked")
    public List<UUID> getAccountGroupIdsForIdentity(UUID identityId) {
        for (String platform : List.of("ios", "macos", "tvos", "visionos", "watchos")) {
            try {
                Map<String, Object> response = restClient.get()
                        .uri("/{platform}/account-groups/by-identity/{identityId}", platform, identityId)
                        .retrieve()
                        .body(Map.class);
                Object data = response != null && response.containsKey("data") ? response.get("data") : response;
                if (data instanceof List<?> list && !list.isEmpty()) {
                    List<UUID> ids = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof String s) ids.add(UUID.fromString(s));
                        else if (item instanceof UUID u) ids.add(u);
                    }
                    return ids;
                }
            } catch (RestClientException e) {
                // Try next platform
            }
        }
        return List.of();
    }

    /**
     * Fetch all OAuth2 provider configs from back_core.
     */
    @SuppressWarnings("unchecked")
    public List<OAuth2ProviderConfig> getAllProviderConfigs() {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/oauth2-providers")
                    .retrieve()
                    .body(Map.class);

            Object data = response != null && response.containsKey("data") ? response.get("data") : response;
            if (data instanceof List<?> list) {
                return list.stream()
                        .map(item -> objectMapper.convertValue(item, OAuth2ProviderConfig.class))
                        .toList();
            }
            return List.of();
        } catch (RestClientException e) {
            log.warn("Failed to fetch OAuth2 provider configs. Error: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch device IDs for a device group from back_core.
     * Tries Apple inner platforms until the group is found.
     */
    @SuppressWarnings("unchecked")
    public Set<UUID> getDeviceGroupDeviceIds(UUID deviceGroupId) {
        for (String platform : List.of("ios", "macos", "tvos", "visionos")) {
            try {
                Map<String, Object> response = restClient.get()
                        .uri("/{platform}/device-groups/{id}", platform, deviceGroupId)
                        .retrieve()
                        .body(Map.class);
                Map<String, Object> data = unwrapApiResponse(response);
                if (data != null) {
                    Object deviceIdsObj = data.get("deviceIds");
                    if (deviceIdsObj instanceof List<?> idList) {
                        Set<UUID> result = new java.util.HashSet<>();
                        for (Object id : idList) {
                            if (id instanceof String s) result.add(UUID.fromString(s));
                        }
                        if (!result.isEmpty()) return result;
                    }
                }
            } catch (RestClientException e) {
                // Try next platform
            }
        }
        return Set.of();
    }

    /**
     * Unwraps ApiResponse wrapper if present.
     * back_core wraps all responses in {success, data, traceId, timestamp}.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapApiResponse(Map<String, Object> response) {
        if (response != null && response.containsKey("data") && response.containsKey("success")) {
            Object data = response.get("data");
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
        }
        return response;
    }
}

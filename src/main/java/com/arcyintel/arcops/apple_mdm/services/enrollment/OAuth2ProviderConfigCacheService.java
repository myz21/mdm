package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.clients.BackCoreClient;
import com.arcyintel.arcops.apple_mdm.domains.OAuth2ProviderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-cached access to OAuth2 provider configs.
 * On cache miss, fetches from back_core via REST API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ProviderConfigCacheService {

    private static final String CACHE_KEY_PREFIX = "oauth2:provider:config:domain:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final BackCoreClient backCoreClient;
    private final ObjectMapper objectMapper;

    @Value("${mdm.enrollment.account-driven.oauth2.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    /**
     * Get OAuth2 provider config for a domain.
     * Checks Redis cache first, falls back to REST call to back_core.
     *
     * @param domain the customer domain (e.g., arcyintel.com)
     * @return provider config or null if not found
     */
    public OAuth2ProviderConfig getByDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }

        // Try exact domain first, then walk up to parent domains
        // e.g., arcops.arcyintel.com → arcyintel.com
        String currentDomain = domain;
        while (currentDomain != null && !currentDomain.isEmpty()) {
            OAuth2ProviderConfig config = lookupDomain(currentDomain);
            if (config != null) {
                // Cache under the original domain too for faster subsequent lookups
                if (!currentDomain.equals(domain)) {
                    cacheConfig(domain, config);
                }
                return config;
            }
            // Move to parent domain
            int dotIndex = currentDomain.indexOf('.');
            if (dotIndex < 0 || dotIndex == currentDomain.length() - 1) {
                break;
            }
            currentDomain = currentDomain.substring(dotIndex + 1);
            // Stop at TLD (must have at least one dot remaining)
            if (!currentDomain.contains(".")) {
                break;
            }
        }

        return null;
    }

    private OAuth2ProviderConfig lookupDomain(String domain) {
        String cacheKey = CACHE_KEY_PREFIX + domain;

        // Try cache first
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                OAuth2ProviderConfig config = objectMapper.convertValue(cached, OAuth2ProviderConfig.class);
                log.debug("OAuth2 provider config cache hit for domain: {}", domain);
                return config;
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for domain: {}, falling back to REST. Error: {}", domain, e.getMessage());
        }

        // Cache miss → fetch from back_core
        log.debug("OAuth2 provider config cache miss for domain: {}", domain);
        OAuth2ProviderConfig config = backCoreClient.getProviderConfigByDomain(domain);

        if (config != null) {
            cacheConfig(domain, config);
        }

        return config;
    }

    private void cacheConfig(String domain, OAuth2ProviderConfig config) {
        try {
            String cacheKey = CACHE_KEY_PREFIX + domain;
            redisTemplate.opsForValue().set(cacheKey, config, cacheTtlSeconds, TimeUnit.SECONDS);
            log.debug("OAuth2 provider config cached for domain: {} (TTL={}s)", domain, cacheTtlSeconds);
        } catch (Exception e) {
            log.warn("Redis cache write failed for domain: {}. Error: {}", domain, e.getMessage());
        }
    }

    /**
     * Manually evict cache for a domain.
     */
    public void evict(String domain) {
        String cacheKey = CACHE_KEY_PREFIX + domain;
        redisTemplate.delete(cacheKey);
        log.debug("OAuth2 provider config cache evicted for domain: {}", domain);
    }
}

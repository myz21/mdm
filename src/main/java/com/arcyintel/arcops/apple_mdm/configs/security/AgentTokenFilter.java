package com.arcyintel.arcops.apple_mdm.configs.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filters requests to /agent/catalog/** endpoints.
 * Validates Bearer token against Redis and sets identity_id as request attribute.
 */
@Component
@RequiredArgsConstructor
public class AgentTokenFilter extends OncePerRequestFilter {

    private static final String AGENT_TOKEN_PREFIX = "agent:session:";
    public static final String IDENTITY_ID_ATTR = "agent_identity_id";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Only filter /agent/catalog/** paths
        return !path.startsWith("/agent/catalog");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Missing or invalid token.\"}");
            response.setContentType("application/json");
            return;
        }

        String token = authHeader.substring(7);
        String redisKey = AGENT_TOKEN_PREFIX + token;
        Object identityId = redisTemplate.opsForValue().get(redisKey);

        if (identityId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Token expired or invalid.\"}");
            response.setContentType("application/json");
            return;
        }

        // Sliding expiration: renew TTL on each authenticated request
        redisTemplate.expire(redisKey, 24, java.util.concurrent.TimeUnit.HOURS);

        request.setAttribute(IDENTITY_ID_ATTR, identityId.toString());
        filterChain.doFilter(request, response);
    }
}

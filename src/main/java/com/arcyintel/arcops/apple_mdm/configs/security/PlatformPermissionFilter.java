package com.arcyintel.arcops.apple_mdm.configs.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Servlet filter that enforces granular platform permissions on apple_mdm endpoints.
 *
 * All JWT-authenticated endpoints require at least one "apple" grant.
 * MDM protocol and agent endpoints are handled by mdmFilterChain (permitAll).
 *
 * Maps request path to specific grant requirements:
 * - /devices/{udid}/commands/* → devices:command
 * - /vnc-*, /screen-share/*, /remote-terminal/* → devices:remote
 * - /cert/*, /abm/* → enrollment:manage (writes), enrollment:view (reads)
 * - /app-groups/* → apps:view / apps:manage
 * - /devices/* (read) → devices:view
 */
@Component
public class PlatformPermissionFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(PlatformPermissionFilter.class);

    private static final String PLATFORM_KEY = "apple";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            // No JWT — MDM/agent request, let it through
            filterChain.doFilter(request, response);
            return;
        }

        List<String> grants = extractGrants(jwtAuth);
        if (grants == null) {
            logger.warn("Permission denied: {} {} → no access to apple platform", method, path);
            sendForbidden(response, "No access to platform: apple");
            return;
        }

        // Determine required grant based on path
        String requiredGrant = resolveRequiredGrant(path, method);
        if (requiredGrant != null && !grants.contains(requiredGrant) && !grants.contains("*")) {
            logger.warn("Permission denied: {} {} → missing grant '{}'", method, path, requiredGrant);
            sendForbidden(response, "Missing permission: " + requiredGrant);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isReadOperation(String path, String method) {
        return READ_METHODS.contains(method)
                || ("POST".equals(method) && path.endsWith("/list"));
    }

    private String resolveRequiredGrant(String path, String method) {
        // Commands
        if (path.contains("/commands/") || path.contains("/commands")) {
            return "devices:command";
        }

        // Remote access (WebSocket paths handled by mdmFilterChain, but REST endpoints here)
        if (path.contains("/vnc") || path.contains("/screen-share") || path.contains("/remote-terminal")) {
            return "devices:remote";
        }

        // Enrollment / certificates / ABM / DEP
        if (path.startsWith("/api/apple/cert") || path.startsWith("/api/apple/abm")) {
            return isReadOperation(path, method) ? "enrollment:view" : "enrollment:manage";
        }

        // App groups
        if (path.startsWith("/api/apple/app-groups") || path.startsWith("/api/apple/enterprise-apps")) {
            return isReadOperation(path, method) ? "apps:view" : "apps:manage";
        }

        // VPP
        if (path.contains("/vpp")) {
            return isReadOperation(path, method) ? "apps:view" : "apps:manage";
        }

        // Device endpoints (info, list, etc.)
        if (path.startsWith("/api/apple/devices")) {
            return isReadOperation(path, method) ? "devices:view" : "devices:manage";
        }

        // System settings
        if (path.startsWith("/api/apple/system-settings")) {
            return isReadOperation(path, method) ? "settings" : "settings";
        }

        // Dashboard (read-only analytics)
        if (path.startsWith("/api/apple/dashboard")) {
            return "dashboard";
        }

        // Reports (read-only analytics)
        if (path.startsWith("/api/apple/reports")) {
            return "reports";
        }

        // Default: just require any apple grant (has platform access)
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractGrants(JwtAuthenticationToken jwtAuth) {
        Object permsClaim = jwtAuth.getToken().getClaim("permissions");
        if (!(permsClaim instanceof Map<?, ?> permissions)) {
            return null;
        }

        Object wildcard = permissions.get("*");
        if (wildcard instanceof List<?> wGrants && !wGrants.isEmpty()) {
            return (List<String>) wGrants;
        }

        Object platformGrants = permissions.get(PLATFORM_KEY);
        if (platformGrants instanceof List<?> pGrants && !pGrants.isEmpty()) {
            return (List<String>) pGrants;
        }

        return null;
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}

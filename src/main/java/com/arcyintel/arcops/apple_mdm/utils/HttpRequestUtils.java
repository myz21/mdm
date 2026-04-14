package com.arcyintel.arcops.apple_mdm.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility methods for extracting client information from HTTP requests.
 * Consolidates IP extraction logic from AgentAuthController and EnrollmentAuditServiceImpl.
 */
public final class HttpRequestUtils {

    private HttpRequestUtils() {
    }

    /**
     * Extracts the client IP address from the request, checking proxy headers first.
     * Checks X-Forwarded-For, then X-Real-IP, then falls back to remoteAddr.
     */
    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) return null;

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Extracts the User-Agent header, truncating to 512 characters if needed.
     */
    public static String extractUserAgent(HttpServletRequest request) {
        if (request == null) return null;
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 512) {
            return userAgent.substring(0, 512);
        }
        return userAgent;
    }
}

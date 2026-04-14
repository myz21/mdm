package com.arcyintel.arcops.apple_mdm.models.profile;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BasePayload {

    private String payloadIdentifier;
    private String payloadType;
    private String payloadUUID;
    private Integer payloadVersion;
    private Boolean payloadRemovalDisallowed;

    /**
     * Safely converts a value from a deserialized JSON map to int.
     * Handles: null, Integer, Long, Double, String, and other Number types.
     */
    protected static int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    /**
     * Safely converts a value from a deserialized JSON map to boolean.
     * Handles: null, Boolean, String ("true"/"false"), Number (0=false, else true).
     */
    protected static boolean toBool(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s.trim());
        if (value instanceof Number n) return n.intValue() != 0;
        return defaultValue;
    }
}

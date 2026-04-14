package com.arcyintel.arcops.apple_mdm.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Utility methods for safely extracting typed values from Jackson JsonNode objects.
 * Used by AgentTelemetryService, AgentLocationService, and other MQTT message handlers.
 */
public final class JsonNodeUtils {

    private JsonNodeUtils() {
    }

    public static Integer intOrNull(JsonNode node, String field) {
        JsonNode val = node.path(field);
        return val.isMissingNode() || val.isNull() ? null : val.asInt();
    }

    public static Long longOrNull(JsonNode node, String field) {
        JsonNode val = node.path(field);
        return val.isMissingNode() || val.isNull() ? null : val.asLong();
    }

    public static Boolean boolOrNull(JsonNode node, String field) {
        JsonNode val = node.path(field);
        return val.isMissingNode() || val.isNull() ? null : val.asBoolean();
    }

    public static String textOrNull(JsonNode node, String field) {
        JsonNode val = node.path(field);
        return val.isMissingNode() || val.isNull() ? null : val.asText();
    }

    public static Double doubleOrNull(JsonNode node, String field) {
        JsonNode val = node.path(field);
        return val.isMissingNode() || val.isNull() ? null : val.asDouble();
    }

    public static Instant parseInstant(JsonNode json, String field, Instant fallback) {
        String value = json.path(field).asText(null);
        if (value == null) return fallback;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }
}

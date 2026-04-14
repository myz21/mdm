package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSDictionary;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.arcyintel.arcops.commons.constants.apple.CommandSpecificConfigurations.*;
import static com.arcyintel.arcops.commons.constants.policy.PolicyStatus.POLICY_ID;
import static com.arcyintel.arcops.commons.constants.policy.PolicyStatus.STATUS;

public final class Settings {

    private Settings() {
    }

    /**
     * Builds the list of NSDictionary "Item" entries to be sent with the MDM Settings command.
     * Returns empty list if no SETTINGS block exists or it's empty.
     */
    @SuppressWarnings("unchecked")
    public static List<NSDictionary> buildSettingsPayloads(Map<String, Object> configurationsMap) {
        if (configurationsMap == null || !configurationsMap.containsKey(SETTINGS)) {
            return List.of();
        }

        Object raw = configurationsMap.get(SETTINGS);
        if (!(raw instanceof Map<?, ?> mapRaw) || mapRaw.isEmpty()) {
            return List.of();
        }

        Map<String, Object> settingsMap = (Map<String, Object>) mapRaw;
        List<NSDictionary> payloads = new ArrayList<>();

        addFlatItemIfPresent(settingsMap, SETTINGS_ACCESSIBILITY, payloads);
        addFlatItemIfPresent(settingsMap, SETTINGS_APP_ANALYTICS, payloads);
        addFlatItemIfPresent(settingsMap, SETTINGS_BLUETOOTH, payloads);
        addFlatItemIfPresent(settingsMap, SETTINGS_DATA_ROAMING, payloads);
        addFlatItemIfPresent(settingsMap, SETTINGS_DEFAULT_APPLICATIONS, payloads);
        addFlatItemIfPresent(settingsMap, SETTINGS_DEVICE_NAME, payloads);
        addFlatItemIfPresent(settingsMap, SETTINGS_DIAGNOSTIC_SUBMISSION, payloads);
        addFlatItemIfPresent(settingsMap, SETTINGS_HOST_NAME, payloads);
        addFlatItemIfPresent(settingsMap, SETTINGS_PERSONAL_HOTSPOT, payloads);
        addFlatItemIfPresent(settingsMap, SETTINGS_TIME_ZONE, payloads);

        addNestedItemIfPresent(settingsMap, SETTINGS_MDM_OPTIONS, "MDMOptions", payloads);
        addNestedItemIfPresent(settingsMap, SETTINGS_ORGANIZATION_INFO, "OrganizationInfo", payloads);

        addWallpaperIfPresent(settingsMap, payloads);

        return payloads;
    }

    /**
     * If Settings policy includes a policyId, return it (so caller can pass it to sendSettings).
     */
    @SuppressWarnings("unchecked")
    public static UUID resolvePolicyId(Map<String, Object> configurationsMap) {
        if (configurationsMap == null || !configurationsMap.containsKey(SETTINGS)) return null;

        Object raw = configurationsMap.get(SETTINGS);
        if (!(raw instanceof Map<?, ?> mapRaw)) return null;

        Map<String, Object> settingsMap = (Map<String, Object>) mapRaw;
        Object id = settingsMap.get(POLICY_ID);
        if (id == null) return null;

        try {
            return UUID.fromString(id.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void addFlatItemIfPresent(Map<String, Object> settingsMap,
                                            String itemKey,
                                            List<NSDictionary> payloads) {
        if (!settingsMap.containsKey(itemKey)) return;

        Object raw = settingsMap.get(itemKey);
        if (!(raw instanceof Map<?, ?> rawMap)) return;

        Map<String, Object> map = (Map<String, Object>) rawMap;
        NSDictionary dict = new NSDictionary();
        dict.put("Item", itemKey);

        map.forEach((k, v) -> {
            if (v == null) return;
            if (STATUS.equals(k) || POLICY_ID.equals(k) || "Item".equals(k)) return;
            dict.put(k, v);
        });

        payloads.add(dict);
    }

    @SuppressWarnings("unchecked")
    private static void addNestedItemIfPresent(Map<String, Object> settingsMap,
                                              String itemKey,
                                              String nestedKey,
                                              List<NSDictionary> payloads) {
        if (!settingsMap.containsKey(itemKey)) return;

        Object raw = settingsMap.get(itemKey);
        if (!(raw instanceof Map<?, ?> rawMap)) return;

        Map<String, Object> map = (Map<String, Object>) rawMap;

        NSDictionary dict = new NSDictionary();
        dict.put("Item", itemKey);

        NSDictionary nested = new NSDictionary();
        map.forEach((k, v) -> {
            if (v == null) return;
            if (STATUS.equals(k) || POLICY_ID.equals(k) || "Item".equals(k)) return;
            nested.put(k, v);
        });

        dict.put(nestedKey, nested);
        payloads.add(dict);
    }

    @SuppressWarnings("unchecked")
    private static void addWallpaperIfPresent(Map<String, Object> settingsMap,
                                             List<NSDictionary> payloads) {
        if (!settingsMap.containsKey(SETTINGS_WALLPAPER)) return;

        Object raw = settingsMap.get(SETTINGS_WALLPAPER);
        if (!(raw instanceof Map<?, ?> rawMap)) return;

        Map<String, Object> wp = (Map<String, Object>) rawMap;

        NSDictionary dict = new NSDictionary();
        dict.put("Item", SETTINGS_WALLPAPER);

        if (wp.containsKey("Image")) {
            Object imageData = wp.get("Image");
            if (imageData instanceof String base64String && !base64String.isBlank()) {
                try {
                    dict.put("Image", Base64.getDecoder().decode(base64String.trim()));
                } catch (IllegalArgumentException ignored) {
                    // ignore invalid base64
                }
            } else if (imageData instanceof byte[] bytes) {
                dict.put("Image", bytes);
            }
        }

        if (wp.containsKey("Where")) {
            Object where = wp.get("Where");
            if (where != null) dict.put("Where", where);
        }

        payloads.add(dict);
    }
}
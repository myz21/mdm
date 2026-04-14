package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSDictionary;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppLock extends BasePayload {

    /**
     * Maps to:
     * AppLock.App -> Identifier, Options, UserEnabledOptions
     */
    private SingleAppConfig config;

    @SuppressWarnings("unchecked")
    public static AppLock createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) return null;

        // Expecting:
        // {
        //   "Identifier": "com.apple.mobilenotes",
        //   "Options": { ... },
        //   "UserEnabledOptions": { ... }
        // }
        SingleAppConfig cfg = new SingleAppConfig();
        cfg.setIdentifier(getString(map, "Identifier"));

        // Options (user can't change)
        if (map.containsKey("Options") && map.get("Options") instanceof Map) {
            Map<String, Object> optMap = (Map<String, Object>) map.get("Options");
            SingleAppConfigOptions options = new SingleAppConfigOptions();

            options.setDisableAutoLock(Boolean.TRUE.equals(getBoolean(optMap, "DisableAutoLock")));
            options.setDisableDeviceRotation(Boolean.TRUE.equals(getBoolean(optMap, "DisableDeviceRotation")));
            options.setDisableRingerSwitch(Boolean.TRUE.equals(getBoolean(optMap, "DisableRingerSwitch")));
            options.setDisableSleepWakeButton(Boolean.TRUE.equals(getBoolean(optMap, "DisableSleepWakeButton")));
            options.setDisableTouch(Boolean.TRUE.equals(getBoolean(optMap, "DisableTouch")));
            options.setDisableVolumeButtons(Boolean.TRUE.equals(getBoolean(optMap, "DisableVolumeButtons")));

            options.setEnableAssistiveTouch(Boolean.TRUE.equals(getBoolean(optMap, "EnableAssistiveTouch")));
            options.setEnableInvertColors(Boolean.TRUE.equals(getBoolean(optMap, "EnableInvertColors")));
            options.setEnableMonoAudio(Boolean.TRUE.equals(getBoolean(optMap, "EnableMonoAudio")));
            options.setEnableSpeakSelection(Boolean.TRUE.equals(getBoolean(optMap, "EnableSpeakSelection")));
            options.setEnableVoiceControl(Boolean.TRUE.equals(getBoolean(optMap, "EnableVoiceControl")));
            options.setEnableVoiceOver(Boolean.TRUE.equals(getBoolean(optMap, "EnableVoiceOver")));
            options.setEnableZoom(Boolean.TRUE.equals(getBoolean(optMap, "EnableZoom")));

            cfg.setOptions(options);
        }

        // UserEnabledOptions (user can toggle)
        if (map.containsKey("UserEnabledOptions") && map.get("UserEnabledOptions") instanceof Map) {
            Map<String, Object> userOptMap = (Map<String, Object>) map.get("UserEnabledOptions");
            SingleAppConfigUserEnabledOptions userOptions = new SingleAppConfigUserEnabledOptions();

            userOptions.setAssistiveTouch(Boolean.TRUE.equals(getBoolean(userOptMap, "AssistiveTouch")));
            userOptions.setInvertColors(Boolean.TRUE.equals(getBoolean(userOptMap, "InvertColors")));
            userOptions.setVoiceControl(Boolean.TRUE.equals(getBoolean(userOptMap, "VoiceControl")));
            userOptions.setVoiceOver(Boolean.TRUE.equals(getBoolean(userOptMap, "VoiceOver")));
            userOptions.setZoom(Boolean.TRUE.equals(getBoolean(userOptMap, "Zoom")));

            cfg.setUserEnabledOptions(userOptions);
        }

        // Build payload
        AppLock payload = AppLock.builder()
                .config(cfg)
                .build();

        // Apple spec:
        // PayloadType = com.apple.app.lock
        payload.setPayloadIdentifier("com.apple.app.lock." + policyId);
        payload.setPayloadType("com.apple.app.lock");
        payload.setPayloadUUID(UUID.randomUUID().toString());
        payload.setPayloadVersion(1);

        return payload;
    }

    /**
     * Produces the payload dictionary (PayloadContent item):
     *
     * <dict>
     *   <key>App</key>
     *   <dict>
     *     <key>Identifier</key><string>...</string>
     *     <key>Options</key><dict>...</dict>
     *     <key>UserEnabledOptions</key><dict>...</dict>
     *   </dict>
     *   <key>PayloadIdentifier</key>...
     *   <key>PayloadType</key>com.apple.app.lock
     *   <key>PayloadUUID</key>...
     *   <key>PayloadVersion</key>1
     * </dict>
     */
    public NSDictionary createPayload() {
        if (config == null) {
            throw new IllegalStateException("KioskLockdown.config must not be null");
        }
        if (config.getIdentifier() == null || config.getIdentifier().isBlank()) {
            throw new IllegalStateException("KioskLockdown.config.Identifier must not be null/blank");
        }

        NSDictionary payload = new NSDictionary();

        NSDictionary appDict = new NSDictionary();
        appDict.put("Identifier", config.getIdentifier());

        // Options (user can't change)
        if (config.getOptions() != null) {
            NSDictionary optionsDict = new NSDictionary();
            SingleAppConfigOptions opt = config.getOptions();

            putIfNotNull(optionsDict, "DisableAutoLock", opt.isDisableAutoLock());
            putIfNotNull(optionsDict, "DisableDeviceRotation", opt.isDisableDeviceRotation());
            putIfNotNull(optionsDict, "DisableRingerSwitch", opt.isDisableRingerSwitch());
            putIfNotNull(optionsDict, "DisableSleepWakeButton", opt.isDisableSleepWakeButton());
            putIfNotNull(optionsDict, "DisableTouch", opt.isDisableTouch());
            putIfNotNull(optionsDict, "DisableVolumeButtons", opt.isDisableVolumeButtons());

            putIfNotNull(optionsDict, "EnableAssistiveTouch", opt.isEnableAssistiveTouch());
            putIfNotNull(optionsDict, "EnableInvertColors", opt.isEnableInvertColors());
            putIfNotNull(optionsDict, "EnableMonoAudio", opt.isEnableMonoAudio());
            putIfNotNull(optionsDict, "EnableSpeakSelection", opt.isEnableSpeakSelection());
            putIfNotNull(optionsDict, "EnableVoiceControl", opt.isEnableVoiceControl());
            putIfNotNull(optionsDict, "EnableVoiceOver", opt.isEnableVoiceOver());
            putIfNotNull(optionsDict, "EnableZoom", opt.isEnableZoom());

            // Only include if at least 1 key present
            if (optionsDict.count() > 0) {
                appDict.put("Options", optionsDict);
            }
        }

        // UserEnabledOptions (user can toggle)
        if (config.getUserEnabledOptions() != null) {
            NSDictionary userOptionsDict = new NSDictionary();
            SingleAppConfigUserEnabledOptions uOpt = config.getUserEnabledOptions();

            putIfNotNull(userOptionsDict, "AssistiveTouch", uOpt.isAssistiveTouch());
            putIfNotNull(userOptionsDict, "InvertColors", uOpt.isInvertColors());
            putIfNotNull(userOptionsDict, "VoiceControl", uOpt.isVoiceControl());
            putIfNotNull(userOptionsDict, "VoiceOver", uOpt.isVoiceOver());
            putIfNotNull(userOptionsDict, "Zoom", uOpt.isZoom());

            if (userOptionsDict.count() > 0) {
                appDict.put("UserEnabledOptions", userOptionsDict);
            }
        }

        payload.put("App", appDict);

        payload.put("PayloadIdentifier", getPayloadIdentifier());
        payload.put("PayloadType", getPayloadType());
        payload.put("PayloadUUID", getPayloadUUID());
        payload.put("PayloadVersion", getPayloadVersion());

        return payload;
    }

    // ---- helpers ----

    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Boolean getBoolean(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        if (v instanceof String) {
            String s = ((String) v).trim().toLowerCase();
            if (s.equals("true") || s.equals("1") || s.equals("yes")) return true;
            if (s.equals("false") || s.equals("0") || s.equals("no")) return false;
        }
        throw new IllegalArgumentException("Invalid boolean value for key '" + key + "': " + v);
    }

    private static void putIfNotNull(NSDictionary dict, String key, Object value) {
        if (value != null) dict.put(key, value);
    }

    // -------------------------------------------------------------------------
    // Inner types (no separate top-level classes)
    // -------------------------------------------------------------------------

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SingleAppConfig {
        private String identifier;
        private SingleAppConfigOptions options;
        private SingleAppConfigUserEnabledOptions userEnabledOptions;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SingleAppConfigOptions {
        private boolean disableAutoLock;
        private boolean disableDeviceRotation;
        private boolean disableRingerSwitch;
        private boolean disableSleepWakeButton;
        private boolean disableTouch;
        private boolean disableVolumeButtons;
        private boolean enableAssistiveTouch;
        private boolean enableInvertColors;
        private boolean enableMonoAudio;
        private boolean enableSpeakSelection;
        private boolean enableVoiceOver;
        private boolean enableZoom;
        private boolean enableVoiceControl;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SingleAppConfigUserEnabledOptions {
        private boolean assistiveTouch;
        private boolean invertColors;
        private boolean voiceOver;
        private boolean zoom;
        private boolean voiceControl;
    }
}
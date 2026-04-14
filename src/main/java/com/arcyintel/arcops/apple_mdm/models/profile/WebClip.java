package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSDictionary;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebClip extends BasePayload {


    private String url;
    private String label;
    private byte[] icon;
    private boolean isRemovable = true;
    private boolean fullscreen = false;
    private boolean precomposed = false;
    private boolean ignoreManifestScope = false;
    private String targetApplicationBundleIdentifier;

    public static WebClip createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) {
            return null;
        }
        Object iconObj = map.get("icon");
        byte[] iconBytes = null;
        // Always treat icon as Base64-encoded string data (jpg/jpeg/png), decode to byte[]
        if (iconObj instanceof String s && !s.isBlank()) {
            try {
                iconBytes = java.util.Base64.getDecoder().decode(s.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        WebClip wc = WebClip.builder()
                .url((String) map.get("url"))
                .label((String) map.get("label"))
                .icon(iconBytes)
                .isRemovable(toBool(map.get("isRemovable"), true))
                .fullscreen(toBool(map.get("fullscreen"), false))
                .precomposed(toBool(map.get("precomposed"), false))
                .ignoreManifestScope(toBool(map.get("ignoreManifestScope"), false))
                .targetApplicationBundleIdentifier((String) map.get("targetApplicationBundleIdentifier"))
                .build();

        wc.setPayloadIdentifier(String.format("policy_web_clip-%s", policyId));
        wc.setPayloadType("com.apple.webClip.managed");
        wc.setPayloadUUID(UUID.randomUUID().toString());
        wc.setPayloadVersion(1);
        wc.setPayloadRemovalDisallowed(true);

        return wc;
    }

    /**
     * Creates a kiosk-mode WebClip: full-screen, non-removable, with IgnoreManifestScope to hide Safari UI.
     */
    public static WebClip createForKiosk(String domain, UUID policyId) {
        String url = domain.startsWith("http") ? domain : "https://" + domain;
        return createFromMap(Map.of(
                "url", url,
                "label", domain,
                "isRemovable", false,
                "fullscreen", true,
                "ignoreManifestScope", true
        ), policyId);
    }

    public NSDictionary createPayload() {
        NSDictionary dictionary = new NSDictionary();
        dictionary.put("URL", url);
        dictionary.put("Label", label);
        if (icon != null) dictionary.put("Icon", icon);
        dictionary.put("IsRemovable", isRemovable);
        dictionary.put("FullScreen", fullscreen);
        dictionary.put("Precomposed", precomposed);
        dictionary.put("IgnoreManifestScope", ignoreManifestScope);
        if (targetApplicationBundleIdentifier != null) {
            dictionary.put("TargetApplicationBundleIdentifier", targetApplicationBundleIdentifier);
        }

        dictionary.put("PayloadIdentifier", this.getPayloadIdentifier());
        dictionary.put("PayloadType", this.getPayloadType());
        dictionary.put("PayloadUUID", this.getPayloadUUID());
        dictionary.put("PayloadVersion", this.getPayloadVersion());
        dictionary.put("PayloadRemovalDisallowed", this.getPayloadRemovalDisallowed());
        return dictionary;
    }

}

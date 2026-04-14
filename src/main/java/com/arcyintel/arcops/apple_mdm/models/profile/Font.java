package com.arcyintel.arcops.apple_mdm.models.profile;

import com.dd.plist.NSDictionary;
import lombok.*;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Font extends BasePayload {

    public static final String FONT = "font"; // policy json key suggestion

    // Required fields by Apple profile payload
    private String name;        // user-visible file name e.g. "MyFont.ttf"
    private byte[] fontData;    // raw bytes of .ttf/.otf

    public static Font createFromMap(Map<String, Object> map, UUID policyId) {
        if (map == null) return null;

        // Expect either:
        // - "name": "Font.ttf"
        // - "fontBase64": "<base64>"
        String name = (String) map.get("name");

        Object base64Obj = map.get("fontBase64"); // recommended in your policy JSON
        byte[] bytes = null;
        if (base64Obj != null) {
            bytes = Base64.getDecoder().decode(base64Obj.toString());
        }

        Font payload = Font.builder()
                .name(name)
                .fontData(bytes)
                .build();

        payload.setPayloadIdentifier(String.format("policy_font-%s", policyId));
        payload.setPayloadType("com.apple.font");
        payload.setPayloadUUID(UUID.randomUUID().toString());
        payload.setPayloadVersion(1);

        return payload;
    }

    public NSDictionary createPayload() {
        NSDictionary d = new NSDictionary();

        // Required
        d.put("Name", this.getName());
        d.put("Font", this.getFontData()); // dd-plist will serialize byte[] as <data>

        // Standard payload keys
        d.put("PayloadIdentifier", this.getPayloadIdentifier());
        d.put("PayloadType", this.getPayloadType());
        d.put("PayloadUUID", this.getPayloadUUID());
        d.put("PayloadVersion", this.getPayloadVersion());

        return d;
    }
}
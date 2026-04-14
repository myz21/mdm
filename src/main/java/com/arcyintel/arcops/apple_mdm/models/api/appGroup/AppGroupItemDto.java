package com.arcyintel.arcops.apple_mdm.models.api.appgroup;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "App group item")
public class AppGroupItemDto {

    @Schema(description = "App type", example = "VPP", allowableValues = {"VPP", "ENTERPRISE"})
    private String appType;

    @Schema(description = "App Store/Adam track id as string", example = "310633997")
    private String trackId;

    @Schema(description = "Bundle identifier", example = "com.whatsapp")
    private String bundleId;

    @Schema(description = "Enterprise artifact reference (e.g., IPA storage key)", example = "ipa://apple/whatsapp_2.24.1.ipa")
    private String artifactRef;

    @Schema(description = "Display name override", example = "WhatsApp Messenger")
    private String displayName;

    @Schema(description = "Supported platforms from iTunes metadata", example = "[\"iPhone\",\"iPad\",\"Mac\"]")
    private java.util.List<String> supportedPlatforms;

    @Schema(description = "App icon URL from iTunes metadata", example = "https://is1-ssl.mzstatic.com/.../icon100x100.png")
    private String iconUrl;
}

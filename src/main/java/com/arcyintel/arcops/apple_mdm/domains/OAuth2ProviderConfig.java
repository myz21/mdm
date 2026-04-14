package com.arcyintel.arcops.apple_mdm.domains;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.UUID;

/**
 * POJO representation of an OAuth2 provider configuration.
 * Data is fetched from back_core via REST API and cached in Redis.
 * This is NOT a JPA entity - no local database table is used.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OAuth2ProviderConfig {

    private UUID id;

    private ProviderType providerType;

    private String domain;

    private String clientId;

    private String clientSecret;

    private String authorizationUrl;

    private String tokenUrl;

    private String jwkSetUri;

    @Builder.Default
    private String scopes = "openid,profile,email";

    @Builder.Default
    private Boolean enabled = true;

    public enum ProviderType {
        GOOGLE_WORKSPACE,
        AZURE_ENTRA,
        CUSTOM_OIDC
    }
}

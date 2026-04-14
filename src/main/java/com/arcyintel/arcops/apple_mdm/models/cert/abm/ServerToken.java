package com.arcyintel.arcops.apple_mdm.models.cert.abm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ServerToken {

    @JsonProperty("consumer_key")
    private String consumerKey;

    @JsonProperty("consumer_secret")
    private String consumerSecret;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("access_secret")
    private String accessSecret;

    @JsonProperty("access_token_expiry")
    private String accessTokenExpiry;
}

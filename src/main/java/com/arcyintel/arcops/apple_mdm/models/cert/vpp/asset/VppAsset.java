package com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class VppAsset {

    @JsonProperty("adamId")
    private String adamId;

    @JsonProperty("assignedCount")
    private int assignedCount;

    @JsonProperty("availableCount")
    private int availableCount;

    @JsonProperty("deviceAssignable")
    private boolean deviceAssignable;

    @JsonProperty("pricingParam")
    private String pricingParam;

    @JsonProperty("productType")
    private String productType;

    @JsonProperty("retiredCount")
    private int retiredCount;

    @JsonProperty("revocable")
    private boolean revocable;

    @JsonProperty("supportedPlatforms")
    private List<String> supportedPlatforms;

    @JsonProperty("totalCount")
    private int totalCount;
}
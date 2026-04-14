package com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import lombok.Data;

import java.util.List;

@Data
public class ItunesLookupResponse {
    @JsonProperty("resultCount")
    private int resultCount;

    @JsonProperty("results")
    private List<ItunesAppMeta> results;
}
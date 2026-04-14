package com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset;

import lombok.Data;

import java.util.List;

@Data
public class VppAssetsResponse {
    private List<VppAsset> assets;
    private Integer currentPageIndex;
    private Integer nextPageIndex;
    private Integer totalPages;
    private Integer size;
    private String tokenExpirationDate;
    private String uId;
    private String versionId;
}
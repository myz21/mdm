package com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset;

import lombok.Data;

import java.util.List;

@Data
public class AssignmentResponse {
    private List<Assignment> assignments;
    private Integer currentPageIndex;
    private Integer nextPageIndex;
    private Integer size;
    private Integer totalPages;
    private String tokenExpirationDate;
    private String uId;
    private String versionId;
}
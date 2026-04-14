package com.arcyintel.arcops.apple_mdm.models.cert.vpp.user;

import lombok.Data;

import java.util.List;

@Data
public class VppUsersResponse {
    private List<VppUser> users;
    private Integer currentPageIndex;
    private Integer nextPageIndex;
    private Integer size;
    private Integer totalPages;
    private String tokenExpirationDate;
    private String uId;
    private String versionId;
}
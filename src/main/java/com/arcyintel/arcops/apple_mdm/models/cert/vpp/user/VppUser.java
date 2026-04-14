package com.arcyintel.arcops.apple_mdm.models.cert.vpp.user;

import lombok.Data;

@Data
public class VppUser {
    private String clientUserId;
    private String email;
    private String inviteCode;
    private String status;
}
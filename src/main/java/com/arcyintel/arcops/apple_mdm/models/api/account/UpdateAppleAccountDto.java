package com.arcyintel.arcops.apple_mdm.models.api.account;

import lombok.Data;

@Data
public class UpdateAppleAccountDto {
    private String username;
    private String email;
    private String managedAppleId;
    private String fullName;
}
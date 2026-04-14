package com.arcyintel.arcops.apple_mdm.models.api.account;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GetAppleAccountDto {
    private UUID id;
    private String username;
    private String email;
    private String managedAppleId;
    private String fullName;
    private String status;
    private List<AccountDeviceDto> devices;

    @Data
    @Builder
    public static class AccountDeviceDto {
        private UUID id;
        private String udid;
        private String productName;
        private String serialNumber;
    }
}
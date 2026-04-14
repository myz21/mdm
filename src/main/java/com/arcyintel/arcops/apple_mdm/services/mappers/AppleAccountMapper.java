package com.arcyintel.arcops.apple_mdm.services.mappers;

import com.arcyintel.arcops.apple_mdm.domains.AppleAccount;
import com.arcyintel.arcops.apple_mdm.models.api.account.GetAppleAccountDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AppleAccountMapper {

    public GetAppleAccountDto toDto(AppleAccount account) {
        List<GetAppleAccountDto.AccountDeviceDto> deviceDtos = account.getDevices().stream()
                .map(d -> GetAppleAccountDto.AccountDeviceDto.builder()
                        .id(d.getId())
                        .udid(d.getUdid())
                        .productName(d.getProductName())
                        .serialNumber(d.getSerialNumber())
                        .build())
                .collect(Collectors.toList());

        return GetAppleAccountDto.builder()
                .id(account.getId())
                .username(account.getUsername())
                .email(account.getEmail())
                .managedAppleId(account.getManagedAppleId())
                .fullName(account.getFullName())
                .status(account.getStatus())
                .devices(deviceDtos)
                .build();
    }
}

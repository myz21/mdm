package com.arcyintel.arcops.apple_mdm.services.account;

import com.arcyintel.arcops.apple_mdm.models.api.account.CreateAppleAccountDto;
import com.arcyintel.arcops.apple_mdm.models.api.account.GetAppleAccountDto;
import com.arcyintel.arcops.apple_mdm.models.api.account.UpdateAppleAccountDto;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import org.springframework.data.web.PagedModel;

import java.util.List;
import java.util.Map;

public interface AppleAccountService {
    void createAccount(CreateAppleAccountDto dto);
    void updateAccount(String id, UpdateAppleAccountDto dto);
    void deleteAccount(String id);
    GetAppleAccountDto getAccount(String id);
    List<GetAppleAccountDto> getAccounts();
    PagedModel<Map<String, Object>> listAccounts(DynamicListRequestDto request);
    void assignAccountToDevice(String accountId, String deviceId);
    void removeAccountFromDevice(String accountId, String deviceId);
}
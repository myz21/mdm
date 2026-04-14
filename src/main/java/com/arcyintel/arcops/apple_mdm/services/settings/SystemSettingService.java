package com.arcyintel.arcops.apple_mdm.services.settings;

import com.arcyintel.arcops.apple_mdm.models.api.systemsetting.SystemSettingDto;

import java.util.List;
import java.util.Map;

public interface SystemSettingService {
    Map<String, Object> getValue(String operationIdentifier);
    void upsert(String operationIdentifier, Map<String, Object> value);
    SystemSettingDto getByIdentifier(String operationIdentifier);
    List<SystemSettingDto> listAll();
}

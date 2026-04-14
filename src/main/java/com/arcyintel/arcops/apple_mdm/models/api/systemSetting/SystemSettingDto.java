package com.arcyintel.arcops.apple_mdm.models.api.systemsetting;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
@Builder
public class SystemSettingDto {
    private String operationIdentifier;
    private Map<String, Object> value;
    private Date creationDate;
    private Date lastModifiedDate;
}

package com.arcyintel.arcops.apple_mdm.services.mappers;

import com.arcyintel.arcops.apple_mdm.domains.EnterpriseApp;
import com.arcyintel.arcops.apple_mdm.models.api.enterpriseapp.GetEnterpriseAppDto;
import org.springframework.stereotype.Component;

@Component
public class EnterpriseAppMapper {

    public GetEnterpriseAppDto toDto(EnterpriseApp app) {
        return GetEnterpriseAppDto.builder()
                .id(app.getId())
                .bundleId(app.getBundleId())
                .version(app.getVersion())
                .buildVersion(app.getBuildVersion())
                .displayName(app.getDisplayName())
                .minimumOsVersion(app.getMinimumOsVersion())
                .fileSizeBytes(app.getFileSizeBytes())
                .fileName(app.getFileName())
                .platform(app.getPlatform())
                .fileHash(app.getFileHash())
                .downloadUrl("/enterprise-apps/" + app.getId() + "/download")
                .iconBase64(app.getIconBase64())
                .supportedPlatforms(app.getSupportedPlatforms())
                .creationDate(app.getCreationDate())
                .lastModifiedDate(app.getLastModifiedDate())
                .build();
    }
}

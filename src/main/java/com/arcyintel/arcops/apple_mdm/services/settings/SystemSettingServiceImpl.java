package com.arcyintel.arcops.apple_mdm.services.settings;

import com.arcyintel.arcops.apple_mdm.domains.SystemSetting;
import com.arcyintel.arcops.apple_mdm.models.api.systemsetting.SystemSettingDto;
import com.arcyintel.arcops.apple_mdm.repositories.SystemSettingRepository;
import com.arcyintel.arcops.apple_mdm.services.settings.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl implements SystemSettingService {

    private static final Logger logger = LoggerFactory.getLogger(SystemSettingServiceImpl.class);

    private final SystemSettingRepository repository;

    @Override
    public Map<String, Object> getValue(String operationIdentifier) {
        return repository.findByOperationIdentifier(operationIdentifier)
                .map(SystemSetting::getValue)
                .orElse(Collections.emptyMap());
    }

    @Override
    public void upsert(String operationIdentifier, Map<String, Object> value) {
        SystemSetting setting = repository.findByOperationIdentifier(operationIdentifier)
                .orElseGet(() -> SystemSetting.builder()
                        .operationIdentifier(operationIdentifier)
                        .build());

        setting.setValue(value);
        repository.save(setting);
        logger.info("System setting upserted: {}", operationIdentifier);
    }

    @Override
    public SystemSettingDto getByIdentifier(String operationIdentifier) {
        return repository.findByOperationIdentifier(operationIdentifier)
                .map(this::toDto)
                .orElse(null);
    }

    @Override
    public List<SystemSettingDto> listAll() {
        return repository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private SystemSettingDto toDto(SystemSetting setting) {
        return SystemSettingDto.builder()
                .operationIdentifier(setting.getOperationIdentifier())
                .value(setting.getValue())
                .creationDate(setting.getCreationDate())
                .lastModifiedDate(setting.getLastModifiedDate())
                .build();
    }
}

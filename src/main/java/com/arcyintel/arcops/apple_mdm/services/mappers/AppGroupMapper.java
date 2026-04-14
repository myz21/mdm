package com.arcyintel.arcops.apple_mdm.services.mappers;

import com.arcyintel.arcops.apple_mdm.domains.AppGroup;
import com.arcyintel.arcops.apple_mdm.domains.AppGroupItem;
import com.arcyintel.arcops.apple_mdm.models.api.appgroup.AppGroupDto;
import org.mapstruct.*;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface AppGroupMapper {
    // Map only group-level fields; items are handled via service to maintain both sides
    @Mapping(target = "items", ignore = true)
    AppGroup toEntity(AppGroupDto appGroupDto);

    AppGroupDto toDto(AppGroup appGroup);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "items", ignore = true)
    AppGroup partialUpdate(AppGroupDto appGroupDto, @MappingTarget AppGroup appGroup);

    // If you have Item DTOs, add explicit mappings; otherwise these are placeholders:
    // AppGroupItem toItemEntity(AppGroupItemDto dto);
    // AppGroupItemDto toItemDto(AppGroupItem entity);

    // Utility to set parent on items after mapping (can be used from service)
    default void attachItems(AppGroup group, List<AppGroupItem> items) {
        group.getItems().clear();
        for (AppGroupItem it : items) {
            it.setAppGroup(group);
            group.getItems().add(it);
        }
    }
}
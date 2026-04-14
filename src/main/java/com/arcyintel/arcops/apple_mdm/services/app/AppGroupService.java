package com.arcyintel.arcops.apple_mdm.services.app;

import com.arcyintel.arcops.apple_mdm.domains.AppGroup;
import com.arcyintel.arcops.apple_mdm.domains.AppGroupItem;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AppGroupService {
    AppGroup create(AppGroup request);

    Optional<AppGroup> getById(UUID id);

    Page<AppGroup> list(int page, int size);

    PagedModel<Map<String, Object>> listAppGroups(DynamicListRequestDto request);

    AppGroup update(UUID id, AppGroup request);

    void delete(UUID id);

    AppGroup addItem(UUID groupId, AppGroupItem item);

    AppGroup replaceItems(UUID groupId, List<AppGroupItem> items);

    AppGroup removeItemByIndex(UUID groupId, int index);
}
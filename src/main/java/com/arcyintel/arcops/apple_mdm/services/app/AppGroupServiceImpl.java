package com.arcyintel.arcops.apple_mdm.services.app;

import com.arcyintel.arcops.apple_mdm.clients.BackCoreClient;
import com.arcyintel.arcops.apple_mdm.domains.AppGroup;
import com.arcyintel.arcops.apple_mdm.domains.AppGroupItem;
import com.arcyintel.arcops.apple_mdm.domains.EnterpriseApp;
import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import com.arcyintel.arcops.apple_mdm.models.api.appgroup.AppGroupDto;
import com.arcyintel.arcops.apple_mdm.repositories.AppGroupRepository;
import com.arcyintel.arcops.apple_mdm.repositories.EnterpriseAppRepository;
import com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository;
import com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository;
import com.arcyintel.arcops.apple_mdm.services.app.AppGroupService;
import com.arcyintel.arcops.apple_mdm.services.mappers.AppGroupMapper;
import com.arcyintel.arcops.apple_mdm.specifications.AppGroupFilterSpecification;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import com.arcyintel.arcops.commons.exceptions.BusinessException;
import com.arcyintel.arcops.commons.exceptions.ConflictException;
import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import com.arcyintel.arcops.commons.utils.FieldFilterUtil;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppGroupServiceImpl implements AppGroupService {

    private static final Logger logger = LoggerFactory.getLogger(AppGroupServiceImpl.class);

    private final AppGroupRepository appGroupRepository;
    private final AppGroupMapper appGroupMapper;
    private final ItunesAppMetaRepository itunesAppMetaRepository;
    private final EnterpriseAppRepository enterpriseAppRepository;
    private final BackCoreClient backCoreClient;

    @Transactional
    @Override
    public AppGroup create(AppGroup request) {
        logger.info("Creating AppGroup with name='{}'", request != null ? request.getName() : null);
        validateGroup(request);
        request.setId(null); // ensure new

        // Attach parent for each incoming item before save
        if (request.getItems() != null) {
            for (AppGroupItem it : request.getItems()) {
                validateItem(it);
                it.setAppGroup(request);
            }
        }

        AppGroup saved = appGroupRepository.save(request);
        logger.info("AppGroup created. id={}, name='{}'", saved.getId(), saved.getName());
        return saved;
    }

    @Override
    public Optional<AppGroup> getById(UUID id) {
        logger.debug("Fetching AppGroup by id={}", id);
        Optional<AppGroup> result = appGroupRepository.findById(id);
        if (result.isEmpty()) {
            logger.warn("AppGroup not found. id={}", id);
        } else {
            logger.debug("AppGroup found. id={}, name='{}'", id, result.get().getName());
        }
        result.ifPresent(this::enrichWithPlatforms);
        return result;
    }

    @Override
    public Page<AppGroup> list(int page, int size) {
        logger.debug("Listing AppGroups page={}, size={}", page, size);
        Page<AppGroup> paged = appGroupRepository.findAll(PageRequest.of(page, size));
        paged.getContent().forEach(this::enrichWithPlatforms);
        logger.info("Listed AppGroups: page={}, size={}, totalElements={}", page, size, paged.getTotalElements());
        return paged;
    }

    @Override
    public PagedModel<Map<String, Object>> listAppGroups(DynamicListRequestDto request) {
        if (request.getPage() < 0 || request.getSize() <= 0) {
            logger.warn("Invalid pagination parameters: page={}, size={}.", request.getPage(), request.getSize());
            throw new BusinessException("VALIDATION_ERROR", "Invalid pagination parameters");
        }

        AppGroupFilterSpecification spec = new AppGroupFilterSpecification(
                request.getFilters(), request.isFuzzy(), request.getFuzzyThreshold());
        Page<AppGroup> groups = appGroupRepository.findAll(spec, PageRequest.of(request.getPage(), request.getSize()));
        groups.getContent().forEach(this::enrichWithPlatforms);

        if (groups.isEmpty()) {
            logger.info("No app groups found");
            return new PagedModel<>(Page.empty());
        }

        Set<String> fields = request.getFields();
        Page<Map<String, Object>> result = groups.map(group -> {
            AppGroupDto dto = appGroupMapper.toDto(group);
            return FieldFilterUtil.filterFields(dto, fields);
        });

        logger.info("Successfully retrieved {} app groups with {} fields.", result.getTotalElements(),
                fields == null || fields.isEmpty() ? "all" : fields.size());
        return new PagedModel<>(result);
    }

    @Transactional
    @Override
    public AppGroup update(UUID id, AppGroup request) {
        logger.info("Updating AppGroup id={}", id);
        AppGroup existing = appGroupRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Update failed. AppGroup not found. id={}", id);
                    return new EntityNotFoundException("AppGroup", id);
                });

        if (request.getName() != null) existing.setName(request.getName());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getMetadata() != null) existing.setMetadata(request.getMetadata());
        if (request.getItems() != null) {
            // detach current and attach new, maintaining both sides
            existing.getItems().clear();
            for (AppGroupItem it : request.getItems()) {
                validateItem(it);
                it.setAppGroup(existing); // ensure FK
                existing.getItems().add(it);
            }
        }

        validateGroup(existing);
        AppGroup saved = appGroupRepository.save(existing);
        logger.info("AppGroup updated. id={}, name='{}'", saved.getId(), saved.getName());
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        logger.info("Deleting AppGroup id={}", id);

        // Check if app group is referenced in any policy
        List<Map<String, String>> referencingPolicies = backCoreClient.checkAppGroupUsageInPolicies(id.toString());
        if (!referencingPolicies.isEmpty()) {
            String policyNames = referencingPolicies.stream()
                    .map(p -> p.get("name"))
                    .distinct()
                    .collect(Collectors.joining(", "));
            throw new ConflictException(
                    "This app group is used in the following policies: " + policyNames + ". Please remove it from the policies first.");
        }

        try {
            appGroupRepository.deleteById(id);
            logger.info("AppGroup deleted. id={}", id);
        } catch (EmptyResultDataAccessException ex) {
            logger.warn("Delete requested for non-existing AppGroup. id={}", id);
        }
    }

    // Items management

    @Transactional
    @Override
    public AppGroup addItem(UUID groupId, AppGroupItem item) {
        logger.info("Adding item to AppGroup id={}", groupId);
        AppGroup group = appGroupRepository.findById(groupId)
                .orElseThrow(() -> {
                    logger.error("Add item failed. AppGroup not found. id={}", groupId);
                    return new EntityNotFoundException("AppGroup", groupId);
                });
        validateItem(item);
        item.setAppGroup(group); // ensure FK
        group.getItems().add(item);
        AppGroup saved = appGroupRepository.save(group);
        logger.info("Item added to AppGroup. id={}, itemsCount={}", saved.getId(), saved.getItems().size());
        return saved;
    }

    @Transactional
    @Override
    public AppGroup replaceItems(UUID groupId, List<AppGroupItem> items) {
        logger.info("Replacing items of AppGroup id={}", groupId);
        AppGroup group = appGroupRepository.findById(groupId)
                .orElseThrow(() -> {
                    logger.error("Replace items failed. AppGroup not found. id={}", groupId);
                    return new EntityNotFoundException("AppGroup", groupId);
                });
        if (items == null) items = List.of();
        group.getItems().clear();
        for (AppGroupItem it : items) {
            validateItem(it);
            it.setAppGroup(group); // ensure FK
            group.getItems().add(it);
        }
        AppGroup saved = appGroupRepository.save(group);
        logger.info("Items replaced for AppGroup. id={}, newItemsCount={}", saved.getId(), saved.getItems().size());
        return saved;
    }

    @Transactional
    @Override
    public AppGroup removeItemByIndex(UUID groupId, int index) {
        logger.info("Removing item at index {} from AppGroup id={}", index, groupId);
        AppGroup group = appGroupRepository.findById(groupId)
                .orElseThrow(() -> {
                    logger.error("Remove item failed. AppGroup not found. id={}", groupId);
                    return new EntityNotFoundException("AppGroup", groupId);
                });
        if (index < 0 || index >= group.getItems().size()) {
            logger.error("Invalid item index {} for AppGroup id={}, itemsCount={}", index, groupId, group.getItems().size());
            throw new BusinessException("VALIDATION_ERROR", "Invalid item index: " + index);
        }
        group.getItems().remove(index);
        AppGroup saved = appGroupRepository.save(group);
        logger.info("Item removed. AppGroup id={}, itemsCount={}", saved.getId(), saved.getItems().size());
        return saved;
    }

    // Helpers

    private void validateGroup(AppGroup g) {
        if (g == null) {
            logger.error("Validation failed: AppGroup is null");
            throw new BusinessException("VALIDATION_ERROR", "AppGroup is null");
        }
        if (g.getName() == null || g.getName().isBlank()) {
            logger.error("Validation failed: AppGroup name is required");
            throw new BusinessException("VALIDATION_ERROR", "AppGroup name is required");
        }
        if (g.getItems() != null) {
            for (AppGroupItem it : g.getItems()) {
                validateItem(it);
                // ensure both-side consistency during validation too (in case create() caller forgets)
                if (it.getAppGroup() == null) {
                    it.setAppGroup(g);
                }
            }
        }
        logger.debug("Validation passed for AppGroup name='{}'", g.getName());
    }

    private void validateItem(AppGroupItem i) {
        if (i == null) {
            logger.error("Validation failed: AppGroupItem is null");
            throw new BusinessException("VALIDATION_ERROR", "AppGroupItem is null");
        }
        if (i.getAppType() == null) {
            logger.error("Validation failed: AppGroupItem.appType is required");
            throw new BusinessException("VALIDATION_ERROR", "AppGroupItem.appType is required");
        }

        switch (i.getAppType()) {
            case VPP -> {
                boolean trackMissing = (i.getTrackId() == null || i.getTrackId().isBlank());
                boolean bundleMissing = (i.getBundleId() == null || i.getBundleId().isBlank());
                if (trackMissing && bundleMissing) {
                    logger.error("Validation failed: VPP item requires trackId or bundleId");
                    throw new BusinessException("VALIDATION_ERROR", "VPP item requires trackId or bundleId");
                }
            }
            case ENTERPRISE -> {
                if (i.getBundleId() == null || i.getBundleId().isBlank()) {
                    logger.error("Validation failed: Enterprise item requires bundleId");
                    throw new BusinessException("VALIDATION_ERROR", "Enterprise item requires bundleId");
                }
                // artifactRef optional
            }
        }

        logger.debug("Validation passed for AppGroupItem type={}, bundleId='{}', trackId='{}'",
                i.getAppType(), i.getBundleId(), i.getTrackId());
    }

    /**
     * Enrich AppGroupItem instances with supportedPlatforms.
     * VPP items are enriched from ItunesAppMeta, Enterprise items from EnterpriseApp.
     * Uses batch fetch to avoid N+1 queries.
     */
    private void enrichWithPlatforms(AppGroup group) {
        if (group == null || group.getItems() == null || group.getItems().isEmpty()) return;

        // ── VPP items ───────────────────────────────────────────────────────
        List<Long> trackIds = group.getItems().stream()
                .filter(it -> it.getAppType() == AppGroup.AppType.VPP)
                .map(AppGroupItem::getTrackId)
                .filter(tid -> tid != null && !tid.isBlank())
                .distinct()
                .map(tid -> {
                    try { return Long.parseLong(tid); }
                    catch (NumberFormatException e) { return null; }
                })
                .filter(Objects::nonNull)
                .toList();

        Map<Long, ItunesAppMeta> metaMap = trackIds.isEmpty()
                ? Map.of()
                : itunesAppMetaRepository.findAllByTrackIdIn(trackIds)
                        .stream()
                        .collect(Collectors.toMap(ItunesAppMeta::getTrackId, meta -> meta, (a, b) -> a));

        // ── Enterprise items ────────────────────────────────────────────────
        List<String> enterpriseBundleIds = group.getItems().stream()
                .filter(it -> it.getAppType() == AppGroup.AppType.ENTERPRISE)
                .map(AppGroupItem::getBundleId)
                .filter(bid -> bid != null && !bid.isBlank())
                .distinct()
                .toList();

        Map<String, EnterpriseApp> enterpriseMap = enterpriseBundleIds.isEmpty()
                ? Map.of()
                : enterpriseAppRepository.findAllByBundleIdIn(enterpriseBundleIds)
                        .stream()
                        .collect(Collectors.toMap(EnterpriseApp::getBundleId, ea -> ea, (a, b) -> a));

        // ── Apply enrichment ────────────────────────────────────────────────
        for (AppGroupItem item : group.getItems()) {
            if (item.getAppType() == AppGroup.AppType.VPP) {
                if (item.getTrackId() != null && !item.getTrackId().isBlank()) {
                    try {
                        ItunesAppMeta meta = metaMap.get(Long.parseLong(item.getTrackId()));
                        if (meta != null) {
                            item.setSupportedPlatforms(
                                    meta.getSupportedPlatforms() != null ? meta.getSupportedPlatforms() : List.of());
                            String icon = meta.getArtworkUrl100();
                            if (icon == null || icon.isBlank()) icon = meta.getArtworkUrl60();
                            if (icon == null || icon.isBlank()) icon = meta.getArtworkUrl512();
                            item.setIconUrl(icon);
                        } else {
                            item.setSupportedPlatforms(List.of());
                        }
                    } catch (NumberFormatException e) {
                        item.setSupportedPlatforms(List.of());
                    }
                } else {
                    item.setSupportedPlatforms(List.of());
                }
            } else if (item.getAppType() == AppGroup.AppType.ENTERPRISE) {
                EnterpriseApp ea = (item.getBundleId() != null && !item.getBundleId().isBlank())
                        ? enterpriseMap.get(item.getBundleId())
                        : null;
                if (ea != null) {
                    item.setSupportedPlatforms(
                            ea.getSupportedPlatforms() != null ? ea.getSupportedPlatforms() : List.of());
                    if (ea.getIconBase64() != null && !ea.getIconBase64().isBlank()) {
                        item.setIconUrl("data:image/png;base64," + ea.getIconBase64());
                    }
                } else {
                    item.setSupportedPlatforms(List.of());
                }
            } else {
                item.setSupportedPlatforms(List.of());
            }
        }
    }
}

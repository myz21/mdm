package com.arcyintel.arcops.apple_mdm.services.app;

import com.arcyintel.arcops.apple_mdm.domains.*;
import com.arcyintel.arcops.apple_mdm.repositories.AppCatalogAssignmentRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppGroupRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceAppRepository;
import com.arcyintel.arcops.apple_mdm.repositories.EnterpriseAppRepository;
import com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository;
import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(AppCatalogService.class);

    private final AppCatalogAssignmentRepository assignmentRepository;
    private final AppGroupRepository appGroupRepository;
    private final AppleDeviceAppRepository appleDeviceAppRepository;
    private final ItunesAppMetaRepository itunesAppMetaRepository;
    private final EnterpriseAppRepository enterpriseAppRepository;

    public AppCatalogAssignment assign(UUID appGroupId, String targetType, UUID targetId) {
        AppGroup appGroup = appGroupRepository.findById(appGroupId)
                .orElseThrow(() -> new EntityNotFoundException("AppGroup", appGroupId));

        AppCatalogAssignment assignment = AppCatalogAssignment.builder()
                .appGroup(appGroup)
                .targetType(targetType)
                .targetId(targetId)
                .build();

        assignment = assignmentRepository.save(assignment);
        logger.info("Catalog assigned: appGroup={} -> {}:{}", appGroupId, targetType, targetId);
        return assignment;
    }

    public void removeAssignment(UUID assignmentId) {
        assignmentRepository.deleteById(assignmentId);
        logger.info("Catalog assignment removed: {}", assignmentId);
    }

    public List<AppCatalogAssignment> getAssignmentsForAppGroup(UUID appGroupId) {
        return assignmentRepository.findByAppGroup_Id(appGroupId);
    }

    public List<AppCatalogAssignment> getAssignmentsByTarget(String targetType, UUID targetId) {
        return assignmentRepository.findByTargetTypeAndTargetId(targetType, targetId);
    }

    /**
     * Gets catalog for a specific account (direct assignments + via account groups).
     */
    public List<Map<String, Object>> getCatalogsForAccount(UUID accountId, List<UUID> accountGroupIds) {
        List<AppCatalogAssignment> assignments;
        if (accountGroupIds != null && !accountGroupIds.isEmpty()) {
            assignments = assignmentRepository.findByAccountOrGroups(accountId, accountGroupIds);
        } else {
            assignments = assignmentRepository.findByTargetTypeAndTargetId("ACCOUNT", accountId);
        }

        // Group by AppGroup, deduplicate
        Map<UUID, AppGroup> appGroups = new LinkedHashMap<>();
        for (AppCatalogAssignment a : assignments) {
            appGroups.putIfAbsent(a.getAppGroup().getId(), a.getAppGroup());
        }

        // Batch-fetch icon metadata for all items across all groups
        List<AppGroupItem> allItems = appGroups.values().stream()
                .filter(g -> g.getItems() != null)
                .flatMap(g -> g.getItems().stream())
                .toList();

        Map<Long, ItunesAppMeta> metaMap = resolveVppIcons(allItems);
        Map<String, EnterpriseApp> enterpriseMap = resolveEnterpriseIcons(allItems);

        List<Map<String, Object>> catalogs = new ArrayList<>();
        for (AppGroup group : appGroups.values()) {
            Map<String, Object> catalog = new LinkedHashMap<>();
            catalog.put("id", group.getId());
            catalog.put("name", group.getName());
            catalog.put("description", group.getDescription());

            List<Map<String, Object>> apps = new ArrayList<>();
            if (group.getItems() != null) {
                for (AppGroupItem item : group.getItems()) {
                    Map<String, Object> app = new LinkedHashMap<>();
                    app.put("appType", item.getAppType().name());
                    app.put("trackId", item.getTrackId());
                    app.put("bundleId", item.getBundleId());
                    app.put("displayName", item.getDisplayName());
                    app.put("iconUrl", resolveIconUrl(item, metaMap, enterpriseMap));
                    app.put("supportedPlatforms", resolveSupportedPlatforms(item, metaMap, enterpriseMap));
                    apps.add(app);
                }
            }
            catalog.put("apps", apps);
            catalogs.add(catalog);
        }

        return catalogs;
    }

    private Map<Long, ItunesAppMeta> resolveVppIcons(List<AppGroupItem> items) {
        List<Long> trackIds = items.stream()
                .filter(it -> it.getAppType() == AppGroup.AppType.VPP)
                .map(AppGroupItem::getTrackId)
                .filter(tid -> tid != null && !tid.isBlank())
                .distinct()
                .map(tid -> { try { return Long.parseLong(tid); } catch (NumberFormatException e) { return null; } })
                .filter(Objects::nonNull)
                .toList();
        if (trackIds.isEmpty()) return Map.of();
        return itunesAppMetaRepository.findAllByTrackIdIn(trackIds).stream()
                .collect(Collectors.toMap(ItunesAppMeta::getTrackId, m -> m, (a, b) -> a));
    }

    private Map<String, EnterpriseApp> resolveEnterpriseIcons(List<AppGroupItem> items) {
        List<String> bundleIds = items.stream()
                .filter(it -> it.getAppType() == AppGroup.AppType.ENTERPRISE)
                .map(AppGroupItem::getBundleId)
                .filter(bid -> bid != null && !bid.isBlank())
                .distinct()
                .toList();
        if (bundleIds.isEmpty()) return Map.of();
        return enterpriseAppRepository.findAllByBundleIdIn(bundleIds).stream()
                .collect(Collectors.toMap(EnterpriseApp::getBundleId, ea -> ea, (a, b) -> a));
    }

    private List<String> resolveSupportedPlatforms(AppGroupItem item, Map<Long, ItunesAppMeta> metaMap,
                                                   Map<String, EnterpriseApp> enterpriseMap) {
        if (item.getAppType() == AppGroup.AppType.VPP && item.getTrackId() != null && !item.getTrackId().isBlank()) {
            try {
                ItunesAppMeta meta = metaMap.get(Long.parseLong(item.getTrackId()));
                if (meta != null && meta.getSupportedPlatforms() != null) {
                    return meta.getSupportedPlatforms();
                }
            } catch (NumberFormatException ignored) {}
        } else if (item.getAppType() == AppGroup.AppType.ENTERPRISE && item.getBundleId() != null && !item.getBundleId().isBlank()) {
            EnterpriseApp ea = enterpriseMap.get(item.getBundleId());
            if (ea != null && ea.getSupportedPlatforms() != null) {
                return ea.getSupportedPlatforms();
            }
        }
        return List.of();
    }

    private String resolveIconUrl(AppGroupItem item, Map<Long, ItunesAppMeta> metaMap,
                                  Map<String, EnterpriseApp> enterpriseMap) {
        if (item.getAppType() == AppGroup.AppType.VPP && item.getTrackId() != null && !item.getTrackId().isBlank()) {
            try {
                ItunesAppMeta meta = metaMap.get(Long.parseLong(item.getTrackId()));
                if (meta != null) {
                    String icon = meta.getArtworkUrl100();
                    if (icon == null || icon.isBlank()) icon = meta.getArtworkUrl60();
                    if (icon == null || icon.isBlank()) icon = meta.getArtworkUrl512();
                    return icon;
                }
            } catch (NumberFormatException ignored) {}
        } else if (item.getAppType() == AppGroup.AppType.ENTERPRISE && item.getBundleId() != null && !item.getBundleId().isBlank()) {
            EnterpriseApp ea = enterpriseMap.get(item.getBundleId());
            if (ea != null && ea.getIconBase64() != null && !ea.getIconBase64().isBlank()) {
                return "data:image/png;base64," + ea.getIconBase64();
            }
        }
        return null;
    }

    /**
     * Enriches catalog apps with installed status from a specific device.
     */
    public List<Map<String, Object>> getEnrichedCatalogsForDevice(UUID accountId, List<UUID> accountGroupIds,
                                                                   AppleDevice device) {
        List<Map<String, Object>> catalogs = getCatalogsForAccount(accountId, accountGroupIds);

        // Get installed apps for device
        List<AppleDeviceApp> installedApps = appleDeviceAppRepository.findAllByAppleDevice_Id(device.getId());
        Map<String, AppleDeviceApp> installedByBundleId = installedApps.stream()
                .filter(a -> a.getBundleIdentifier() != null)
                .collect(Collectors.toMap(AppleDeviceApp::getBundleIdentifier, a -> a, (a, b) -> a));

        // Enrich each app with installed status
        for (Map<String, Object> catalog : catalogs) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> apps = (List<Map<String, Object>>) catalog.get("apps");
            if (apps != null) {
                for (Map<String, Object> app : apps) {
                    String bundleId = (String) app.get("bundleId");
                    if (bundleId != null && installedByBundleId.containsKey(bundleId)) {
                        AppleDeviceApp installedApp = installedByBundleId.get(bundleId);
                        app.put("installed", true);
                        app.put("installedVersion", installedApp.getShortVersion());
                    } else {
                        app.put("installed", false);
                        app.put("installedVersion", null);
                    }
                }
            }
        }

        return catalogs;
    }

    /**
     * Verifies that a given app (by bundleId) is in the catalog for the given account.
     */
    public boolean isAppInCatalog(UUID accountId, List<UUID> accountGroupIds, String bundleId) {
        List<Map<String, Object>> catalogs = getCatalogsForAccount(accountId, accountGroupIds);
        for (Map<String, Object> catalog : catalogs) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> apps = (List<Map<String, Object>>) catalog.get("apps");
            if (apps != null) {
                for (Map<String, Object> app : apps) {
                    if (bundleId.equals(app.get("bundleId"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

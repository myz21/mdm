package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.clients.BackCoreClient;
import com.arcyintel.arcops.apple_mdm.domains.AppleAccount;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.repositories.AppleAccountRepository;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentCatalogService;
import com.arcyintel.arcops.apple_mdm.services.app.AppCatalogService;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandSenderService;
import com.arcyintel.arcops.apple_mdm.services.device.DeviceLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AgentCatalogServiceImpl implements AgentCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(AgentCatalogServiceImpl.class);

    private final AppCatalogService appCatalogService;
    private final AppleAccountRepository appleAccountRepository;
    private final DeviceLookupService deviceLookupService;
    private final AppleCommandSenderService commandSenderService;
    private final BackCoreClient backCoreClient;

    @Override
    public List<Map<String, Object>> getCatalogForIdentity(UUID identityId, String deviceUdid) {
        Optional<AppleAccount> accountOpt = appleAccountRepository.findByIdentityId(identityId);
        if (accountOpt.isEmpty()) {
            return List.of();
        }

        AppleAccount account = accountOpt.get();
        UUID accountId = account.getId();

        // Resolve account group IDs from back_core
        List<UUID> accountGroupIds = backCoreClient.getAccountGroupIdsForIdentity(identityId);

        // Resolve device for enrichment (installed status)
        AppleDevice device = null;
        if (deviceUdid != null && !deviceUdid.isBlank()) {
            device = deviceLookupService.findByUdid(deviceUdid).orElse(null);
        }
        // Fallback: try first device from account
        if (device == null && !account.getDevices().isEmpty()) {
            device = account.getDevices().iterator().next();
        }

        List<Map<String, Object>> catalogs;
        if (device != null) {
            catalogs = appCatalogService.getEnrichedCatalogsForDevice(accountId, accountGroupIds, device);
            filterCatalogsByDevicePlatform(catalogs, device);
        } else {
            catalogs = appCatalogService.getCatalogsForAccount(accountId, accountGroupIds);
        }
        return catalogs;
    }

    /**
     * Filters catalog apps to only include those supporting the device's platform.
     * VPP platforms: "iOS", "macOS", "watchOS", "tvOS", "visionOS"
     * Enterprise platforms: "iPhone", "iPad", "Mac", "AppleTV"
     */
    @SuppressWarnings("unchecked")
    private void filterCatalogsByDevicePlatform(List<Map<String, Object>> catalogs, AppleDevice device) {
        String productName = Optional.ofNullable(device.getProductName()).orElse("").toLowerCase(Locale.ROOT);

        // Build the set of platform labels that match this device
        Set<String> matchingLabels = new HashSet<>();
        if (productName.contains("iphone") || productName.contains("ipod")) {
            matchingLabels.addAll(List.of("iOS", "iPhone"));
        } else if (productName.contains("ipad")) {
            matchingLabels.addAll(List.of("iOS", "iPadOS", "iPad"));
        } else if (productName.contains("mac")) {
            matchingLabels.addAll(List.of("macOS", "Mac"));
        } else if (productName.contains("apple tv") || productName.contains("appletv")) {
            matchingLabels.addAll(List.of("tvOS", "AppleTV"));
        } else if (productName.contains("vision") || productName.contains("reality")) {
            matchingLabels.addAll(List.of("visionOS"));
        } else if (productName.contains("watch")) {
            matchingLabels.addAll(List.of("watchOS"));
        } else {
            return; // Unknown platform — don't filter
        }

        for (Map<String, Object> catalog : catalogs) {
            List<Map<String, Object>> apps = (List<Map<String, Object>>) catalog.get("apps");
            if (apps != null) {
                apps.removeIf(app -> {
                    List<String> platforms = (List<String>) app.get("supportedPlatforms");
                    if (platforms == null || platforms.isEmpty()) return false; // keep if unknown
                    return platforms.stream().noneMatch(matchingLabels::contains);
                });
            }
        }
    }

    @Override
    public ResponseEntity<?> requestInstall(UUID identityId, String bundleId, String trackId, String deviceUdid) {
        if (bundleId == null || bundleId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "bundleId is required"));
        }

        Optional<AppleAccount> accountOpt = appleAccountRepository.findByIdentityId(identityId);
        if (accountOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found for identity");
        }

        UUID accountId = accountOpt.get().getId();

        // Resolve account group IDs from back_core
        List<UUID> accountGroupIds = backCoreClient.getAccountGroupIdsForIdentity(identityId);

        // Verify app is in catalog
        if (!appCatalogService.isAppInCatalog(accountId, accountGroupIds, bundleId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "App is not in your catalog."));
        }

        // Resolve device
        AppleDevice device = null;
        if (deviceUdid != null && !deviceUdid.isBlank()) {
            device = deviceLookupService.findByUdid(deviceUdid).orElse(null);
        }
        if (device == null && !accountOpt.get().getDevices().isEmpty()) {
            device = accountOpt.get().getDevices().iterator().next();
        }
        if (device == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No device found for installation."));
        }

        // Send install command
        try {
            Object identifier = trackId != null ? Integer.parseInt(trackId) : bundleId;
            commandSenderService.installApp(device.getUdid(), identifier, true, false, null);
            logger.info("Install command sent for bundleId={} to device={}", bundleId, device.getUdid());
            return ResponseEntity.accepted().body(Map.of("message", "Install command sent."));
        } catch (Exception e) {
            logger.error("Failed to send install command: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send install command."));
        }
    }
}

package com.arcyintel.arcops.apple_mdm.services.apple.abm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.arcyintel.arcops.apple_mdm.domains.ItunesAppMeta;
import com.arcyintel.arcops.apple_mdm.models.cert.vpp.asset.*;
import com.arcyintel.arcops.apple_mdm.models.cert.vpp.user.VppUser;
import com.arcyintel.arcops.apple_mdm.models.cert.vpp.user.VppUsersResponse;
import com.arcyintel.arcops.apple_mdm.repositories.ItunesAppMetaRepository;
import com.arcyintel.arcops.apple_mdm.services.apple.abm.AppleVppService;
import com.arcyintel.arcops.apple_mdm.utils.storage.StorageService;
import org.springframework.transaction.annotation.Transactional;
import com.arcyintel.arcops.commons.exceptions.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Primary
public class AppleVppServiceImpl implements AppleVppService {

    //logger
    private static final Logger logger = LoggerFactory.getLogger(AppleVppServiceImpl.class);
    // --- Services and State ---
    private final StorageService storageService;
    private final ItunesAppMetaRepository itunesAppMetaRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AppleVppServiceImpl(StorageService storageService, ItunesAppMetaRepository itunesAppMetaRepository, ObjectMapper objectMapper) {
        this.storageService = storageService;
        this.itunesAppMetaRepository = itunesAppMetaRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @Transactional
    public List<ItunesAppMeta> fetchVppAssets() throws Exception {
        logger.info("Starting to fetch all VPP assets from Apple...");

        List<VppAsset> allAssets = new ArrayList<>();
        int pageIndex = 0;
        boolean hasNextPage = true;

        HttpHeaders headers = buildVppAuthHeaders();

        while (hasNextPage) {
            String url = "https://vpp.itunes.apple.com/mdm/v2/assets?pageIndex=" + pageIndex;
            logger.debug("Requesting VPP assets page: {}", pageIndex);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<VppAssetsResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    VppAssetsResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.error("Failed to fetch VPP assets at page {}. Status: {}", pageIndex, response.getStatusCode());
                throw new BusinessException("VPP_ERROR", "VPP asset fetch failed at page " + pageIndex);
            }

            VppAssetsResponse vppResponse = response.getBody();
            List<VppAsset> pageAssets = vppResponse.getAssets();

            logger.info("Fetched {} assets from page {}.", pageAssets.size(), pageIndex);
            allAssets.addAll(pageAssets);

            if (vppResponse.getNextPageIndex() != null) {
                pageIndex = vppResponse.getNextPageIndex();
            } else {
                hasNextPage = false;
            }
        }

        logger.info("Successfully fetched all VPP assets. Total count: {}", allAssets.size());

        // Sync all assets (new ones will be created, existing ones will have license counts updated)
        try {
            if (!allAssets.isEmpty()) {
                VppAssetsResponse allAssetsResponse = new VppAssetsResponse();
                allAssetsResponse.setAssets(allAssets);
                syncItunesAppMeta(allAssetsResponse);

                // Remove apps that no longer exist in the current VPP account
                List<Long> currentTrackIds = allAssets.stream()
                        .map(asset -> Long.valueOf(asset.getAdamId()))
                        .collect(Collectors.toList());
                itunesAppMetaRepository.deleteSupportedPlatformsNotIn(currentTrackIds);
                itunesAppMetaRepository.deleteByTrackIdNotIn(currentTrackIds);
                logger.info("VPP sync complete: {} assets synced, stale records removed.", allAssets.size());
            } else {
                // No assets in current VPP account — clear all
                itunesAppMetaRepository.deleteAllSupportedPlatforms();
                itunesAppMetaRepository.deleteAllInBatch();
                logger.info("No VPP assets in current account. All local records cleared.");
            }
        } catch (Exception e) {
            logger.error("Failed during sync of VPP assets: {}", e.getMessage(), e);
        }


        // Return the full list fetched from Apple (even though we only synced new ones)
        return itunesAppMetaRepository.findAllByTrackIdIn(allAssets.stream().map(asset -> Long.valueOf(asset.getAdamId())).collect(Collectors.toList()));
    }

    @Override
    public void assignAssetsToDevices(List<AssetAssignRequest> requests) throws IOException {
        logger.info("Starting the process to assign assets to devices.");

        List<String> allSerials = new ArrayList<>();
        List<Map<String, String>> allAssets = new ArrayList<>();
        Set<String> uniqueAdamIds = new HashSet<>();

        logger.debug("Processing asset assignment requests.");
        for (AssetAssignRequest request : requests) {
            allSerials.add(request.getSerialNumber());
            uniqueAdamIds.addAll(request.getAdamIds());
        }
        logger.debug("Collected {} serial numbers and {} unique Adam IDs.", allSerials.size(), uniqueAdamIds.size());

        logger.info("Fetching VPP asset metadata from the database.");
        List<ItunesAppMeta> appMetas = itunesAppMetaRepository.findAllByTrackIdIn(
                uniqueAdamIds.stream().map(Long::valueOf).toList()
        );
        logger.debug("Fetched {} VPP asset metadata records from the database.", appMetas.size());

        for (ItunesAppMeta appMeta : appMetas) {
            allAssets.add(Map.of(
                    "adamId", String.valueOf(appMeta.getTrackId()),
                    "pricingParam", Optional.ofNullable(appMeta.getPricingParam()).orElse("STDQ")
            ));
        }
        logger.debug("Prepared asset data for {} assets.", allAssets.size());

        Map<String, Object> body = Map.of(
                "serialNumbers", allSerials,
                "assets", allAssets
        );
        logger.debug("Request body prepared for asset assignment: {}", body);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildVppAuthHeaders());
        logger.info("Sending request to Apple VPP API to assign assets to devices.");

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://vpp.itunes.apple.com/mdm/v2/assets/associate",
                HttpMethod.POST,
                entity,
                Map.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            logger.info("Assets assigned successfully. Event ID: {}", response.getBody().get("eventId"));
        } else {
            logger.error("Failed to assign assets. HTTP Status: {}, Response Body: {}", response.getStatusCode(), response.getBody());
            throw new BusinessException("VPP_ERROR", "Failed to assign assets: " + response.getStatusCode());
        }
        logger.info("Completed the process to assign assets to devices.");
    }

    @Override
    public void disassociateAssetsFromDevices(List<AssetAssignRequest> requests) throws IOException {
        logger.info("Starting the process to disassociate assets from devices.");

        List<String> allSerials = new ArrayList<>();
        List<Map<String, String>> allAssets = new ArrayList<>();
        Set<String> uniqueAdamIds = new HashSet<>();

        logger.debug("Processing asset disassociation requests.");
        for (AssetAssignRequest request : requests) {
            allSerials.add(request.getSerialNumber());
            uniqueAdamIds.addAll(request.getAdamIds());
        }
        logger.debug("Collected {} serial numbers and {} unique Adam IDs.", allSerials.size(), uniqueAdamIds.size());

        for (String adamId : uniqueAdamIds) {
            allAssets.add(Map.of("adamId", adamId));
        }
        logger.debug("Prepared asset data for {} assets.", allAssets.size());

        Map<String, Object> body = Map.of(
                "serialNumbers", allSerials,
                "assets", allAssets
        );
        logger.debug("Request body prepared for asset disassociation: {}", body);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildVppAuthHeaders());
        logger.info("Sending request to Apple VPP API to disassociate assets from devices.");

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://vpp.itunes.apple.com/mdm/v2/assets/disassociate",
                HttpMethod.POST,
                entity,
                Map.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            logger.info("Assets disassociated successfully. Event ID: {}", response.getBody().get("eventId"));
        } else {
            logger.error("Failed to disassociate assets. HTTP Status: {}, Response Body: {}", response.getStatusCode(), response.getBody());
            throw new BusinessException("VPP_ERROR", "Failed to disassociate assets: " + response.getStatusCode());
        }

        logger.info("Completed the process to disassociate assets from devices.");
    }

    @Override
    public List<Assignment> fetchAllAssignments() throws IOException {
        logger.info("Starting the process to fetch all assignments from VPP.");

        List<Assignment> allAssignments = new ArrayList<>();
        int pageIndex = 0;
        boolean hasNextPage = true;

        HttpHeaders headers = buildVppAuthHeaders();

        while (hasNextPage) {
            String url = "https://vpp.itunes.apple.com/mdm/v2/assignments?pageIndex=" + pageIndex;
            logger.debug("Fetching assignments from URL: {}", url);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<AssignmentResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    AssignmentResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.error("Failed to fetch assignments. HTTP Status: {}, Response Body: {}", response.getStatusCode(), response.getBody());
                throw new BusinessException("VPP_ERROR", "Failed to fetch assignments: " + response.getStatusCode());
            }

            AssignmentResponse assignmentResponse = response.getBody();
            allAssignments.addAll(assignmentResponse.getAssignments());
            logger.debug("Fetched {} assignments from the current page.", assignmentResponse.getAssignments().size());

            if (assignmentResponse.getNextPageIndex() != null) {
                pageIndex = assignmentResponse.getNextPageIndex();
                logger.debug("Moving to the next page. Page index: {}", pageIndex);
            } else {
                hasNextPage = false;
            }
        }

        logger.info("Fetched total {} assignments from VPP.", allAssignments.size());
        return allAssignments;
    }

    @Override
    public void revokeAssetsFromDevice(String serialNumber) throws IOException {
        logger.info("Starting the process to revoke all VPP assets from device with serial number: {}", serialNumber);

        // Request body
        Map<String, Object> body = Map.of(
                "serialNumbers", List.of(serialNumber)
        );
        logger.debug("Request body prepared for revoking assets: {}", body);

        // Authorization headers
        HttpHeaders headers = buildVppAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        logger.debug("Authorization headers prepared for revoking assets.");

        // HTTP entity with body and headers
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        logger.debug("HTTP entity created with request body and headers.");

        // Send the request
        logger.info("Sending request to Apple VPP API to revoke assets for device with serial number: {}", serialNumber);
        ResponseEntity<Map> response = restTemplate.exchange(
                "https://vpp.itunes.apple.com/mdm/v2/assets/revoke",
                HttpMethod.POST,
                entity,
                Map.class
        );

        // Handle response
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            logger.info("Successfully revoked assets for device with serial number: {}. Event ID: {}", serialNumber, response.getBody().get("eventId"));
        } else {
            logger.error("Failed to revoke assets for device with serial number: {}. HTTP Status: {}, Response Body: {}", serialNumber, response.getStatusCode(), response.getBody());
            throw new BusinessException("VPP_ERROR", "Revoke assets failed with status: " + response.getStatusCode());
        }

        logger.info("Completed the process to revoke assets for device with serial number: {}", serialNumber);
    }

    @Override
    public List<VppUser> fetchAllVppUsers() throws IOException {
        logger.info("Fetching all VPP users from Apple VPP API (paginated)...");

        List<VppUser> allUsers = new ArrayList<>();
        int pageIndex = 0;
        boolean hasNext = true;

        while (hasNext) {
            URI uri = URI.create("https://vpp.itunes.apple.com/mdm/v2/users?pageIndex=" + pageIndex);
            HttpEntity<Void> request = new HttpEntity<>(buildVppAuthHeaders());

            ResponseEntity<VppUsersResponse> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    request,
                    VppUsersResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                VppUsersResponse body = response.getBody();
                logger.info("Fetched page {} with {} users", body.getCurrentPageIndex(), body.getUsers().size());

                allUsers.addAll(body.getUsers());

                if (body.getNextPageIndex() != null) {
                    pageIndex = body.getNextPageIndex();
                } else {
                    hasNext = false;
                }
            } else {
                logger.error("Failed to fetch VPP users. Status: {}", response.getStatusCode());
                throw new BusinessException("VPP_ERROR", "VPP users fetch failed with status: " + response.getStatusCode());
            }
        }

        logger.info("Successfully fetched total {} users from all pages", allUsers.size());
        return allUsers;
    }

    // --- Private Methods ---

    @Override
    @Async
    public void syncItunesAppMeta(VppAssetsResponse vppAssetsResponse) throws Exception {
        logger.info("Starting the synchronization of iTunes app metadata with the VPP assets.");

        List<String> adamIds = vppAssetsResponse.getAssets().stream()
                .map(VppAsset::getAdamId)
                .filter(Objects::nonNull)
                .toList();

        // https://itunes.apple.com/lookup?id=310633997,408709785,377298193&country=us
        String ids = String.join(",", adamIds);
        String url = "https://itunes.apple.com/lookup?id=" + ids;
        logger.info("Fetching iTunes app metadata from URL: {}", url);

        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(url),
                HttpMethod.GET,
                null,
                String.class
        );

        ItunesLookupResponse lookupResponse = objectMapper.readValue(response.getBody(), ItunesLookupResponse.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            logger.info("Successfully fetched iTunes app metadata. Count: {}", lookupResponse.getResultCount());
            List<ItunesAppMeta> itunesAppMetaList = lookupResponse.getResults();

            // Update the licence count for each app
            for (ItunesAppMeta itunesAppMeta : itunesAppMetaList) {
                Optional<VppAsset> vppAsset = vppAssetsResponse.getAssets().stream()
                        .filter(asset -> Long.valueOf(asset.getAdamId()).equals(itunesAppMeta.getTrackId()))
                        .findFirst();

                if (vppAsset.isPresent()) {
                    itunesAppMeta.setTotalCount(vppAsset.get().getTotalCount());
                    itunesAppMeta.setRetiredCount(vppAsset.get().getRetiredCount());
                    itunesAppMeta.setAvailableCount(vppAsset.get().getAvailableCount());
                    itunesAppMeta.setAssignedCount(vppAsset.get().getAssignedCount());
                    itunesAppMeta.setPricingParam(vppAsset.get().getPricingParam());
                    itunesAppMeta.setSupportedPlatforms(vppAsset.get().getSupportedPlatforms());
                    logger.debug("Updated license count for app with track ID: {}. Count: {}", itunesAppMeta.getTrackId(), itunesAppMeta.getTotalCount());
                } else {
                    logger.warn("No matching VPP asset found for app with track ID: {}. License count not updated.", itunesAppMeta.getTrackId());
                }
            }

            // Save the iTunes app metadata to the database
            for (ItunesAppMeta itunesAppMeta : itunesAppMetaList) {

                // Check if the app already exists in the database
                Optional<ItunesAppMeta> existingAppMeta = itunesAppMetaRepository.findByTrackId(itunesAppMeta.getTrackId());

                if (existingAppMeta.isPresent()) {
                    logger.debug("iTunes app metadata already exists for track ID: {}. Updating license counts.", itunesAppMeta.getTrackId());
                    ItunesAppMeta existing = existingAppMeta.get();
                    existing.setTotalCount(itunesAppMeta.getTotalCount());
                    existing.setRetiredCount(itunesAppMeta.getRetiredCount());
                    existing.setAvailableCount(itunesAppMeta.getAvailableCount());
                    existing.setAssignedCount(itunesAppMeta.getAssignedCount());
                    existing.setPricingParam(itunesAppMeta.getPricingParam());
                    existing.setSupportedPlatforms(itunesAppMeta.getSupportedPlatforms());
                    itunesAppMetaRepository.save(existing);
                    continue;
                } else {
                    logger.debug("New iTunes app metadata found for track ID: {}. Saving new record.", itunesAppMeta.getTrackId());
                }

                itunesAppMetaRepository.save(itunesAppMeta);
                logger.debug("Saved iTunes app metadata: {}", itunesAppMeta);
            }
        } else {
            logger.error("Failed to fetch iTunes app metadata. Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody());
            throw new BusinessException("VPP_ERROR", "iTunes app metadata fetch failed with status: " + response.getStatusCode());
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getClientConfig() throws Exception {
        HttpHeaders headers = buildVppAuthHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "https://vpp.itunes.apple.com/mdm/v2/client/config",
                HttpMethod.GET,
                entity,
                Map.class
        );
        Map<String, Object> body = response.getBody();
        if (body == null) return Map.of();
        Map<String, String> result = new HashMap<>();
        if (body.containsKey("orgName")) result.put("orgName", String.valueOf(body.get("orgName")));
        if (body.containsKey("locationName")) result.put("locationName", String.valueOf(body.get("locationName")));
        if (body.containsKey("countryCode")) result.put("countryCode", String.valueOf(body.get("countryCode")));
        if (body.containsKey("uId")) result.put("uId", String.valueOf(body.get("uId")));
        return result;
    }

    private HttpHeaders buildVppAuthHeaders() throws IOException {
        logger.info("Starting to build VPP authentication headers.");

        Resource vppTokenResource = storageService.loadAsResource("vpp_token.vpp", "apple");
        logger.debug("Loaded VPP token resource from file: vpp_token.vpp");

        String vppToken;
        try (InputStream is = vppTokenResource.getInputStream()) {
            vppToken = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        logger.debug("VPP token successfully read and trimmed.");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        logger.debug("Set Content-Type header to application/json.");

        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        logger.debug("Set Accept header to application/json.");

        headers.setBearerAuth(vppToken);
        logger.debug("Set Bearer token for authentication.");

        logger.info("VPP authentication headers built successfully.");
        return headers;
    }

}

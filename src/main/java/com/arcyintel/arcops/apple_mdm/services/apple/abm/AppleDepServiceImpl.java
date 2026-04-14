package com.arcyintel.arcops.apple_mdm.services.apple.abm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.arcyintel.arcops.apple_mdm.domains.AbmDevice;
import com.arcyintel.arcops.apple_mdm.domains.AbmProfile;
import com.arcyintel.arcops.apple_mdm.models.api.device.AbmDeviceSummaryDto;
import com.arcyintel.arcops.apple_mdm.models.cert.abm.*;
import com.arcyintel.arcops.apple_mdm.repositories.AbmDeviceRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AbmProfileRepository;
import com.arcyintel.arcops.apple_mdm.services.apple.abm.AppleDepService;
import com.arcyintel.arcops.apple_mdm.services.mappers.AbmDeviceMapper;
import com.arcyintel.arcops.apple_mdm.utils.storage.StorageService;
import com.arcyintel.arcops.commons.exceptions.BusinessException;
import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Primary
public class AppleDepServiceImpl implements AppleDepService {

    private static final Logger logger = LoggerFactory.getLogger(AppleDepServiceImpl.class);

    private static final String BASE_URL = "https://mdmenrollment.apple.com";
    private static final String SERVER_DEVICES_PATH = "/server/devices";
    private static final String PROFILE_PATH = "/profile";
    private static final String PROFILE_DEVICES_PATH = "/profile/devices";
    private static final String SESSION_PATH = "/session";
    private static final String SERVER_TOKEN_FILE = "server_token.json";
    private static final String USER_AGENT = "MyMDMServer/1.0";
    private static final String SERVER_PROTOCOL_VERSION = "3";
    private static final long SESSION_TOKEN_EXPIRATION_MS = 3 * 60 * 1000;

    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AbmDeviceRepository abmDeviceRepository;
    private final AbmProfileRepository abmProfileRepository;
    private final AbmDeviceMapper abmDeviceMapper;

    @Value("${host}")
    private String apiHost;

    private String authSessionToken;
    private long authSessionTokenExpiresAt;

    public AppleDepServiceImpl(StorageService storageService,
                               ObjectMapper objectMapper,
                               AbmDeviceRepository abmDeviceRepository,
                               AbmProfileRepository abmProfileRepository,
                               AbmDeviceMapper abmDeviceMapper) {
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.abmDeviceRepository = abmDeviceRepository;
        this.abmProfileRepository = abmProfileRepository;
        this.abmDeviceMapper = abmDeviceMapper;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public List<Device> fetchDevices() throws Exception {
        ServerToken serverToken = loadServerToken();
        ensureValidSessionToken(serverToken);

        List<Device> allDevices = new ArrayList<>();
        String cursor = null;
        boolean moreToFollow = true;

        while (moreToFollow) {
            Map<String, Object> requestBody = new HashMap<>();
            if (cursor != null) {
                requestBody.put("cursor", cursor);
            }

            ResponseEntity<FetchDevicesResponse> response = restTemplate.exchange(
                    URI.create(BASE_URL + SERVER_DEVICES_PATH),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, buildAuthHeaders()),
                    FetchDevicesResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                FetchDevicesResponse body = response.getBody();
                allDevices.addAll(body.getDevices());
                moreToFollow = Boolean.TRUE.equals(body.getMoreToFollow());
                cursor = body.getCursor();
                logger.info("Fetched {} devices so far.", allDevices.size());
            } else {
                throw new BusinessException("ABM_ERROR", "Failed to fetch devices: " + response.getStatusCode());
            }
        }

        logger.info("Total devices fetched from ABM: {}", allDevices.size());
        syncDevicesToDb(allDevices);

        return allDevices;
    }

    @Override
    public DeviceStatusResponse disownDevices(List<String> serialNumbers) throws Exception {
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "The request did not contain any devices");
        }

        ServerToken serverToken = loadServerToken();
        ensureValidSessionToken(serverToken);

        DeviceListRequest req = new DeviceListRequest();
        req.setDevices(serialNumbers);

        ResponseEntity<DeviceStatusResponse> resp = restTemplate.exchange(
                URI.create(BASE_URL + "/devices/disown"),
                HttpMethod.POST,
                new HttpEntity<>(req, buildAuthHeaders()),
                DeviceStatusResponse.class
        );

        if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
            logger.info("Successfully disowned devices. Event ID: '{}'", resp.getBody().getEventId());
            return resp.getBody();
        }
        throw new BusinessException("ABM_ERROR", "Failed to disown devices: " + resp.getStatusCode());
    }

    @Override
    public ProfileResponse createAndSaveProfile(Profile profileRequest) throws Exception {
        if (profileRequest.getProfileName() == null || profileRequest.getProfileName().isBlank()) {
            profileRequest.setProfileName("Default DEP Profile");
        }

        fillProfileDefaults(profileRequest);

        logger.info("Creating DEP profile. configuration_web_url={}, await_device_configured={}, url={}",
                profileRequest.getConfigurationWebURL(), profileRequest.getAwaitDeviceConfigured(), profileRequest.getUrl());

        ServerToken serverToken = loadServerToken();
        ensureValidSessionToken(serverToken);

        ResponseEntity<ProfileResponse> response = restTemplate.exchange(
                URI.create(BASE_URL + PROFILE_PATH),
                HttpMethod.POST,
                new HttpEntity<>(profileRequest, buildAuthHeaders()),
                ProfileResponse.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            ProfileResponse profileResponse = response.getBody();
            profileRequest.setProfileUUID(profileResponse.getProfileUUID());
            saveProfileToDb(profileRequest);
            logger.info("Profile created and saved with UUID: {}", profileResponse.getProfileUUID());
            return profileResponse;
        }
        throw new BusinessException("ABM_ERROR", "Failed to create profile: " + response.getStatusCode());
    }

    @Override
    public Profile getProfile(String profileUuid) {
        if (profileUuid == null || profileUuid.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "profile_uuid is missing");
        }

        String url = BASE_URL + PROFILE_PATH + "?profile_uuid=" + URLEncoder.encode(profileUuid, StandardCharsets.UTF_8);

        ResponseEntity<Profile> response = restTemplate.exchange(
                URI.create(url),
                HttpMethod.GET,
                new HttpEntity<>(buildAuthHeaders()),
                Profile.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        } else if (response.getStatusCode().value() == 400) {
            throw new BusinessException("ABM_ERROR", "Profile not found or profile_uuid required");
        }
        throw new BusinessException("ABM_ERROR", "Failed to fetch profile: " + response.getStatusCode());
    }

    @Override
    public List<Map<String, Object>> listProfiles() {
        List<AbmProfile> profiles = abmProfileRepository.findAll();
        return profiles.stream().map(profile -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", profile.getId());
            map.put("profileUuid", profile.getProfileUuid());
            map.put("profileName", profile.getProfileName());
            map.put("url", profile.getUrl());
            map.put("configurationWebUrl", profile.getConfigurationWebUrl());
            map.put("allowPairing", profile.getAllowPairing());
            map.put("isSupervised", profile.getIsSupervised());
            map.put("isMultiUser", profile.getIsMultiUser());
            map.put("isMandatory", profile.getIsMandatory());
            map.put("awaitDeviceConfigured", profile.getAwaitDeviceConfigured());
            map.put("isMdmRemovable", profile.getIsMdmRemovable());
            map.put("autoAdvanceSetup", profile.getAutoAdvanceSetup());
            map.put("supportPhoneNumber", profile.getSupportPhoneNumber());
            map.put("supportEmailAddress", profile.getSupportEmailAddress());
            map.put("orgMagic", profile.getOrgMagic());
            map.put("department", profile.getDepartment());
            map.put("language", profile.getLanguage());
            map.put("region", profile.getRegion());
            map.put("skipSetupItems", profile.getSkipSetupItems());
            map.put("creationDate", profile.getCreationDate());
            map.put("lastModifiedDate", profile.getLastModifiedDate());
            map.put("deviceCount", abmDeviceRepository.countByAbmStatusAndProfile("ACTIVE", profile));
            return map;
        }).toList();
    }

    @Override
    public ProfileResponse assignProfileToDevices(String profileUuid) throws Exception {
        if (profileUuid == null || profileUuid.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "profile_uuid is missing");
        }

        AbmProfile abmProfile = abmProfileRepository.findByProfileUuid(profileUuid)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found in DB: " + profileUuid));

        // Get ACTIVE devices, skip ones that already have this exact profile
        List<AbmDevice> allDevices = abmDeviceRepository.findAllByAbmStatus("ACTIVE");
        List<AbmDevice> devicesToAssign = allDevices.stream()
                .filter(d -> d.getProfile() == null || !profileUuid.equals(d.getProfile().getProfileUuid()))
                .toList();

        if (devicesToAssign.isEmpty()) {
            logger.info("All {} devices already have profile '{}'. Nothing to assign.", allDevices.size(), profileUuid);
            ProfileResponse emptyResponse = new ProfileResponse();
            emptyResponse.setDevices(Map.of());
            return emptyResponse;
        }

        List<String> serialsToAssign = devicesToAssign.stream()
                .map(AbmDevice::getSerialNumber)
                .toList();

        long skippedCount = allDevices.size() - devicesToAssign.size();
        logger.info("Assigning profile '{}' to {} devices ({} already have this profile, skipped).",
                profileUuid, serialsToAssign.size(), skippedCount);

        ServerToken serverToken = loadServerToken();
        ensureValidSessionToken(serverToken);

        Map<String, Object> requestBody = Map.of(
                "profile_uuid", profileUuid,
                "devices", serialsToAssign
        );

        ResponseEntity<ProfileResponse> response = restTemplate.exchange(
                URI.create(BASE_URL + PROFILE_DEVICES_PATH),
                HttpMethod.PUT,
                new HttpEntity<>(requestBody, buildAuthHeaders()),
                ProfileResponse.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            for (AbmDevice device : devicesToAssign) {
                device.setProfile(abmProfile);
                device.setProfileStatus("assigned");
            }
            abmDeviceRepository.saveAll(devicesToAssign);
            logger.info("Successfully assigned profile '{}' to {} devices.", profileUuid, serialsToAssign.size());
            return response.getBody();
        }
        throw new BusinessException("ABM_ERROR", "Failed to assign profile: " + response.getStatusCode());
    }

    @Override
    public ClearProfileResponse removeProfileFromDevices(ClearProfileRequest req) throws Exception {
        ServerToken serverToken = loadServerToken();
        ensureValidSessionToken(serverToken);

        if (req.getProfileUuid() == null || req.getProfileUuid().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "profile_uuid is missing");
        }
        if (req.getDevices() == null || req.getDevices().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "The request did not contain any device IDs");
        }

        logger.info("Removing profile '{}' from devices: {}", req.getProfileUuid(), req.getDevices());

        HttpEntity<ClearProfileRequest> entity = new HttpEntity<>(req, buildAuthHeaders());

        ResponseEntity<ClearProfileResponse> resp = restTemplate.exchange(
                URI.create(BASE_URL + PROFILE_PATH + "/devices"),
                HttpMethod.DELETE,
                entity,
                ClearProfileResponse.class
        );

        if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
            // Update DB: clear profile from devices
            List<AbmDevice> devices = abmDeviceRepository.findBySerialNumberIn(req.getDevices());
            for (AbmDevice device : devices) {
                device.setProfile(null);
                device.setProfileStatus(null);
            }
            abmDeviceRepository.saveAll(devices);
            logger.info("Successfully removed profile from {} devices.", devices.size());

            // Reassign if requested
            if (req.getReassignProfileUuid() != null && !req.getReassignProfileUuid().isBlank()) {
                logger.info("Reassigning profile '{}' to devices.", req.getReassignProfileUuid());
                reassignProfileToDevices(req.getReassignProfileUuid(), req.getDevices());
            }

            return resp.getBody();
        }
        throw new BusinessException("ABM_ERROR", "Failed to remove profile: " + resp.getStatusCode());
    }

    @Override
    public void deleteProfile(String profileUuid) {
        if (profileUuid == null || profileUuid.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "profile_uuid is required");
        }

        AbmProfile profile = abmProfileRepository.findByProfileUuid(profileUuid)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found: " + profileUuid));

        // Check if any active devices are assigned to this profile
        long deviceCount = abmDeviceRepository.countByAbmStatusAndProfile("ACTIVE", profile);
        if (deviceCount > 0) {
            throw new BusinessException("ABM_ERROR", "Cannot delete profile '" + profile.getProfileName()
                    + "': " + deviceCount + " device(s) still assigned. Remove the profile from devices first.");
        }

        abmProfileRepository.delete(profile);
        logger.info("Profile '{}' ({}) deleted from local database.", profile.getProfileName(), profileUuid);
    }

    // --- Private helpers ---

    private void reassignProfileToDevices(String profileUuid, List<String> serialNumbers) throws Exception {
        AbmProfile newProfile = abmProfileRepository.findByProfileUuid(profileUuid)
                .orElseThrow(() -> new EntityNotFoundException("Reassign profile not found: " + profileUuid));

        Map<String, Object> requestBody = Map.of(
                "profile_uuid", profileUuid,
                "devices", serialNumbers
        );

        ResponseEntity<ProfileResponse> response = restTemplate.exchange(
                URI.create(BASE_URL + PROFILE_DEVICES_PATH),
                HttpMethod.PUT,
                new HttpEntity<>(requestBody, buildAuthHeaders()),
                ProfileResponse.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            List<AbmDevice> devices = abmDeviceRepository.findBySerialNumberIn(serialNumbers);
            for (AbmDevice device : devices) {
                device.setProfile(newProfile);
                device.setProfileStatus("assigned");
            }
            abmDeviceRepository.saveAll(devices);
            logger.info("Successfully reassigned profile '{}' to {} devices.", profileUuid, serialNumbers.size());
        } else {
            logger.error("Failed to reassign profile '{}' to devices. Status: {}", profileUuid, response.getStatusCode());
        }
    }

    private void syncDevicesToDb(List<Device> devices) {
        // Collect serial numbers from ABM response
        Set<String> abmSerialNumbers = devices.stream()
                .map(Device::getSerialNumber)
                .collect(Collectors.toSet());

        // Upsert devices from ABM
        for (Device device : devices) {
            AbmDevice abmDevice = abmDeviceRepository.findBySerialNumber(device.getSerialNumber())
                    .orElse(new AbmDevice());

            abmDevice.setSerialNumber(device.getSerialNumber());
            abmDevice.setModel(device.getModel());
            abmDevice.setDescription(device.getDescription());
            abmDevice.setColor(device.getColor());
            abmDevice.setAssetTag(device.getAssetTag());
            abmDevice.setOs(device.getOs());
            abmDevice.setDeviceFamily(device.getDeviceFamily());
            abmDevice.setProfileStatus(device.getProfileStatus());
            abmDevice.setProfileAssignTime(device.getProfileAssignTime());
            abmDevice.setProfilePushTime(device.getProfilePushTime());
            abmDevice.setDeviceAssignedDate(device.getDeviceAssignedDate());
            abmDevice.setDeviceAssignedBy(device.getDeviceAssignedBy());
            abmDevice.setAbmStatus("ACTIVE");

            // Link profile if device has one from Apple
            if (device.getProfileUUID() != null && !device.getProfileUUID().isBlank()) {
                abmProfileRepository.findByProfileUuid(device.getProfileUUID())
                        .ifPresent(abmDevice::setProfile);
            } else {
                abmDevice.setProfile(null);
            }

            abmDeviceRepository.save(abmDevice);
        }

        // Mark devices no longer in ABM as REMOVED
        List<AbmDevice> activeDevices = abmDeviceRepository.findAllByAbmStatus("ACTIVE");
        int removedCount = 0;
        for (AbmDevice dbDevice : activeDevices) {
            if (!abmSerialNumbers.contains(dbDevice.getSerialNumber())) {
                dbDevice.setAbmStatus("REMOVED");
                dbDevice.setProfile(null);
                abmDeviceRepository.save(dbDevice);
                removedCount++;
            }
        }

        logger.info("Synced {} devices to database. {} devices marked as REMOVED.", devices.size(), removedCount);
    }

    private void saveProfileToDb(Profile profile) {
        AbmProfile abmProfile = abmProfileRepository.findByProfileUuid(profile.getProfileUUID())
                .orElse(new AbmProfile());

        abmProfile.setProfileUuid(profile.getProfileUUID());
        abmProfile.setProfileName(profile.getProfileName());
        abmProfile.setUrl(profile.getUrl());
        abmProfile.setConfigurationWebUrl(profile.getConfigurationWebURL());
        abmProfile.setAllowPairing(profile.getAllowPairing());
        abmProfile.setIsSupervised(profile.getIsSupervised());
        abmProfile.setIsMultiUser(profile.getIsMultiUser());
        abmProfile.setIsMandatory(profile.getIsMandatory());
        abmProfile.setAwaitDeviceConfigured(profile.getAwaitDeviceConfigured());
        abmProfile.setIsMdmRemovable(profile.getIsMDMRemovable());
        abmProfile.setAutoAdvanceSetup(profile.getAutoAdvanceSetup());
        abmProfile.setSupportPhoneNumber(profile.getSupportPhoneNumber());
        abmProfile.setSupportEmailAddress(profile.getSupportEmailAddress());
        abmProfile.setOrgMagic(profile.getOrgMagic());
        abmProfile.setDepartment(profile.getDepartment());
        abmProfile.setLanguage(profile.getLanguage());
        abmProfile.setRegion(profile.getRegion());
        abmProfile.setSkipSetupItems(profile.getSkipSetupItems());
        abmProfile.setAnchorCerts(profile.getAnchorCerts());
        abmProfile.setSupervisingHostCerts(profile.getSupervisingHostCerts());

        abmProfileRepository.save(abmProfile);
        logger.info("Profile '{}' saved to database.", profile.getProfileUUID());
    }

    private void fillProfileDefaults(Profile profileRequest) {
        // Boolean fields - default to false if null
        profileRequest.setIsMandatory(profileRequest.getIsMandatory() != null && profileRequest.getIsMandatory());
        profileRequest.setIsMDMRemovable(profileRequest.getIsMDMRemovable() != null && profileRequest.getIsMDMRemovable());
        profileRequest.setIsSupervised(profileRequest.getIsSupervised() != null && profileRequest.getIsSupervised());
        profileRequest.setAllowPairing(profileRequest.getAllowPairing() != null && profileRequest.getAllowPairing());
        profileRequest.setAutoAdvanceSetup(profileRequest.getAutoAdvanceSetup() != null && profileRequest.getAutoAdvanceSetup());
        profileRequest.setAwaitDeviceConfigured(profileRequest.getAwaitDeviceConfigured() != null && profileRequest.getAwaitDeviceConfigured());
        profileRequest.setIsMultiUser(profileRequest.getIsMultiUser() != null && profileRequest.getIsMultiUser());

        // Internal fields - not user configurable
        profileRequest.setOrgMagic("");
        profileRequest.setAnchorCerts(null);
        profileRequest.setSupervisingHostCerts(null);

        // URL - always set from config
        profileRequest.setUrl(apiHost + "/mdm/enrollment");

        // Configuration Web URL - auto-set if use_web_auth is enabled (independent from await_device_configured)
        if (Boolean.TRUE.equals(profileRequest.getUseWebAuth())) {
            profileRequest.setConfigurationWebURL(apiHost + "/mdm/enrollment/web-auth");
        } else {
            profileRequest.setConfigurationWebURL(null);
        }

        // Clear the frontend-only flag before sending to Apple
        profileRequest.setUseWebAuth(null);
    }

    // --- Auth helpers ---

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", USER_AGENT);
        headers.set("X-Server-Protocol-Version", SERVER_PROTOCOL_VERSION);
        headers.set("X-ADM-Auth-Session", authSessionToken);
        return headers;
    }

    private void ensureValidSessionToken(ServerToken serverToken) throws Exception {
        if (authSessionToken == null || System.currentTimeMillis() > authSessionTokenExpiresAt) {
            obtainNewSessionToken(serverToken);
        }
    }

    private void obtainNewSessionToken(ServerToken serverToken) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", buildOAuth1Header(serverToken, BASE_URL + SESSION_PATH, "GET"));
        headers.set("User-Agent", USER_AGENT);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.set("X-Server-Protocol-Version", SERVER_PROTOCOL_VERSION);

        ResponseEntity<Map> response = restTemplate.exchange(
                URI.create(BASE_URL + SESSION_PATH),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            this.authSessionToken = (String) response.getBody().get("auth_session_token");
            this.authSessionTokenExpiresAt = System.currentTimeMillis() + SESSION_TOKEN_EXPIRATION_MS;
            logger.info("Obtained new session token.");
        } else {
            throw new BusinessException("ABM_ERROR", "Failed to obtain session token: " + response.getStatusCode());
        }
    }

    private ServerToken loadServerToken() throws Exception {
        Resource serverTokenResource = storageService.loadAsResource(SERVER_TOKEN_FILE, "apple");
        try (InputStream is = serverTokenResource.getInputStream()) {
            return objectMapper.readValue(is, ServerToken.class);
        }
    }

    private String buildOAuth1Header(ServerToken serverToken, String url, String method) throws Exception {
        String oauthNonce = UUID.randomUUID().toString().replace("-", "");
        String oauthTimestamp = String.valueOf(System.currentTimeMillis() / 1000);

        Map<String, String> params = new TreeMap<>(Map.of(
                "oauth_consumer_key", serverToken.getConsumerKey(),
                "oauth_token", serverToken.getAccessToken(),
                "oauth_nonce", oauthNonce,
                "oauth_timestamp", oauthTimestamp,
                "oauth_signature_method", "HMAC-SHA1",
                "oauth_version", "1.0"
        ));

        String baseString = method.toUpperCase() + "&" + encode(url) + "&" + encode(buildParameterString(params));
        String signingKey = encode(serverToken.getConsumerSecret()) + "&" + encode(serverToken.getAccessSecret());
        String signature = generateSignature(baseString, signingKey);
        params.put("oauth_signature", signature);

        return "OAuth " + params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=\"" + encode(e.getValue()) + "\"")
                .collect(Collectors.joining(", "));
    }

    private String encode(String value) {
        if (value == null) return null;
        if (value.isEmpty()) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private String buildParameterString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String generateSignature(String baseString, String key) throws Exception {
        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(signingKey);
        byte[] rawSignature = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(rawSignature);
    }

    @Override
    public AbmDeviceSummaryDto getDeviceSummary() {
        long totalCount = abmDeviceRepository.countByAbmStatus("ACTIVE");
        long pushedCount = abmDeviceRepository.countByAbmStatusAndProfileStatus("ACTIVE", "pushed");
        long assignedCount = abmDeviceRepository.countByAbmStatusAndProfileStatus("ACTIVE", "assigned");
        long emptyCount = totalCount - pushedCount - assignedCount;

        List<AbmDevice> recent = abmDeviceRepository.findTop10ByAbmStatusOrderByCreationDateDesc("ACTIVE");
        List<AbmDeviceSummaryDto.RecentDevice> recentDevices = recent.stream()
                .map(abmDeviceMapper::toRecentDevice)
                .toList();

        return AbmDeviceSummaryDto.builder()
                .totalCount(totalCount)
                .pushedCount(pushedCount)
                .assignedCount(assignedCount)
                .emptyCount(emptyCount)
                .recentDevices(recentDevices)
                .build();
    }
}
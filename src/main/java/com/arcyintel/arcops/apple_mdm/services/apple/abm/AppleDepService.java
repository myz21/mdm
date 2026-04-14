package com.arcyintel.arcops.apple_mdm.services.apple.abm;

import com.arcyintel.arcops.apple_mdm.domains.AbmProfile;
import com.arcyintel.arcops.apple_mdm.models.api.device.AbmDeviceSummaryDto;
import com.arcyintel.arcops.apple_mdm.models.cert.abm.*;

import java.util.List;
import java.util.Map;

public interface AppleDepService {
    // Device Operations
    List<Device> fetchDevices() throws Exception;

    DeviceStatusResponse disownDevices(List<String> serialNumbers) throws Exception;

    // Profile Operations
    ProfileResponse createAndSaveProfile(Profile profileRequest) throws Exception;

    Profile getProfile(String profileUuid) throws Exception;

    List<Map<String, Object>> listProfiles();

    ProfileResponse assignProfileToDevices(String profileUuid) throws Exception;

    ClearProfileResponse removeProfileFromDevices(ClearProfileRequest req) throws Exception;

    /**
     * Deletes a profile from the local database.
     * Only allowed if no devices are currently assigned to this profile.
     * Apple DEP API does not support profile deletion, so this is local-only.
     */
    void deleteProfile(String profileUuid);

    /**
     * Returns a summary of ABM devices: counts by profile status and recent devices.
     */
    AbmDeviceSummaryDto getDeviceSummary();
}
package com.arcyintel.arcops.apple_mdm.services.device;

import com.arcyintel.arcops.apple_mdm.models.api.device.GetAppleDeviceDetailDto;

import java.util.UUID;

/**
 * Service for fetching detailed Apple device information.
 */
public interface AppleDeviceDetailService {

    /**
     * Get detailed device information by device ID.
     *
     * @param deviceId the device UUID
     * @return detailed device DTO with properties, command history, and installed apps
     */
    GetAppleDeviceDetailDto getDeviceDetail(UUID deviceId);

    /**
     * Get detailed device information by UDID.
     *
     * @param udid the device UDID
     * @return detailed device DTO with properties, command history, and installed apps
     */
    GetAppleDeviceDetailDto getDeviceDetailByUdid(String udid);
}

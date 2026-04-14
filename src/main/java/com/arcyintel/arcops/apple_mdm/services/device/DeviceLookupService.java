package com.arcyintel.arcops.apple_mdm.services.device;

import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Centralized service for looking up AppleDevice entities by UDID or serial number.
 * Eliminates repeated findByUdid + null/exception handling across controllers and services.
 */
@Service
@RequiredArgsConstructor
public class DeviceLookupService {

    private final AppleDeviceRepository appleDeviceRepository;

    /**
     * Finds a device by UDID, throwing NOT_FOUND if not present.
     */
    public AppleDevice getByUdid(String udid) {
        return appleDeviceRepository.findByUdid(udid)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + udid));
    }

    /**
     * Finds a device by UDID, returning Optional.
     */
    public Optional<AppleDevice> findByUdid(String udid) {
        return appleDeviceRepository.findByUdid(udid);
    }

    /**
     * Finds a device by serial number, throwing NOT_FOUND if not present.
     */
    public AppleDevice getBySerialNumber(String serial) {
        return appleDeviceRepository.findBySerialNumber(serial)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + serial));
    }
}

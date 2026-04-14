package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AppleDeviceLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppleDeviceLocationRepository extends JpaRepository<AppleDeviceLocation, String> {
    // Useful to get location history for a specific device
    List<AppleDeviceLocation> findByAppleDevice_IdOrderByTimestampDesc(UUID deviceId);
}
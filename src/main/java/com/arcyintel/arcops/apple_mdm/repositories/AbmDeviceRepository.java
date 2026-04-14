package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.AbmDevice;
import com.arcyintel.arcops.apple_mdm.domains.AbmProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AbmDeviceRepository extends JpaRepository<AbmDevice, UUID> {
    Optional<AbmDevice> findBySerialNumber(String serialNumber);
    List<AbmDevice> findByProfileIsNull();
    List<AbmDevice> findBySerialNumberIn(List<String> serialNumbers);
    long countByProfileStatus(String profileStatus);
    long countByProfile(AbmProfile profile);
    List<AbmDevice> findTop10ByOrderByCreationDateDesc();

    List<AbmDevice> findAllByAbmStatus(String abmStatus);
    long countByAbmStatus(String abmStatus);
    List<AbmDevice> findTop10ByAbmStatusOrderByCreationDateDesc(String abmStatus);
    long countByAbmStatusAndProfileStatus(String abmStatus, String profileStatus);
    long countByAbmStatusAndProfile(String abmStatus, AbmProfile profile);
}
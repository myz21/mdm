package com.arcyintel.arcops.apple_mdm.repositories;

import com.arcyintel.arcops.apple_mdm.domains.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface EnrollmentStatusRepository extends JpaRepository<EnrollmentStatus, UUID> {

    /**
     * Gets the singleton enrollment status record.
     * There should only be one record in this table.
     */
    @Query("SELECT e FROM EnrollmentStatus e")
    Optional<EnrollmentStatus> findSingleton();
}

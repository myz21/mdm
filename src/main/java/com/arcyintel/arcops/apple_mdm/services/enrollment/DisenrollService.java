package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.AbmProfile;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.domains.EnrollmentType;
import com.arcyintel.arcops.apple_mdm.models.cert.abm.ClearProfileRequest;
import com.arcyintel.arcops.apple_mdm.repositories.AbmDeviceRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.services.apple.abm.AppleDepService;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandSenderService;
import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

/**
 * Handles server-initiated device disenrollment.
 *
 * Flow:
 * 1. Send RemoveProfile MDM command targeting the enrollment profile
 * 2. For DEP devices, remove the ABM profile assignment (prevent auto re-enrollment)
 * 3. Send RestartDevice command
 * 4. When the device processes RemoveProfile, it sends a CheckOut message
 *    (because CheckOutWhenRemoved=true) which triggers the existing
 *    AppleCheckinServiceImpl.checkOut() → soft-delete + enrollment history + event publish
 */
@Service
@RequiredArgsConstructor
public class DisenrollService {

    private static final Logger log = LoggerFactory.getLogger(DisenrollService.class);

    private final AppleDeviceRepository appleDeviceRepository;
    private final AppleCommandSenderService commandSenderService;
    private final AppleDepService depService;
    private final AbmDeviceRepository abmDeviceRepository;

    @Value("${host}")
    private String apiHost;

    public void disenroll(String udid) {
        AppleDevice device = appleDeviceRepository.findByUdid(udid)
                .orElseThrow(() -> new EntityNotFoundException("AppleDevice", udid));
        disenrollDevice(device);
    }

    public DisenrollResult bulkDisenroll(List<String> udids) {
        int success = 0;
        int failed = 0;

        for (String udid : udids) {
            try {
                AppleDevice device = appleDeviceRepository.findByUdid(udid)
                        .orElseThrow(() -> new EntityNotFoundException("AppleDevice", udid));
                disenrollDevice(device);
                success++;
            } catch (EntityNotFoundException e) {
                log.warn("Bulk disenroll: device {} not found, skipping", udid);
                failed++;
            } catch (Exception e) {
                log.error("Bulk disenroll failed for device {}: {}", udid, e.getMessage());
                failed++;
            }
        }

        log.info("Bulk disenroll completed: success={}, failed={}", success, failed);
        return new DisenrollResult(udids.size(), success, failed);
    }

    private void disenrollDevice(AppleDevice device) {
        String deviceIdentifier = device.getUdid();

        // 1. For DEP devices, remove ABM profile assignment first (prevent auto re-enrollment on reset)
        if (device.getEnrollmentType() == EnrollmentType.DEP && device.getSerialNumber() != null) {
            removeDepProfileAssignment(device);
        }

        // 2. Send RemoveProfile targeting the MDM enrollment profile
        //    When the device processes this, it sends CheckOut → checkOut() handles cleanup
        String enrollmentProfileId = resolveEnrollmentProfileIdentifier(device.getEnrollmentType());
        try {
            commandSenderService.removeProfile(deviceIdentifier, enrollmentProfileId);
            log.info("RemoveProfile command queued for device {} (profile: {})", deviceIdentifier, enrollmentProfileId);
        } catch (Exception e) {
            log.error("Failed to send RemoveProfile to {}: {}", deviceIdentifier, e.getMessage());
            throw new RuntimeException("Failed to send disenroll command: " + e.getMessage(), e);
        }

        // RemoveProfile is sufficient — when the device processes it, CheckOutWhenRemoved=true
        // triggers automatic CheckOut → checkOut() handler does soft-delete + event publish.
        // No RestartDevice needed — it was causing race conditions (restart before profile removal).
    }

    private void removeDepProfileAssignment(AppleDevice device) {
        try {
            abmDeviceRepository.findBySerialNumber(device.getSerialNumber()).ifPresent(abmDevice -> {
                AbmProfile profile = abmDevice.getProfile();
                if (profile != null && profile.getProfileUuid() != null) {
                    ClearProfileRequest req = new ClearProfileRequest();
                    req.setProfileUuid(profile.getProfileUuid());
                    req.setDevices(List.of(device.getSerialNumber()));
                    try {
                        depService.removeProfileFromDevices(req);
                        log.info("DEP profile assignment removed for device {} (serial: {})",
                                device.getUdid(), device.getSerialNumber());
                    } catch (Exception e) {
                        log.warn("Failed to remove DEP profile for {} (non-fatal): {}", device.getSerialNumber(), e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Failed to check DEP status for {} (non-fatal): {}", device.getSerialNumber(), e.getMessage());
        }
    }

    /**
     * Resolves the enrollment profile PayloadIdentifier based on enrollment type.
     * Must match the identifiers generated in EnrollmentProfileGeneratorImpl.
     */
    private String resolveEnrollmentProfileIdentifier(EnrollmentType enrollmentType) {
        String orgId = deriveOrgIdentifier();
        return switch (enrollmentType) {
            case USER_ENROLLMENT -> orgId + ".mdm.user.enrollment";
            case DEP -> orgId + ".mdm.adde.enrollment";
            default -> orgId + ".mdm.device.enrollment";
        };
    }

    private String deriveOrgIdentifier() {
        try {
            String host = URI.create(apiHost).getHost();
            if (host == null || host.isBlank()) host = apiHost;
            String[] parts = host.split("\\.");
            String[] reversed = new String[parts.length];
            for (int i = 0; i < parts.length; i++) {
                reversed[i] = parts[parts.length - 1 - i];
            }
            return String.join(".", reversed);
        } catch (Exception e) {
            log.warn("Could not derive org identifier from host '{}', using fallback", apiHost);
            return "com.arcops";
        }
    }

    public record DisenrollResult(int total, int success, int failed) {}
}

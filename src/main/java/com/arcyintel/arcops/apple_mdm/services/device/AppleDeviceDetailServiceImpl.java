package com.arcyintel.arcops.apple_mdm.services.device;

import com.arcyintel.arcops.apple_mdm.services.device.AppleDeviceDetailService;
import com.arcyintel.arcops.commons.exceptions.EntityNotFoundException;
import com.arcyintel.arcops.apple_mdm.domains.*;
import com.arcyintel.arcops.apple_mdm.models.api.device.GetAppleDeviceDetailDto;
import com.arcyintel.arcops.apple_mdm.domains.AgentLocation;
import com.arcyintel.arcops.apple_mdm.repositories.AgentActivityLogRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AgentLocationRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AgentPresenceHistoryRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleCommandRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceAppRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.repositories.DeviceAuthHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of AppleDeviceDetailService.
 * Fetches detailed device information including properties, command history, and installed apps.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppleDeviceDetailServiceImpl implements AppleDeviceDetailService {

    private final AppleDeviceRepository appleDeviceRepository;
    private final AppleCommandRepository appleCommandRepository;
    private final AppleDeviceAppRepository appleDeviceAppRepository;
    private final AgentPresenceHistoryRepository agentPresenceHistoryRepository;
    private final AgentLocationRepository agentLocationRepository;
    private final DeviceAuthHistoryRepository deviceAuthHistoryRepository;
    private final AgentActivityLogRepository agentActivityLogRepository;

    @Override
    @Transactional(readOnly = true)
    public GetAppleDeviceDetailDto getDeviceDetail(UUID deviceId) {
        log.debug("Fetching device detail for ID: {}", deviceId);

        AppleDevice device = appleDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new EntityNotFoundException("Device", deviceId));

        return buildDetailDto(device);
    }

    @Override
    @Transactional(readOnly = true)
    public GetAppleDeviceDetailDto getDeviceDetailByUdid(String udid) {
        log.debug("Fetching device detail for UDID: {}", udid);

        AppleDevice device = appleDeviceRepository.findByUdid(udid)
                .orElseThrow(() -> new EntityNotFoundException("Device not found with UDID: " + udid));

        return buildDetailDto(device);
    }

    private GetAppleDeviceDetailDto buildDetailDto(AppleDevice device) {
        // Fetch command history
        List<AppleCommand> commands = appleCommandRepository.findByDeviceIdOrderByRequestTimeDesc(
                device.getId(), PageRequest.of(0, 50));

        // Fetch installed apps
        List<AppleDeviceApp> apps = appleDeviceAppRepository.findAllByAppleDevice_Id(device.getId());

        // Fetch presence history (last 50 transition events)
        List<AgentPresenceHistory> presenceEvents = agentPresenceHistoryRepository
                .findByDeviceIdOrderByTimestampDesc(device.getId());
        List<GetAppleDeviceDetailDto.PresenceEventDto> presenceHistory = presenceEvents.stream()
                .limit(50)
                .map(e -> GetAppleDeviceDetailDto.PresenceEventDto.builder()
                        .eventType(e.getEventType())
                        .timestamp(e.getTimestamp())
                        .durationSeconds(e.getDurationSeconds())
                        .reason(e.getReason())
                        .agentVersion(e.getAgentVersion())
                        .agentPlatform(e.getAgentPlatform())
                        .build())
                .toList();

        // Fetch auth history (last 50 sign-in/sign-out events)
        List<DeviceAuthHistory> authEntries = deviceAuthHistoryRepository
                .findByDevice_Id(device.getId(), PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        List<GetAppleDeviceDetailDto.AuthHistoryDto> authHistory = authEntries.stream()
                .map(e -> GetAppleDeviceDetailDto.AuthHistoryDto.builder()
                        .id(e.getId())
                        .username(e.getUsername())
                        .authSource(e.getAuthSource())
                        .eventType(e.getEventType())
                        .ipAddress(e.getIpAddress())
                        .agentVersion(e.getAgentVersion())
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList();

        // Fetch agent activity log (last 50 entries)
        List<AgentActivityLog> activityLogs = agentActivityLogRepository.findByDevice_IdOrderByCreatedAtDesc(
                device.getId(), PageRequest.of(0, 50));
        List<GetAppleDeviceDetailDto.AgentActivityLogDto> agentActivityLog = activityLogs.stream()
                .map(a -> GetAppleDeviceDetailDto.AgentActivityLogDto.builder()
                        .id(a.getId())
                        .activityType(a.getActivityType())
                        .status(a.getStatus())
                        .details(a.getDetails())
                        .sessionId(a.getSessionId())
                        .startedAt(a.getStartedAt())
                        .endedAt(a.getEndedAt())
                        .durationSeconds(a.getDurationSeconds())
                        .initiatedBy(a.getInitiatedBy())
                        .createdAt(a.getCreatedAt())
                        .build())
                .toList();

        // Fetch lost-mode location if device is in lost mode
        GetAppleDeviceDetailDto.LostModeLocationDto lostModeLocation = null;
        if (device.getDeviceProperties() != null
                && Boolean.TRUE.equals(device.getDeviceProperties().getMdmlostModeEnabled())) {
            Optional<AgentLocation> loc = agentLocationRepository
                    .findFirstByDeviceIdentifierAndSourceOrderByDeviceCreatedAtDesc(
                            device.getUdid(), "MDM_LOST_MODE");
            lostModeLocation = loc.map(l -> GetAppleDeviceDetailDto.LostModeLocationDto.builder()
                    .latitude(l.getLatitude())
                    .longitude(l.getLongitude())
                    .altitude(l.getAltitude())
                    .horizontalAccuracy(l.getHorizontalAccuracy())
                    .verticalAccuracy(l.getVerticalAccuracy())
                    .speed(l.getSpeed())
                    .course(l.getCourse())
                    .timestamp(l.getDeviceCreatedAt())
                    .build()).orElse(null);
        }

        return GetAppleDeviceDetailDto.builder()
                .id(device.getId())
                .udid(device.getUdid())
                .serialNumber(device.getSerialNumber())
                .productName(device.getProductName())
                .osVersion(device.getOsVersion())
                .buildVersion(device.getBuildVersion())
                .status(device.getStatus())
                .managementMode(device.getManagementMode())
                .enrollmentType(device.getEnrollmentType() != null ? device.getEnrollmentType().name() : null)
                .isUserEnrollment(device.getIsUserEnrollment())
                .enrollmentId(device.getEnrollmentId())
                .isDeclarativeManagementEnabled(device.getIsDeclarativeManagementEnabled())
                .declarativeStatus(device.getDeclarativeStatus())
                .isCompliant(device.getIsCompliant())
                .complianceFailures(device.getComplianceFailures())
                .appliedPolicy(device.getAppliedPolicy())
                .deviceProperties(mapDeviceProperties(device.getDeviceProperties()))
                .commandHistory(mapCommandHistory(commands))
                .installedApps(mapInstalledApps(apps))
                .agentOnline(device.getAgentOnline())
                .agentLastSeenAt(device.getAgentLastSeenAt())
                .agentVersion(device.getAgentVersion())
                .agentPlatform(device.getAgentPlatform())
                .presenceHistory(presenceHistory)
                .authHistory(authHistory)
                .agentActivityLog(agentActivityLog)
                .lostModeLocation(lostModeLocation)
                .creationDate(device.getCreationDate())
                .lastModifiedDate(device.getLastModifiedDate())
                .build();
    }

    private GetAppleDeviceDetailDto.DevicePropertiesDto mapDeviceProperties(AppleDeviceInformation info) {
        if (info == null) {
            return null;
        }

        return GetAppleDeviceDetailDto.DevicePropertiesDto.builder()
                .deviceName(info.getDeviceName())
                .modelName(info.getModelName())
                .model(info.getModel())
                .supervised(info.getSupervised())
                .multiUser(info.getMultiUser())
                .imei(info.getImei())
                .meid(info.getMeid())
                .bluetoothMAC(info.getBluetoothMAC())
                .wifiMAC(info.getWifiMAC())
                .cellularTechnology(info.getCellularTechnology())
                .subscriberMCC(info.getSubscriberMCC())
                .subscriberMNC(info.getSubscriberMNC())
                .dataRoamingEnabled(info.getDataRoamingEnabled())
                .voiceRoamingEnabled(info.getVoiceRoamingEnabled())
                .networkTethered(info.getNetworkTethered())
                .personalHotspotEnabled(info.getPersonalHotspotEnabled())
                .roaming(info.getRoaming())
                .batteryLevel(info.getBatteryLevel())
                .deviceCapacity(info.getDeviceCapacity())
                .activationLockEnabled(info.getActivationLockEnabled())
                .cloudBackupEnabled(info.getCloudBackupEnabled())
                .deviceLocatorServiceEnabled(info.getDeviceLocatorServiceEnabled())
                .mdmLostModeEnabled(info.getMdmlostModeEnabled())
                .doNotDisturbInEffect(info.getDoNotDisturbInEffect())
                .awaitingConfiguration(info.getAwaitingConfiguration())
                .appAnalyticsEnabled(info.getAppAnalyticsEnabled())
                .diagnosticSubmissionEnabled(info.getDiagnosticSubmissionEnabled())
                .itunesStoreAccountIsActive(info.getItunesStoreAccountIsActive())
                .easDeviceIdentifier(info.getEasDeviceIdentifier())
                .modemFirmwareVersion(info.getModemFirmwareVersion())
                .securityInfo(info.getSecurityInfo())
                .certificateList(info.getCertificateList())
                .build();
    }

    private List<GetAppleDeviceDetailDto.CommandHistoryDto> mapCommandHistory(List<AppleCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return Collections.emptyList();
        }

        return commands.stream()
                .map(cmd -> GetAppleDeviceDetailDto.CommandHistoryDto.builder()
                        .id(cmd.getId())
                        .commandType(cmd.getCommandType())
                        .commandUUID(cmd.getCommandUUID())
                        .status(cmd.getStatus())
                        .requestTime(instantToDate(cmd.getRequestTime()))
                        .executionTime(instantToDate(cmd.getExecutionTime()))
                        .completionTime(instantToDate(cmd.getCompletionTime()))
                        .failureReason(cmd.getFailureReason())
                        .policyId(cmd.getPolicyId())
                        .build())
                .toList();
    }

    private List<GetAppleDeviceDetailDto.InstalledAppDto> mapInstalledApps(List<AppleDeviceApp> apps) {
        if (apps == null || apps.isEmpty()) {
            return Collections.emptyList();
        }

        return apps.stream()
                .map(app -> GetAppleDeviceDetailDto.InstalledAppDto.builder()
                        .id(app.getId())
                        .bundleIdentifier(app.getBundleIdentifier())
                        .name(app.getName())
                        .version(app.getVersion())
                        .shortVersion(app.getShortVersion())
                        .bundleSize(app.getBundleSize())
                        .installing(app.isInstalling())
                        .managed(app.isManaged())
                        .hasConfiguration(app.isHasConfiguration())
                        .hasFeedback(app.isHasFeedback())
                        .validated(app.isValidated())
                        .managementFlags(app.getManagementFlags())
                        .build())
                .toList();
    }

    private Date instantToDate(Instant instant) {
        return instant != null ? Date.from(instant) : null;
    }
}

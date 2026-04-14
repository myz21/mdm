package com.arcyintel.arcops.apple_mdm.models.api.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detailed Apple Device DTO for device detail page.
 * Includes device info, command history, installed apps, and applied policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetAppleDeviceDetailDto implements Serializable {

    private UUID id;
    private String udid;
    private String serialNumber;
    private String productName;
    private String osVersion;
    private String buildVersion;
    private String status;
    private String managementMode;

    // Enrollment info
    private String enrollmentType;
    private Boolean isUserEnrollment;
    private String enrollmentId;

    // MDM info
    private Boolean isDeclarativeManagementEnabled;
    private Map<String, Object> declarativeStatus;

    // Compliance
    private Boolean isCompliant;
    private Map<String, Object> complianceFailures;

    // Applied policy (the merged/combined policy)
    private Map<String, Object> appliedPolicy;

    // Device properties (extended info)
    private DevicePropertiesDto deviceProperties;

    // Command history
    private List<CommandHistoryDto> commandHistory;

    // Installed apps
    private List<InstalledAppDto> installedApps;

    // Agent presence
    private Boolean agentOnline;
    private Instant agentLastSeenAt;
    private String agentVersion;
    private String agentPlatform;

    // Agent presence history (online/offline transition events)
    private List<PresenceEventDto> presenceHistory;

    // Auth history (sign-in/sign-out events)
    private List<AuthHistoryDto> authHistory;

    // Agent activity log (screen share, terminal, notifications)
    private List<AgentActivityLogDto> agentActivityLog;

    // Lost mode location (from DeviceLocation MDM command)
    private LostModeLocationDto lostModeLocation;

    // Timestamps
    private Date creationDate;
    private Date lastModifiedDate;

    /**
     * Extended device properties from AppleDeviceInformation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DevicePropertiesDto implements Serializable {
        private String deviceName;
        private String modelName;
        private String model;
        private Boolean supervised;
        private Boolean multiUser;

        // Hardware
        private String imei;
        private String meid;
        private String bluetoothMAC;
        private String wifiMAC;
        private Integer cellularTechnology;

        // Network
        private String subscriberMCC;
        private String subscriberMNC;
        private Boolean dataRoamingEnabled;
        private Boolean voiceRoamingEnabled;
        private Boolean networkTethered;
        private Boolean personalHotspotEnabled;
        private Boolean roaming;

        // Status
        private Number batteryLevel;
        private Number deviceCapacity;
        private Number availableDeviceCapacity;
        private Boolean activationLockEnabled;
        private Boolean cloudBackupEnabled;
        private Boolean deviceLocatorServiceEnabled;
        private Boolean mdmLostModeEnabled;
        private Boolean doNotDisturbInEffect;
        private Boolean awaitingConfiguration;

        // Services
        private Boolean appAnalyticsEnabled;
        private Boolean diagnosticSubmissionEnabled;
        private Boolean itunesStoreAccountIsActive;
        private String easDeviceIdentifier;
        private String modemFirmwareVersion;

        // Security
        private Map<String, Object> securityInfo;
        private List<Map<String, Object>> certificateList;
    }

    /**
     * Command history entry
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandHistoryDto implements Serializable {
        private UUID id;
        private String commandType;
        private String commandUUID;
        private String status;
        private Date requestTime;
        private Date executionTime;
        private Date completionTime;
        private String failureReason;
        private UUID policyId;
    }

    /**
     * Installed app entry
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstalledAppDto implements Serializable {
        private UUID id;
        private String bundleIdentifier;
        private String name;
        private String version;
        private String shortVersion;
        private Integer bundleSize;
        private Boolean installing;
        private Boolean managed;
        private Boolean hasConfiguration;
        private Boolean hasFeedback;
        private Boolean validated;
        private Integer managementFlags;
    }

    /**
     * Agent presence transition event (ONLINE or OFFLINE).
     * durationSeconds = how long the device was in the previous state before this transition.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresenceEventDto implements Serializable {
        private String eventType;       // ONLINE or OFFLINE
        private Instant timestamp;      // when the transition happened
        private Long durationSeconds;   // previous state duration
        private String reason;          // for OFFLINE: graceful, heartbeat_timeout, server_restart
        private String agentVersion;
        private String agentPlatform;
    }

    /**
     * Device auth history entry (sign-in / sign-out events).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthHistoryDto implements Serializable {
        private UUID id;
        private String username;
        private String authSource;   // AGENT | SETUP
        private String eventType;    // SIGN_IN | SIGN_OUT
        private String ipAddress;
        private String agentVersion;
        private Instant createdAt;
    }

    /**
     * Agent activity log entry (screen share, terminal, notification).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentActivityLogDto implements Serializable {
        private UUID id;
        private String activityType;     // SCREEN_SHARE | REMOTE_TERMINAL | NOTIFICATION
        private String status;           // STARTED | COMPLETED | FAILED
        private Map<String, Object> details;
        private String sessionId;
        private Instant startedAt;
        private Instant endedAt;
        private Long durationSeconds;
        private String initiatedBy;
        private Instant createdAt;
    }

    /**
     * Location obtained via MDM DeviceLocation command while in Lost Mode.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LostModeLocationDto implements Serializable {
        private Double latitude;
        private Double longitude;
        private Double altitude;
        private Double horizontalAccuracy;
        private Double verticalAccuracy;
        private Double speed;
        private Double course;
        private Instant timestamp;
    }
}

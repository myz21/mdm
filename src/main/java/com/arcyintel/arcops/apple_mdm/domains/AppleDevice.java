package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AuditableTimestamps;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Setter
@Table(name = "apple_device")
@SQLDelete(sql = """
            UPDATE apple_device
                    set status =
            'DELETED'
            WHERE id = ?
        """)
@SQLRestriction("status <> 'DELETED'")
public class AppleDevice extends AuditableTimestamps {

    @Column(name = "build_version")
    private String buildVersion;

    @Column(name = "os_version")
    private String osVersion;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "udid", unique = true)
    private String udid;

    /**
     * EnrollmentID is used instead of UDID for User Enrollment (privacy feature).
     * This is an anonymized identifier that changes if device re-enrolls.
     */
    @Column(name = "enrollment_id", length = 128)
    private String enrollmentId;

    /**
     * EnrollmentUserID is the user identifier from Apple during User Enrollment.
     */
    @Column(name = "enrollment_user_id", length = 256)
    private String enrollmentUserId;

    /**
     * The type of enrollment used to add this device.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_type", length = 32)
    @Builder.Default
    private EnrollmentType enrollmentType = EnrollmentType.UNKNOWN;

    /**
     * Whether this device was enrolled via User Enrollment (BYOD).
     */
    @Column(name = "is_user_enrollment")
    @Builder.Default
    private Boolean isUserEnrollment = false;

    @Column(name = "token", length = 2048)
    private String token;

    @Column(name = "push_magic")
    private String pushMagic;

    @Column(name = "unlock_token", length = 4096)
    private String unlockToken;

    @Column(name = "is_declarative_management_enabled")
    private Boolean isDeclarativeManagementEnabled;

    @Column(name = "declarative_status")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> declarativeStatus;

    @Column(name = "declarative_token", length = 248)
    private String declarationToken;

    @OneToOne(cascade = {CascadeType.MERGE, CascadeType.REMOVE}, mappedBy = "appleDevice")
    private AppleDeviceInformation deviceProperties;

    @Column(name = "applied_policy")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> appliedPolicy;

    @Column(name = "is_compliant", nullable = false)
    @Builder.Default
    private Boolean isCompliant = true;

    @Column(name = "compliance_failures")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> complianceFailures;

    @Column(name = "status")
    private String status;

    @Column(name = "management_mode", columnDefinition = "TEXT")
    private String managementMode;

    // --- Agent Presence ---

    @Column(name = "agent_online")
    @Builder.Default
    private Boolean agentOnline = false;

    @Column(name = "agent_last_seen_at")
    private Instant agentLastSeenAt;

    @Column(name = "agent_version", length = 50)
    private String agentVersion;

    @Column(name = "agent_platform", length = 20)
    private String agentPlatform;

    @Column(name = "agent_push_token", length = 256)
    private String agentPushToken;

    @OneToMany(cascade = {CascadeType.MERGE, CascadeType.REMOVE}, fetch = FetchType.LAZY, mappedBy = "appleDevice")
    @JsonManagedReference
    private List<AppleCommand> appleCommands;

    @ManyToMany(mappedBy = "devices", fetch = FetchType.LAZY)
    private Set<AppleAccount> accounts;

    @OneToMany(cascade = {CascadeType.MERGE, CascadeType.REMOVE}, fetch = FetchType.LAZY, mappedBy = "appleDevice")
    @JsonManagedReference
    private List<AppleDeviceApp> installedApps;

    @Version
    private Long version;

}

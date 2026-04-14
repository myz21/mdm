package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AbstractEntity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

@Entity
@Table(name = "enrollment_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class EnrollmentAuditLog extends AbstractEntity {

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "creation_date", updatable = false)
    private Date creationDate;

    @Column(name = "action", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(name = "target_type", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private AuditTargetType targetType;

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private AuditStatus status;

    @Column(name = "message", length = 1024)
    private String message;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "performed_by", length = 256)
    private String performedBy;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    public enum AuditAction {
        UPLOAD,
        RENEW,
        GENERATE,
        DELETE,
        REFRESH,
        HOT_RELOAD
    }

    public enum AuditTargetType {
        APNS_CERT,
        DEP_TOKEN,
        VPP_TOKEN,
        VENDOR_CERT,
        DEP_CERT,
        ENROLLMENT_STATUS
    }

    public enum AuditStatus {
        SUCCESS,
        FAILURE
    }
}

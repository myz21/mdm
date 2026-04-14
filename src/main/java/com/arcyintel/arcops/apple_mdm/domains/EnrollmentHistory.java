package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AbstractEntity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "enrollment_history")
public class EnrollmentHistory extends AbstractEntity {

    @Column(name = "device_id")
    private UUID deviceId;

    private String udid;

    @Column(name = "enrollment_id", length = 128)
    private String enrollmentId;

    @Column(name = "enrollment_user_id", length = 256)
    private String enrollmentUserId;

    @Column(name = "enrollment_type", length = 50)
    private String enrollmentType;

    @Column(name = "is_user_enrollment")
    private Boolean isUserEnrollment;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "build_version", length = 50)
    private String buildVersion;

    @Column(name = "token", length = 2048)
    private String token;

    @Column(name = "push_magic")
    private String pushMagic;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "enrolled_at")
    private LocalDateTime enrolledAt;

    @Column(name = "unenrolled_at")
    private LocalDateTime unenrolledAt;

    @Column(name = "unenroll_reason", length = 50)
    private String unenrollReason;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

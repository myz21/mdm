package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.Auditable;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Table(name = "apple_command")
public class AppleCommand extends Auditable<String> {

    @Column(name = "template", columnDefinition = "TEXT")
    private String template;

    @Column(name = "command_type")
    private String commandType;

    @Column(name = "command_uuid")
    private String commandUUID;

    @Column(name = "status")
    private String status;

    @Column(name = "request_time")
    private Instant requestTime;

    @Column(name = "execution_time")
    private Instant executionTime;

    @Column(name = "completion_time")
    private Instant completionTime;

    @Column(name = "failure_reason", length = 1024)
    private String failureReason;

    @Column(name = "apple_device_udid", insertable = false, updatable = false)
    private String appleDeviceUdid;

    @ManyToOne(targetEntity = AppleDevice.class, fetch = FetchType.LAZY)
    @JoinColumn(name = "apple_device_udid", referencedColumnName = "udid")
    @JsonBackReference
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private AppleDevice appleDevice;

    @Column(name = "bulk_command_id")
    private UUID bulkCommandId;

    @Column(name = "policy_id", insertable = false, updatable = false, nullable = true)
    private UUID policyId;

    @ManyToOne(targetEntity = Policy.class, fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", referencedColumnName = "id")
    @SQLRestriction("status <> 'DELETED'")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Policy policy;

}

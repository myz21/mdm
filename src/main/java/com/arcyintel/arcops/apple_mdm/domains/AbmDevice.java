package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AuditableTimestamps;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "abm_device")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AbmDevice extends AuditableTimestamps {

    @Column(name = "serial_number", nullable = false, unique = true)
    private String serialNumber;

    @Column
    private String model;

    @Column
    private String description;

    @Column
    private String color;

    @Column(name = "asset_tag")
    private String assetTag;

    @Column
    private String os;

    @Column(name = "device_family")
    private String deviceFamily;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private AbmProfile profile;

    @Column(name = "profile_status")
    private String profileStatus;

    @Column(name = "profile_assign_time")
    private String profileAssignTime;

    @Column(name = "profile_push_time")
    private String profilePushTime;

    @Column(name = "device_assigned_date")
    private String deviceAssignedDate;

    @Column(name = "device_assigned_by")
    private String deviceAssignedBy;

    @Column(name = "abm_status")
    @Builder.Default
    private String abmStatus = "ACTIVE";
}
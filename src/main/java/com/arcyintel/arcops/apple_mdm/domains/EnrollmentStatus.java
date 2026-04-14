package com.arcyintel.arcops.apple_mdm.domains;

import com.arcyintel.arcops.commons.domains.AuditableTimestamps;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "enrollment_status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class EnrollmentStatus extends AuditableTimestamps {

    // Enrollment Progress
    @Column(name = "current_step")
    @Builder.Default
    private Integer currentStep = 1;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "completed_steps", columnDefinition = "integer[]")
    private Integer[] completedStepsArray;

    @Transient
    @Builder.Default
    private List<Integer> completedSteps = new ArrayList<>();

    // Helper methods to convert between array and list
    @PostLoad
    private void loadCompletedSteps() {
        if (completedStepsArray != null) {
            completedSteps = new ArrayList<>(Arrays.asList(completedStepsArray));
        } else {
            completedSteps = new ArrayList<>();
        }
    }

    @PrePersist
    @PreUpdate
    private void saveCompletedSteps() {
        if (completedSteps != null) {
            completedStepsArray = completedSteps.toArray(new Integer[0]);
        } else {
            completedStepsArray = new Integer[0];
        }
    }

    @Column(name = "enrollment_completed")
    @Builder.Default
    private Boolean enrollmentCompleted = false;

    // APNS Certificate Info
    @Column(name = "apns_cert_uploaded")
    @Builder.Default
    private Boolean apnsCertUploaded = false;

    @Column(name = "apns_cert_subject", length = 512)
    private String apnsCertSubject;

    @Column(name = "apns_cert_issuer", length = 512)
    private String apnsCertIssuer;

    @Column(name = "apns_cert_serial", length = 128)
    private String apnsCertSerial;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "apns_cert_not_before")
    private Date apnsCertNotBefore;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "apns_cert_not_after")
    private Date apnsCertNotAfter;

    @Column(name = "apns_push_topic", length = 256)
    private String apnsPushTopic;

    // DEP (ABM) Token Info
    @Column(name = "dep_token_uploaded")
    @Builder.Default
    private Boolean depTokenUploaded = false;

    @Column(name = "dep_token_consumer_key", length = 256)
    private String depTokenConsumerKey;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dep_token_not_after")
    private Date depTokenNotAfter;

    @Column(name = "dep_org_name", length = 256)
    private String depOrgName;

    @Column(name = "dep_org_email", length = 256)
    private String depOrgEmail;

    @Column(name = "dep_org_phone", length = 64)
    private String depOrgPhone;

    @Column(name = "dep_org_address", length = 512)
    private String depOrgAddress;

    // VPP Token Info
    @Column(name = "vpp_token_uploaded")
    @Builder.Default
    private Boolean vppTokenUploaded = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "vpp_token_not_after")
    private Date vppTokenNotAfter;

    @Column(name = "vpp_org_name", length = 256)
    private String vppOrgName;

    @Column(name = "vpp_location_name", length = 256)
    private String vppLocationName;

    // Vendor Certificate Info (internal use only)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "vendor_cert_not_after")
    private Date vendorCertNotAfter;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "vendor_cert_renewed_at")
    private Date vendorCertRenewedAt;

    @Column(name = "push_cert_renewed_after_vendor")
    @Builder.Default
    private Boolean pushCertRenewedAfterVendor = true;
}

package com.arcyintel.arcops.apple_mdm.models.api.enrollment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetEnrollmentStatusDto {
    private UUID id;
    private Date creationDate;
    private Date lastModifiedDate;

    // Enrollment Progress
    private Integer currentStep;
    private List<Integer> completedSteps;
    private Boolean enrollmentCompleted;

    // APNS Certificate Info
    private CertificateInfoDto apnsCertificate;

    // DEP (ABM) Token Info
    private DepTokenInfoDto depToken;

    // VPP Token Info
    private VppTokenInfoDto vppToken;

    // Vendor Certificate Info (read from filesystem on every status request)
    private Date vendorCertNotAfter;
    private Integer vendorCertDaysUntilExpiry;
    private String vendorCertStatus; // VALID, EXPIRING_SOON, EXPIRED, UNKNOWN
    private Date vendorCertRenewedAt;
    private Boolean pushCertRenewedAfterVendor;
    private Boolean vendorCertRenewalWarning; // True if vendor was renewed but push cert wasn't

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CertificateInfoDto {
        private Boolean uploaded;
        private String subject;
        private String issuer;
        private String serial;
        private Date notBefore;
        private Date notAfter;
        private String pushTopic;
        private Integer daysUntilExpiry;
        private String status; // VALID, EXPIRING_SOON, EXPIRED, NOT_UPLOADED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepTokenInfoDto {
        private Boolean uploaded;
        private String consumerKey;
        private Date notAfter;
        private String orgName;
        private String orgEmail;
        private String orgPhone;
        private String orgAddress;
        private Integer daysUntilExpiry;
        private String status; // VALID, EXPIRING_SOON, EXPIRED, NOT_UPLOADED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VppTokenInfoDto {
        private Boolean uploaded;
        private Date notAfter;
        private String orgName;
        private String locationName;
        private Integer daysUntilExpiry;
        private String status; // VALID, EXPIRING_SOON, EXPIRED, NOT_UPLOADED
    }
}

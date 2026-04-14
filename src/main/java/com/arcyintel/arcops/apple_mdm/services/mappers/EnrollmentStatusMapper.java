package com.arcyintel.arcops.apple_mdm.services.mappers;

import com.arcyintel.arcops.apple_mdm.domains.EnrollmentStatus;
import com.arcyintel.arcops.apple_mdm.models.api.enrollment.GetEnrollmentStatusDto;
import com.arcyintel.arcops.apple_mdm.services.apple.abm.AppleVppService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

@Component
public class EnrollmentStatusMapper {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentStatusMapper.class);
    private static final int EXPIRING_SOON_DAYS = 30;
    private static final String CERTS_DIR = "certs/apple";

    private final AppleVppService appleVppService;

    @Value("${mdm.cert.paths.mdmPem}")
    private Resource vendorCertResource;

    public EnrollmentStatusMapper(AppleVppService appleVppService) {
        this.appleVppService = appleVppService;
    }

    public GetEnrollmentStatusDto mapToDto(EnrollmentStatus status) {
        boolean vendorRenewalWarning = status.getVendorCertRenewedAt() != null
                && !Boolean.TRUE.equals(status.getPushCertRenewedAfterVendor());

        Date vendorCertNotAfter = readVendorCertNotAfterFromDisk();
        Integer vendorCertDaysUntilExpiry = calculateDaysUntilExpiry(vendorCertNotAfter);
        String vendorCertStatus = calculateCertStatus(vendorCertDaysUntilExpiry);

        return GetEnrollmentStatusDto.builder()
                .id(status.getId())
                .creationDate(status.getCreationDate())
                .lastModifiedDate(status.getLastModifiedDate())
                .currentStep(status.getCurrentStep())
                .completedSteps(status.getCompletedSteps())
                .enrollmentCompleted(Boolean.TRUE.equals(status.getEnrollmentCompleted())
                        || (status.getCompletedSteps() != null && status.getCompletedSteps().contains(6)))
                .apnsCertificate(buildApnsCertificateInfo(status))
                .depToken(buildDepTokenInfo(status))
                .vppToken(buildVppTokenInfo(status))
                .vendorCertNotAfter(vendorCertNotAfter)
                .vendorCertDaysUntilExpiry(vendorCertDaysUntilExpiry)
                .vendorCertStatus(vendorCertStatus)
                .vendorCertRenewedAt(status.getVendorCertRenewedAt())
                .pushCertRenewedAfterVendor(status.getPushCertRenewedAfterVendor())
                .vendorCertRenewalWarning(vendorRenewalWarning)
                .build();
    }

    private Date readVendorCertNotAfterFromDisk() {
        try {
            if (vendorCertResource.exists()) {
                try (InputStream is = vendorCertResource.getInputStream()) {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
                    return cert.getNotAfter();
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read vendor certificate from disk: {}", e.getMessage());
        }
        return null;
    }

    private GetEnrollmentStatusDto.CertificateInfoDto buildApnsCertificateInfo(EnrollmentStatus status) {
        if (!Boolean.TRUE.equals(status.getApnsCertUploaded())) {
            return GetEnrollmentStatusDto.CertificateInfoDto.builder()
                    .uploaded(false)
                    .status("NOT_UPLOADED")
                    .build();
        }

        Integer daysUntilExpiry = calculateDaysUntilExpiry(status.getApnsCertNotAfter());
        String certStatus = calculateCertStatus(daysUntilExpiry);

        return GetEnrollmentStatusDto.CertificateInfoDto.builder()
                .uploaded(true)
                .subject(status.getApnsCertSubject())
                .issuer(status.getApnsCertIssuer())
                .serial(status.getApnsCertSerial())
                .notBefore(status.getApnsCertNotBefore())
                .notAfter(status.getApnsCertNotAfter())
                .pushTopic(status.getApnsPushTopic())
                .daysUntilExpiry(daysUntilExpiry)
                .status(certStatus)
                .build();
    }

    private GetEnrollmentStatusDto.DepTokenInfoDto buildDepTokenInfo(EnrollmentStatus status) {
        File tokenFile = new File(CERTS_DIR + "/server_token.json");
        if (!tokenFile.exists() && !Boolean.TRUE.equals(status.getDepTokenUploaded())) {
            return GetEnrollmentStatusDto.DepTokenInfoDto.builder()
                    .uploaded(false)
                    .status("NOT_UPLOADED")
                    .build();
        }

        String consumerKey = status.getDepTokenConsumerKey();
        Date notAfter = status.getDepTokenNotAfter();
        if (tokenFile.exists()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(tokenFile);
                if (node.has("consumerKey")) {
                    consumerKey = node.get("consumerKey").asText();
                }
                if (node.has("accessTokenExpiry") && !node.get("accessTokenExpiry").isNull()) {
                    String expiry = node.get("accessTokenExpiry").asText();
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                        notAfter = sdf.parse(expiry);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                logger.debug("Could not parse server_token.json: {}", e.getMessage());
            }
        }

        Integer daysUntilExpiry = calculateDaysUntilExpiry(notAfter);
        String tokenStatus = calculateCertStatus(daysUntilExpiry);

        return GetEnrollmentStatusDto.DepTokenInfoDto.builder()
                .uploaded(true)
                .consumerKey(consumerKey)
                .notAfter(notAfter)
                .orgName(status.getDepOrgName())
                .orgEmail(status.getDepOrgEmail())
                .orgPhone(status.getDepOrgPhone())
                .orgAddress(status.getDepOrgAddress())
                .daysUntilExpiry(daysUntilExpiry)
                .status(tokenStatus)
                .build();
    }

    private GetEnrollmentStatusDto.VppTokenInfoDto buildVppTokenInfo(EnrollmentStatus status) {
        File tokenFile = new File(CERTS_DIR + "/vpp_token.vpp");
        if (!tokenFile.exists() && !Boolean.TRUE.equals(status.getVppTokenUploaded())) {
            return GetEnrollmentStatusDto.VppTokenInfoDto.builder()
                    .uploaded(false)
                    .status("NOT_UPLOADED")
                    .build();
        }

        String orgName = status.getVppOrgName();
        String locationName = status.getVppLocationName();

        if (tokenFile.exists() && orgName == null) {
            try {
                Map<String, String> config = appleVppService.getClientConfig();
                orgName = config.get("orgName");
                locationName = config.get("locationName");
            } catch (Exception e) {
                logger.debug("Could not fetch VPP client config: {}", e.getMessage());
            }
        }

        Integer daysUntilExpiry = calculateDaysUntilExpiry(status.getVppTokenNotAfter());
        String tokenStatus = calculateCertStatus(daysUntilExpiry);

        return GetEnrollmentStatusDto.VppTokenInfoDto.builder()
                .uploaded(true)
                .notAfter(status.getVppTokenNotAfter())
                .orgName(orgName)
                .locationName(locationName)
                .daysUntilExpiry(daysUntilExpiry)
                .status(tokenStatus)
                .build();
    }

    public Integer calculateDaysUntilExpiry(Date expiryDate) {
        if (expiryDate == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(Instant.now(), expiryDate.toInstant());
        return (int) days;
    }

    public String calculateCertStatus(Integer daysUntilExpiry) {
        if (daysUntilExpiry == null) {
            return "UNKNOWN";
        }
        if (daysUntilExpiry < 0) {
            return "EXPIRED";
        }
        if (daysUntilExpiry <= EXPIRING_SOON_DAYS) {
            return "EXPIRING_SOON";
        }
        return "VALID";
    }
}

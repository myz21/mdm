package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.EnrollmentStatus;
import com.arcyintel.arcops.apple_mdm.models.api.enrollment.GetEnrollmentStatusDto;
import com.arcyintel.arcops.apple_mdm.repositories.EnrollmentStatusRepository;
import com.arcyintel.arcops.apple_mdm.services.apple.abm.AppleVppService;
import com.arcyintel.arcops.apple_mdm.services.apple.apns.ApplePushService;
import com.arcyintel.arcops.apple_mdm.services.apple.cert.AppleCertificationService;
import com.arcyintel.arcops.apple_mdm.services.apple.cert.ApplePushCredentialService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.EnrollmentStatusService;
import com.arcyintel.arcops.apple_mdm.services.mappers.EnrollmentStatusMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Primary
@RequiredArgsConstructor
public class EnrollmentStatusServiceImpl implements EnrollmentStatusService {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentStatusServiceImpl.class);
    private static final int EXPIRING_SOON_DAYS = 30;
    private static final String CERTS_DIR = "certs/apple";

    private final EnrollmentStatusRepository enrollmentStatusRepository;
    private final ApplePushService applePushService;
    private final AppleCertificationService appleCertificationService;
    private final ApplePushCredentialService applePushCredentialService;
    private final AppleVppService appleVppService;
    private final EnrollmentStatusMapper enrollmentStatusMapper;

    @Value("${mdm.cert.paths.pushCert}")
    private Resource mdmPushCertResource;

    @Value("${mdm.cert.paths.mdmPem}")
    private Resource vendorCertResource;

    @Override
    @Transactional(readOnly = true)
    public GetEnrollmentStatusDto getEnrollmentStatus() {
        EnrollmentStatus status = getOrCreateEnrollmentStatus();
        return enrollmentStatusMapper.mapToDto(status);
    }

    @Override
    @Transactional
    public GetEnrollmentStatusDto updateCurrentStep(int step) {
        EnrollmentStatus status = getOrCreateEnrollmentStatus();
        status.setCurrentStep(step);
        enrollmentStatusRepository.save(status);
        return enrollmentStatusMapper.mapToDto(status);
    }

    @Override
    @Transactional
    public GetEnrollmentStatusDto completeStep(int step) {
        EnrollmentStatus status = getOrCreateEnrollmentStatus();
        List<Integer> completedSteps = status.getCompletedSteps();
        if (completedSteps == null) {
            completedSteps = new ArrayList<>();
        }
        if (!completedSteps.contains(step)) {
            completedSteps.add(step);
            status.setCompletedSteps(completedSteps);
        }
        // Auto-advance to next step or mark completed on final step
        if (status.getCurrentStep() != null && status.getCurrentStep() == step) {
            if (step >= 6) {
                status.setCurrentStep(6);
                status.setEnrollmentCompleted(true);
            } else {
                status.setCurrentStep(step + 1);
            }
        }
        enrollmentStatusRepository.save(status);
        return enrollmentStatusMapper.mapToDto(status);
    }

    @Override
    @Transactional
    public GetEnrollmentStatusDto markEnrollmentCompleted() {
        EnrollmentStatus status = getOrCreateEnrollmentStatus();
        status.setEnrollmentCompleted(true);
        enrollmentStatusRepository.save(status);
        return enrollmentStatusMapper.mapToDto(status);
    }

    @Override
    @Transactional
    public GetEnrollmentStatusDto refreshCertificateInfo() {
        logger.info("Refreshing certificate and token information from disk...");
        EnrollmentStatus status = getOrCreateEnrollmentStatus();

        // Refresh APNS certificate info
        refreshApnsCertificateInfo(status);

        // Refresh DEP token info
        refreshDepTokenInfo(status);

        // Refresh VPP token info
        refreshVppTokenInfo(status);

        // Refresh vendor certificate info
        refreshVendorCertificateInfo(status);

        enrollmentStatusRepository.save(status);
        logger.info("Certificate and token information refreshed successfully.");
        return enrollmentStatusMapper.mapToDto(status);
    }

    @Override
    public void hotReloadCredentials() throws Exception {
        logger.info("Starting hot-reload of all cached credentials...");

        // Reload customer private key
        try {
            appleCertificationService.reloadCustomerPrivateKey();
            logger.info("Customer private key reloaded successfully.");
        } catch (Exception e) {
            logger.warn("Failed to reload customer private key: {}", e.getMessage());
        }

        // Regenerate APNS P12 file
        try {
            applePushCredentialService.generateApnsP12();
            logger.info("APNS P12 file regenerated successfully.");
        } catch (Exception e) {
            logger.warn("Failed to regenerate APNS P12 file: {}", e.getMessage());
        }

        // Reinitialize APNS client
        try {
            applePushService.initializeApnsClient();
            logger.info("APNS client reinitialized successfully.");
        } catch (Exception e) {
            logger.warn("Failed to reinitialize APNS client: {}", e.getMessage());
        }

        logger.info("Hot-reload of credentials completed.");
    }

    @Override
    @Transactional
    public GetEnrollmentStatusDto resetSteps(List<Integer> stepsToReset) {
        logger.info("Resetting enrollment steps: {}", stepsToReset);
        EnrollmentStatus status = getOrCreateEnrollmentStatus();
        List<Integer> completedSteps = status.getCompletedSteps();
        if (completedSteps == null) {
            completedSteps = new ArrayList<>();
        }
        completedSteps.removeAll(stepsToReset);
        status.setCompletedSteps(completedSteps);

        // Reset enrollmentCompleted if any steps are being reset
        status.setEnrollmentCompleted(false);

        // Set current step to the first reset step
        int firstResetStep = stepsToReset.stream().min(Integer::compareTo).orElse(1);
        status.setCurrentStep(firstResetStep);

        enrollmentStatusRepository.save(status);
        logger.info("Steps reset complete. Current step: {}, Completed: {}",
                firstResetStep, status.getCompletedSteps());
        return enrollmentStatusMapper.mapToDto(status);
    }

    @Override
    @Transactional
    public void markVendorCertRenewed() {
        logger.info("Marking vendor certificate as renewed...");
        EnrollmentStatus status = getOrCreateEnrollmentStatus();
        status.setVendorCertRenewedAt(new Date());
        status.setPushCertRenewedAfterVendor(false);
        enrollmentStatusRepository.save(status);
        logger.info("Vendor certificate marked as renewed. Push cert renewal warning is now active.");
    }

    @Override
    @Transactional
    public void markPushCertRenewed() {
        logger.info("Marking push certificate as renewed after vendor cert renewal...");
        EnrollmentStatus status = getOrCreateEnrollmentStatus();
        status.setPushCertRenewedAfterVendor(true);
        enrollmentStatusRepository.save(status);
        logger.info("Push certificate renewal flag updated. Vendor renewal warning cleared.");
    }

    private EnrollmentStatus getOrCreateEnrollmentStatus() {
        return enrollmentStatusRepository.findSingleton()
                .orElseGet(() -> {
                    logger.info("Creating new enrollment status record...");
                    EnrollmentStatus newStatus = EnrollmentStatus.builder()
                            .currentStep(1)
                            .completedSteps(new ArrayList<>())
                            .enrollmentCompleted(false)
                            .apnsCertUploaded(false)
                            .depTokenUploaded(false)
                            .vppTokenUploaded(false)
                            .build();
                    return enrollmentStatusRepository.save(newStatus);
                });
    }

    private void refreshApnsCertificateInfo(EnrollmentStatus status) {
        try {
            File certFile = new File(CERTS_DIR + "/MDM_Certificate.pem");
            if (!certFile.exists()) {
                logger.debug("APNS certificate file not found.");
                status.setApnsCertUploaded(false);
                clearApnsCertInfo(status);
                return;
            }

            if (mdmPushCertResource.exists()) {
                try (InputStream is = mdmPushCertResource.getInputStream()) {
                    byte[] certBytes = is.readAllBytes();
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(certBytes)) {
                        X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);

                        status.setApnsCertUploaded(true);
                        status.setApnsCertSubject(cert.getSubjectX500Principal().getName());
                        status.setApnsCertIssuer(cert.getIssuerX500Principal().getName());
                        status.setApnsCertSerial(cert.getSerialNumber().toString(16));
                        status.setApnsCertNotBefore(cert.getNotBefore());
                        status.setApnsCertNotAfter(cert.getNotAfter());

                        // Extract push topic
                        String pushTopic = extractPushTopic(cert);
                        status.setApnsPushTopic(pushTopic);

                        logger.info("APNS certificate info refreshed. Expires: {}", cert.getNotAfter());
                    }
                }
            } else {
                status.setApnsCertUploaded(false);
                clearApnsCertInfo(status);
            }
        } catch (Exception e) {
            logger.error("Failed to refresh APNS certificate info: {}", e.getMessage());
            status.setApnsCertUploaded(false);
            clearApnsCertInfo(status);
        }
    }

    private void clearApnsCertInfo(EnrollmentStatus status) {
        status.setApnsCertSubject(null);
        status.setApnsCertIssuer(null);
        status.setApnsCertSerial(null);
        status.setApnsCertNotBefore(null);
        status.setApnsCertNotAfter(null);
        status.setApnsPushTopic(null);
    }

    private String extractPushTopic(X509Certificate cert) {
        try {
            // Check Subject Alternative Names
            Collection<List<?>> altNames = cert.getSubjectAlternativeNames();
            if (altNames != null) {
                for (List<?> entry : altNames) {
                    Object value = entry.get(1);
                    if (value instanceof String str && str.contains("com.apple.mgmt.External")) {
                        return str;
                    }
                }
            }

            // Check Subject DN
            String subj = cert.getSubjectX500Principal().getName();
            if (subj.contains("com.apple.mgmt.External")) {
                Pattern p = Pattern.compile("(com\\.apple\\.mgmt\\.External\\.[^,\\s]+)");
                Matcher m = p.matcher(subj);
                if (m.find()) {
                    return m.group(1);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract push topic: {}", e.getMessage());
        }
        return null;
    }

    private void refreshDepTokenInfo(EnrollmentStatus status) {
        try {
            File tokenFile = new File(CERTS_DIR + "/server_token.json");
            if (tokenFile.exists()) {
                // DEP token is stored and valid
                status.setDepTokenUploaded(true);
                // Note: Detailed token parsing would require reading the JSON file
                // For now, just mark as uploaded if file exists
                logger.info("DEP token file found.");
            } else {
                status.setDepTokenUploaded(false);
                status.setDepTokenConsumerKey(null);
                status.setDepTokenNotAfter(null);
                status.setDepOrgName(null);
                status.setDepOrgEmail(null);
                status.setDepOrgPhone(null);
                status.setDepOrgAddress(null);
            }
        } catch (Exception e) {
            logger.error("Failed to refresh DEP token info: {}", e.getMessage());
        }
    }

    private void refreshVppTokenInfo(EnrollmentStatus status) {
        try {
            File tokenFile = new File(CERTS_DIR + "/vpp_token.vpp");
            if (tokenFile.exists()) {
                status.setVppTokenUploaded(true);
                logger.info("VPP token file found.");
                // Fetch org info from Apple VPP API
                try {
                    Map<String, String> config = appleVppService.getClientConfig();
                    if (config.containsKey("orgName")) {
                        status.setVppOrgName(config.get("orgName"));
                    }
                    if (config.containsKey("locationName")) {
                        status.setVppLocationName(config.get("locationName"));
                    }
                    logger.info("VPP org info refreshed: orgName={}, locationName={}",
                            config.get("orgName"), config.get("locationName"));
                } catch (Exception e) {
                    logger.warn("Could not fetch VPP client config from Apple: {}", e.getMessage());
                }
            } else {
                status.setVppTokenUploaded(false);
                status.setVppTokenNotAfter(null);
                status.setVppOrgName(null);
                status.setVppLocationName(null);
            }
        } catch (Exception e) {
            logger.error("Failed to refresh VPP token info: {}", e.getMessage());
        }
    }

    private void refreshVendorCertificateInfo(EnrollmentStatus status) {
        try {
            if (vendorCertResource.exists()) {
                try (InputStream is = vendorCertResource.getInputStream()) {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
                    status.setVendorCertNotAfter(cert.getNotAfter());
                    logger.info("Vendor certificate expires: {}", cert.getNotAfter());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to refresh vendor certificate info: {}", e.getMessage());
        }
    }

}

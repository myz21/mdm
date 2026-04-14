package com.arcyintel.arcops.apple_mdm.services.apple.abm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

public interface AppleAbmTokenService {
    static final Logger logger = LoggerFactory.getLogger(AppleAbmTokenService.class);
    static final String CERTS_DIR = "certs/apple";
    static final String DEP_KEY_FILE = CERTS_DIR + "/dep.key";
    static final String DEP_DER_FILE = CERTS_DIR + "/dep.der";
    static final String CUSTOMER_PRIVATE_KEY_FILE = "customer.key";
    static final int RSA_KEY_SIZE = 2048;
    static final String SIGN_ALGORITHM = "SHA256withRSA";
    static final long CERT_VALIDITY_MS = 365L * 24 * 60 * 60 * 1000;

    void uploadServerToken(MultipartFile file) throws Exception;

    void uploadVppToken(MultipartFile file) throws Exception;

    String generateDepCertificateAndSave() throws Exception;
}
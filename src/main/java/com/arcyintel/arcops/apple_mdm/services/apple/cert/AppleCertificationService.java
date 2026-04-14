package com.arcyintel.arcops.apple_mdm.services.apple.cert;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.PrivateKey;

public interface AppleCertificationService {

    /**
     * Generates a Base64-encoded plist file containing the CSR for Apple Push Certificate.
     * @param companyName Optional company/organization name (overrides config if provided)
     * @param email Optional contact email (overrides config if provided)
     * @return Base64-encoded plist string
     */
    String generatePlist(String companyName, String email) throws Exception;


    void uploadCertificate(MultipartFile file) throws IOException;

    PrivateKey loadPrivateKeyFromDisk() throws Exception;

    /**
     * Reloads the customer private key from disk.
     * Call this after certificate renewal to update cached key.
     */
    void reloadCustomerPrivateKey() throws Exception;

    /**
     * Renews the vendor certificate (mdm.pem).
     * This is a protected operation that requires a secret password.
     * @param file The new vendor certificate file
     * @param secretPassword The secret password to authorize this operation
     * @throws SecurityException if password is invalid
     * @throws IOException if file operation fails
     */
    void renewVendorCertificate(org.springframework.web.multipart.MultipartFile file, String secretPassword) throws Exception;
}
package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.services.apple.cert.ApplePushCredentialService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AppleEnrollmentService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;


@Service
@RequiredArgsConstructor
public class AppleEnrollmentServiceImpl implements AppleEnrollmentService {
    private static final Logger logger = LogManager.getLogger(AppleEnrollmentServiceImpl.class);

    private static final int BASE64_LINE_LENGTH = 64;

    private final ApplePushCredentialService applePushCredentialService;

    @Value("${mdm.enroll.paths.identityB64}")
    private Resource identityB64Resource;

    @Value("${host}")
    private String apiHost;

    @Value("${mdm.organization.name:ARCOPS}")
    private String organizationName;

    public String generateEnrollProfile() throws Exception {
        return generateEnrollProfile(null);
    }

    @Override
    public String generateEnrollProfile(String organizationMagic) throws Exception {

        logger.info("Starting the process to generate enroll.mobileconfig (orgMagic={})", organizationMagic != null ? "present" : "null");

        // 1. Read and prepare the identity PKCS#12 certificate
        logger.info("Attempting to read the identity PKCS#12 certificate from resource: {}", identityB64Resource.getFilename());
        String identityB64 = readResourceAsString(identityB64Resource).replaceAll("\\s", "");
        logger.info("Successfully read and processed the identity PKCS#12 certificate. Length: {} characters", identityB64.length());
        String wrappedIdentityB64 = wrapBase64(identityB64);
        logger.debug("Wrapped identity PKCS#12 certificate into Base64 format");

        // 2. Extract the APNs push topic
        logger.info("Extracting the APNs push topic from the certificate");
        String topic = applePushCredentialService.getPushTopic();
        logger.info("Successfully extracted APNs push topic: {}", topic);

        // 3. Generate the enroll.mobileconfig content
        logger.info("Building the enroll.mobileconfig content using the identity certificate and APNs topic");
        String mobileconfig = buildMobileconfig(wrappedIdentityB64, topic, organizationMagic);
        logger.info("Successfully built the enroll.mobileconfig content");

        // 4. Generate APNs client P12 file if needed
        logger.info("Checking and generating the APNs client P12 file if required");
        applePushCredentialService.generateApnsP12();
        logger.info("APNs client P12 file generation completed");

        logger.info("Enroll.mobileconfig generation process completed successfully");
        return mobileconfig;
    }

    private String readResourceAsString(Resource resource) throws IOException {
        if (resource == null) {
            logger.error("The provided resource is null. Unable to proceed with reading.");
            throw new IOException("Provided resource is null.");
        }
        if (!resource.exists()) {
            logger.error("Resource not found: {}. Ensure the file exists and the path is correct.", resource.getFilename());
            throw new IOException("Resource not found: " + resource.getFilename());
        }
        try (InputStream is = resource.getInputStream()) {
            logger.info("Successfully opened the resource: {} for reading.", resource.getFilename());
            logger.debug("Reading the content of the resource: {}", resource.getFilename());
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            logger.info("Successfully read the content of the resource: {}. Content length: {} characters.", resource.getFilename(), content.length());
            return content;
        } catch (IOException e) {
            logger.error("An error occurred while reading the resource: {}. Error: {}", resource.getFilename(), e.getMessage(), e);
            throw e;
        }
    }

    private String wrapBase64(String input) {
        if (input == null || input.isEmpty()) {
            logger.warn("The provided Base64 input string is null or empty. Returning the input as is.");
            return input;
        }
        StringBuilder sb = new StringBuilder();
        logger.debug("Starting to wrap the Base64 string into lines of length {} characters.", AppleEnrollmentServiceImpl.BASE64_LINE_LENGTH);
        for (int i = 0; i < input.length(); i += AppleEnrollmentServiceImpl.BASE64_LINE_LENGTH) {
            int end = Math.min(i + AppleEnrollmentServiceImpl.BASE64_LINE_LENGTH, input.length());
            sb.append(input, i, end).append("\n");
            logger.trace("Wrapped Base64 substring from index {} to {}.", i, end);
        }
        logger.debug("Completed wrapping the Base64 string into lines of length {} characters.", AppleEnrollmentServiceImpl.BASE64_LINE_LENGTH);
        return sb.toString();
    }

    private String buildMobileconfig(String identityB64, String topic, String organizationMagic) {
        logger.debug("Starting to build the mobileconfig XML for the enrollment profile.");

        if (identityB64 == null || identityB64.isEmpty()) {
            logger.error("The provided identity Base64 string is null or empty. Unable to build mobileconfig.");
            throw new IllegalArgumentException("Identity Base64 string cannot be null or empty.");
        }

        if (topic == null || topic.trim().isEmpty()) {
            logger.error("The provided APNs topic is null or empty. Unable to build mobileconfig.");
            throw new IllegalArgumentException("APNs topic cannot be null or empty.");
        }

        logger.info("Building the mobileconfig XML with the provided identity Base64 string and APNs topic.");
        logger.debug("Identity Base64 string length: {} characters", identityB64.length());
        logger.debug("APNs topic: {}", topic.trim());

        String checkInUrl = apiHost + "/mdm/checkin"
                + (organizationMagic != null && !organizationMagic.isBlank()
                        ? "?orgMagic=" + organizationMagic : "");
        logger.debug("Check-in URL for MDM: {}", checkInUrl);

        String connectUrl = apiHost + "/mdm/connect";
        logger.debug("Check-out URL for MDM: {}", connectUrl);

        // Derive reverse-DNS identifier from host (e.g. "https://test.uconos.com/api/apple" → "com.uconos.test")
        String orgId = deriveOrgIdentifier();

        String mobileconfig = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                  <dict>
                    <key>PayloadContent</key>
                    <array>
                      <dict>
                        <key>PayloadType</key><string>com.apple.security.pkcs12</string>
                        <key>PayloadVersion</key><integer>1</integer>
                        <key>PayloadIdentifier</key><string>%1$s.pkcs12.identity</string>
                        <key>PayloadUUID</key><string>d2f06074-14ef-4076-9f4e-dd154bab2238</string>
                        <key>PayloadDisplayName</key><string>Identity Certificate PKCS12</string>
                        <key>PayloadDescription</key><string>Contains the identity certificate for secure communication.</string>
                        <key>Password</key><string>12345678</string>
                        <key>PayloadContent</key><data>
                %5$s
                        </data>
                      </dict>
                      <dict>
                        <key>AccessRights</key><integer>8191</integer>
                        <key>CheckInURL</key><string>%3$s</string>
                        <key>CheckOutWhenRemoved</key><true/>
                        <key>PayloadDescription</key><string>Enrolls the device with the %2$s MDM server.</string>
                        <key>PayloadIdentifier</key><string>%1$s.mdm.enroll</string>
                        <key>PayloadOrganization</key><string>%2$s</string>
                        <key>PayloadType</key><string>com.apple.mdm</string>
                        <key>PayloadUUID</key><string>D6EF7D21-18B0-489C-82B8-7607E8889880</string>
                        <key>PayloadVersion</key><integer>1</integer>
                        <key>ServerCapabilities</key>
                        <array>
                          <string>com.apple.mdm.per-user-connections</string>
                          <string>com.apple.mdm.bootstraptoken</string>
                        </array>
                        <key>ServerURL</key><string>%4$s</string>
                        <key>Topic</key><string>%6$s</string>
                        <key>IdentityCertificateUUID</key><string>d2f06074-14ef-4076-9f4e-dd154bab2238</string>
                        <key>SignMessage</key><true/>
                      </dict>
                    </array>
                    <key>PayloadDescription</key><string>The server may alter your settings as part of the enrollment process.</string>
                    <key>PayloadDisplayName</key><string>Enrollment Profile</string>
                    <key>PayloadIdentifier</key><string>%1$s.mdm.config</string>
                    <key>PayloadOrganization</key><string>%2$s MDM</string>
                    <key>PayloadScope</key><string>System</string>
                    <key>PayloadType</key><string>Configuration</string>
                    <key>PayloadUUID</key><string>6EBD3C25-DB23-435B-89F2-E4E1E0A113E1</string>
                    <key>PayloadVersion</key><integer>1</integer>
                  </dict>
                </plist>
                """.formatted(orgId, organizationName, checkInUrl, connectUrl, identityB64, topic.trim());

        logger.info("Successfully built the mobileconfig XML for the enrollment profile.");
        logger.debug("Generated mobileconfig XML length: {} characters", mobileconfig.length());

        return mobileconfig;
    }

    /**
     * Derives a reverse-DNS identifier from the host URL.
     * e.g. "https://test.uconos.com/api/apple" → "com.uconos.test"
     * e.g. "https://dev.uconos.com/api/apple"  → "com.uconos.dev"
     */
    private String deriveOrgIdentifier() {
        try {
            String host = URI.create(apiHost).getHost();
            if (host == null || host.isBlank()) host = apiHost;
            String[] parts = host.split("\\.");
            String[] reversed = new String[parts.length];
            for (int i = 0; i < parts.length; i++) {
                reversed[i] = parts[parts.length - 1 - i];
            }
            return String.join(".", reversed);
        } catch (Exception e) {
            logger.warn("Could not derive org identifier from host '{}', using fallback", apiHost);
            return "com.arcops";
        }
    }
}

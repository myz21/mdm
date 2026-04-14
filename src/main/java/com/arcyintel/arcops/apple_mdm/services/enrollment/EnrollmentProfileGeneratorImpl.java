package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.EnrollmentProfileType;
import com.arcyintel.arcops.apple_mdm.services.apple.cert.ApplePushCredentialService;
import com.arcyintel.arcops.apple_mdm.services.enrollment.EnrollmentProfileGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Implementation of EnrollmentProfileGenerator.
 *
 * Generates MDM enrollment profiles for different enrollment types:
 * - Standard device enrollment (DEP, profile-based)
 * - Account-Driven User Enrollment (BYOD)
 * - Account-Driven Device Enrollment (ADDE)
 */
@Service
@RequiredArgsConstructor
public class EnrollmentProfileGeneratorImpl implements EnrollmentProfileGenerator {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentProfileGeneratorImpl.class);
    private static final int BASE64_LINE_LENGTH = 64;

    private final ApplePushCredentialService applePushCredentialService;

    @Value("${mdm.enroll.paths.identityB64}")
    private Resource identityB64Resource;

    @Value("${host}")
    private String apiHost;

    @Value("${mdm.organization.name:ARCOPS}")
    private String organizationName;

    @Override
    public String generateDeviceEnrollmentProfile() throws Exception {
        return generateProfile(EnrollmentProfileType.DEVICE, null);
    }

    @Override
    public String generateUserEnrollmentProfile(String managedAppleId) throws Exception {
        if (managedAppleId == null || managedAppleId.isBlank()) {
            throw new IllegalArgumentException("Managed Apple ID is required for User Enrollment");
        }
        return generateProfile(EnrollmentProfileType.USER_ENROLLMENT, managedAppleId);
    }

    @Override
    public String generateAccountDrivenDeviceEnrollmentProfile(String managedAppleId) throws Exception {
        if (managedAppleId == null || managedAppleId.isBlank()) {
            throw new IllegalArgumentException("Managed Apple ID is required for Account-Driven Device Enrollment");
        }
        return generateProfile(EnrollmentProfileType.ACCOUNT_DRIVEN_DEVICE, managedAppleId);
    }

    @Override
    public String generateProfile(EnrollmentProfileType type, String managedAppleId) throws Exception {
        return generateProfile(type, managedAppleId, null);
    }

    @Override
    public String generateProfile(EnrollmentProfileType type, String managedAppleId, String orgMagic) throws Exception {
        logger.info("Generating enrollment profile. Type: {}, ManagedAppleId: {}, orgMagic: {}",
                type, managedAppleId != null ? "provided" : "not provided",
                orgMagic != null ? "provided" : "not provided");

        // Read and prepare the identity certificate
        String identityB64 = readResourceAsString(identityB64Resource).replaceAll("\\s", "");
        String wrappedIdentityB64 = wrapBase64(identityB64);

        // Get the APNs push topic
        String topic = applePushCredentialService.getPushTopic();

        // Ensure APNs client P12 exists
        applePushCredentialService.generateApnsP12();

        // Build CheckInURL with optional orgMagic
        String checkInUrl = apiHost + "/mdm/checkin"
                + (orgMagic != null && !orgMagic.isBlank() ? "?orgMagic=" + orgMagic : "");

        // Build the profile based on type
        String profile = switch (type) {
            case DEVICE -> buildDeviceEnrollmentProfile(wrappedIdentityB64, topic);
            case USER_ENROLLMENT -> buildUserEnrollmentProfile(wrappedIdentityB64, topic, managedAppleId, checkInUrl);
            case ACCOUNT_DRIVEN_DEVICE -> buildAccountDrivenDeviceEnrollmentProfile(wrappedIdentityB64, topic, managedAppleId, checkInUrl);
        };

        logger.info("Successfully generated {} enrollment profile", type);
        return profile;
    }

    /**
     * Builds a standard device enrollment profile.
     */
    private String buildDeviceEnrollmentProfile(String identityB64, String topic) {
        String checkInUrl = apiHost + "/mdm/checkin";
        String connectUrl = apiHost + "/mdm/connect";
        String orgId = deriveOrgIdentifier();

        return """
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
                        <key>PayloadDisplayName</key><string>Identity Certificate</string>
                        <key>Password</key><string>12345678</string>
                        <key>PayloadContent</key><data>
                %2$s
                        </data>
                      </dict>
                      <dict>
                        <key>AccessRights</key><integer>8191</integer>
                        <key>CheckInURL</key><string>%3$s</string>
                        <key>CheckOutWhenRemoved</key><true/>
                        <key>PayloadDescription</key><string>Enrolls the device with MDM server.</string>
                        <key>PayloadIdentifier</key><string>%1$s.mdm.enroll</string>
                        <key>PayloadOrganization</key><string>%4$s</string>
                        <key>PayloadType</key><string>com.apple.mdm</string>
                        <key>PayloadUUID</key><string>D6EF7D21-18B0-489C-82B8-7607E8889880</string>
                        <key>PayloadVersion</key><integer>1</integer>
                        <key>ServerCapabilities</key>
                        <array>
                          <string>com.apple.mdm.per-user-connections</string>
                          <string>com.apple.mdm.bootstraptoken</string>
                        </array>
                        <key>ServerURL</key><string>%5$s</string>
                        <key>Topic</key><string>%6$s</string>
                        <key>IdentityCertificateUUID</key><string>d2f06074-14ef-4076-9f4e-dd154bab2238</string>
                        <key>SignMessage</key><true/>
                      </dict>
                    </array>
                    <key>PayloadDescription</key><string>Device Enrollment Profile</string>
                    <key>PayloadDisplayName</key><string>%4$s MDM Enrollment</string>
                    <key>PayloadIdentifier</key><string>%1$s.mdm.device.enrollment</string>
                    <key>PayloadOrganization</key><string>%4$s</string>
                    <key>PayloadScope</key><string>System</string>
                    <key>PayloadType</key><string>Configuration</string>
                    <key>PayloadUUID</key><string>%7$s</string>
                    <key>PayloadVersion</key><integer>1</integer>
                  </dict>
                </plist>
                """.formatted(
                orgId,              // 1$s - reverse-DNS identifier
                identityB64,        // 2$s - identity certificate
                checkInUrl,         // 3$s - check-in URL
                organizationName,   // 4$s - organization name
                connectUrl,         // 5$s - server URL
                topic.trim(),       // 6$s - APNs topic
                UUID.randomUUID()   // 7$s - payload UUID
        );
    }

    /**
     * Builds a User Enrollment profile for BYOD.
     *
     * Key differences from device enrollment:
     * - EnrollmentMode = BYOD
     * - AssignedManagedAppleID specified
     * - AccessRights MUST NOT be present (Apple requirement for BYOD)
     */
    private String buildUserEnrollmentProfile(String identityB64, String topic, String managedAppleId, String checkInUrl) {
        String connectUrl = apiHost + "/mdm/connect";
        String orgId = deriveOrgIdentifier();
        String pkcs12PayloadUuid = UUID.randomUUID().toString();

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                  <dict>
                    <key>PayloadContent</key>
                    <array>
                      <dict>
                        <key>PayloadType</key><string>com.apple.security.pkcs12</string>
                        <key>PayloadVersion</key><integer>1</integer>
                        <key>PayloadIdentifier</key><string>%1$s.pkcs12.identity.user</string>
                        <key>PayloadUUID</key><string>%2$s</string>
                        <key>PayloadDisplayName</key><string>Identity Certificate</string>
                        <key>Password</key><string>12345678</string>
                        <key>PayloadContent</key><data>
                %3$s
                        </data>
                      </dict>
                      <dict>
                        <key>AssignedManagedAppleID</key><string>%4$s</string>
                        <key>CheckInURL</key><string>%5$s</string>
                        <key>CheckOutWhenRemoved</key><true/>
                        <key>EnrollmentMode</key><string>BYOD</string>
                        <key>PayloadDescription</key><string>Enrolls your personal device with work management.</string>
                        <key>PayloadIdentifier</key><string>%1$s.mdm.user.enroll</string>
                        <key>PayloadOrganization</key><string>%6$s</string>
                        <key>PayloadType</key><string>com.apple.mdm</string>
                        <key>PayloadUUID</key><string>%7$s</string>
                        <key>PayloadVersion</key><integer>1</integer>
                        <key>ServerCapabilities</key>
                        <array>
                          <string>com.apple.mdm.per-user-connections</string>
                        </array>
                        <key>ServerURL</key><string>%8$s</string>
                        <key>Topic</key><string>%9$s</string>
                        <key>IdentityCertificateUUID</key><string>%2$s</string>
                        <key>SignMessage</key><true/>
                      </dict>
                    </array>
                    <key>PayloadDescription</key><string>This profile enables your organization to manage work apps and data on your personal device. Your personal data remains private.</string>
                    <key>PayloadDisplayName</key><string>%6$s User Enrollment</string>
                    <key>PayloadIdentifier</key><string>%1$s.mdm.user.enrollment</string>
                    <key>PayloadOrganization</key><string>%6$s</string>
                    <key>PayloadScope</key><string>User</string>
                    <key>PayloadType</key><string>Configuration</string>
                    <key>PayloadUUID</key><string>%10$s</string>
                    <key>PayloadVersion</key><integer>1</integer>
                  </dict>
                </plist>
                """.formatted(
                orgId,              // 1$s - reverse-DNS identifier
                pkcs12PayloadUuid,  // 2$s - PKCS12 payload UUID (reused for IdentityCertificateUUID)
                identityB64,        // 3$s - identity certificate
                managedAppleId,     // 4$s - managed Apple ID
                checkInUrl,         // 5$s - check-in URL
                organizationName,   // 6$s - organization name
                UUID.randomUUID(),  // 7$s - MDM payload UUID
                connectUrl,         // 8$s - server URL
                topic.trim(),       // 9$s - APNs topic
                UUID.randomUUID()   // 10$s - configuration payload UUID
        );
    }

    /**
     * Builds an Account-Driven Device Enrollment profile.
     *
     * Similar to device enrollment but with ManagedAppleID specified.
     * Full MDM control, but enrollment initiated via Managed Apple ID.
     */
    private String buildAccountDrivenDeviceEnrollmentProfile(String identityB64, String topic, String managedAppleId, String checkInUrl) {
        String connectUrl = apiHost + "/mdm/connect";
        String orgId = deriveOrgIdentifier();
        String pkcs12PayloadUuid = UUID.randomUUID().toString();

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                  <dict>
                    <key>PayloadContent</key>
                    <array>
                      <dict>
                        <key>PayloadType</key><string>com.apple.security.pkcs12</string>
                        <key>PayloadVersion</key><integer>1</integer>
                        <key>PayloadIdentifier</key><string>%1$s.pkcs12.identity.adde</string>
                        <key>PayloadUUID</key><string>%2$s</string>
                        <key>PayloadDisplayName</key><string>Identity Certificate</string>
                        <key>Password</key><string>12345678</string>
                        <key>PayloadContent</key><data>
                %3$s
                        </data>
                      </dict>
                      <dict>
                        <key>AccessRights</key><integer>8191</integer>
                        <key>CheckInURL</key><string>%4$s</string>
                        <key>CheckOutWhenRemoved</key><true/>
                        <key>EnrollmentMode</key><string>ADDE</string>
                        <key>AssignedManagedAppleID</key><string>%5$s</string>
                        <key>PayloadDescription</key><string>Enrolls this device with full management capabilities.</string>
                        <key>PayloadIdentifier</key><string>%1$s.mdm.adde.enroll</string>
                        <key>PayloadOrganization</key><string>%6$s</string>
                        <key>PayloadType</key><string>com.apple.mdm</string>
                        <key>PayloadUUID</key><string>%7$s</string>
                        <key>PayloadVersion</key><integer>1</integer>
                        <key>ServerCapabilities</key>
                        <array>
                          <string>com.apple.mdm.per-user-connections</string>
                          <string>com.apple.mdm.bootstraptoken</string>
                        </array>
                        <key>ServerURL</key><string>%8$s</string>
                        <key>Topic</key><string>%9$s</string>
                        <key>IdentityCertificateUUID</key><string>%2$s</string>
                        <key>SignMessage</key><true/>
                      </dict>
                    </array>
                    <key>PayloadDescription</key><string>Account-Driven Device Enrollment Profile</string>
                    <key>PayloadDisplayName</key><string>%6$s Device Enrollment</string>
                    <key>PayloadIdentifier</key><string>%1$s.mdm.adde.enrollment</string>
                    <key>PayloadOrganization</key><string>%6$s</string>
                    <key>PayloadScope</key><string>System</string>
                    <key>PayloadType</key><string>Configuration</string>
                    <key>PayloadUUID</key><string>%10$s</string>
                    <key>PayloadVersion</key><integer>1</integer>
                  </dict>
                </plist>
                """.formatted(
                orgId,              // 1$s - reverse-DNS identifier
                pkcs12PayloadUuid,  // 2$s - PKCS12 payload UUID (reused for IdentityCertificateUUID)
                identityB64,        // 3$s - identity certificate
                checkInUrl,         // 4$s - check-in URL
                managedAppleId,     // 5$s - managed Apple ID
                organizationName,   // 6$s - organization name
                UUID.randomUUID(),  // 7$s - MDM payload UUID
                connectUrl,         // 8$s - server URL
                topic.trim(),       // 9$s - APNs topic
                UUID.randomUUID()   // 10$s - configuration payload UUID
        );
    }

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

    private String readResourceAsString(Resource resource) throws IOException {
        if (resource == null || !resource.exists()) {
            throw new IOException("Resource not found: " + (resource != null ? resource.getFilename() : "null"));
        }
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String wrapBase64(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i += BASE64_LINE_LENGTH) {
            int end = Math.min(i + BASE64_LINE_LENGTH, input.length());
            sb.append(input, i, end).append("\n");
        }
        return sb.toString();
    }
}

package com.arcyintel.arcops.apple_mdm.services.apple.command.strategy;

import com.dd.plist.NSDictionary;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.models.profile.PolicyContext;
import com.arcyintel.arcops.commons.constants.apple.ApplePlatform;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for platform-specific MDM payload building.
 *
 * Each Apple platform (iOS, macOS, tvOS, visionOS, watchOS) has different
 * MDM capabilities. Implementations build only the payloads and sections
 * that are valid for their platform.
 */
public interface PlatformPayloadStrategy {

    ApplePlatform getPlatform();

    /**
     * Build all MDM profile payloads for this platform.
     * Handles kiosk lockdown, platform-specific payloads, and BYOD filtering.
     */
    void buildPayloads(AppleDevice device,
                       Map<String, Object> payload,
                       Map<String, Object> kioskLockdown,
                       List<NSDictionary> payloadContent,
                       PolicyContext context);

    boolean supportsKioskLockdown();

    default boolean supportsCellular() {
        return false;
    }

    default boolean supportsDeclarativeManagement() {
        return true;
    }

    /**
     * Extract capabilities that need to be delivered via agent rather than MDM profile.
     * Default: empty map (most platforms have nothing to extract).
     */
    default Map<String, Object> extractAgentCapabilities(Map<String, Object> payload) {
        return Map.of();
    }
}

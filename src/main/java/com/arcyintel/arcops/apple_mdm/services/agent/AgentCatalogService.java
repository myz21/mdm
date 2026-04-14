package com.arcyintel.arcops.apple_mdm.services.agent;

import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provides catalog operations for authenticated agent users.
 */
public interface AgentCatalogService {

    List<Map<String, Object>> getCatalogForIdentity(UUID identityId, String deviceUdid);

    ResponseEntity<?> requestInstall(UUID identityId, String bundleId, String trackId, String deviceUdid);
}

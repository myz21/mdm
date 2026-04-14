package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.configs.security.AgentTokenFilter;
import com.arcyintel.arcops.commons.web.RawResponse;
import com.arcyintel.arcops.apple_mdm.services.agent.AgentCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RawResponse
@RequestMapping("/agent/catalog")
@RequiredArgsConstructor
@Tag(name = "Agent Catalog", description = "Agent app catalog endpoints")
public class AgentCatalogController {

    private final AgentCatalogService agentCatalogService;

    @Operation(summary = "Get enriched catalog for authenticated agent user")
    @GetMapping
    public ResponseEntity<?> getCatalog(HttpServletRequest request,
                                        @RequestParam(required = false) String deviceUdid) {
        UUID identityId = getIdentityId(request);
        List<Map<String, Object>> catalogs = agentCatalogService.getCatalogForIdentity(identityId, deviceUdid);
        return ResponseEntity.ok(Map.of("catalogs", catalogs));
    }

    @Operation(summary = "Request app install from catalog")
    @PostMapping("/install")
    public ResponseEntity<?> requestInstall(HttpServletRequest request,
                                            @RequestBody Map<String, String> body) {
        UUID identityId = getIdentityId(request);
        String bundleId = body.get("bundleId");
        String trackId = body.get("trackId");
        String deviceUdid = body.get("deviceUdid");

        return agentCatalogService.requestInstall(identityId, bundleId, trackId, deviceUdid);
    }

    private UUID getIdentityId(HttpServletRequest request) {
        Object attr = request.getAttribute(AgentTokenFilter.IDENTITY_ID_ATTR);
        if (attr == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identity not resolved from token");
        }
        return UUID.fromString(attr.toString());
    }
}

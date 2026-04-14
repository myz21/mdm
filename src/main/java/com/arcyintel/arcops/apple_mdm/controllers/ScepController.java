package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.services.apple.cert.AppleScepService;
import com.arcyintel.arcops.commons.web.RawResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RawResponse
@RequestMapping("/scep")
@RequiredArgsConstructor
@Tag(name = "Apple SCEP", description = "Simple Certificate Enrollment Protocol endpoints for device certificate management.")
public class ScepController {

    private final AppleScepService scepService;

    @Operation(summary = "Handle SCEP GET Request", description = "Handles GetCACaps and GetCACert SCEP operations.")
    @GetMapping
    public ResponseEntity<?> scepGet(@RequestParam("operation") String operation) throws Exception {

        return switch (operation) {

            case "GetCACaps" -> ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(scepService.getCaCaps());

            case "GetCACert" -> ResponseEntity.ok()
                    .contentType(MediaType.valueOf("application/x-x509-ca-cert"))
                    .body(scepService.getCaCertificateBytes());

            default -> ResponseEntity.badRequest().body("Unknown GET Operation: " + operation);
        };
    }

    @Operation(summary = "Handle SCEP POST Request", description = "Handles PKIOperation for SCEP certificate enrollment.")
    @PostMapping
    public ResponseEntity<?> scepPost(@RequestParam("operation") String operation,
                                      @RequestBody byte[] message) throws Exception {

        if (!"PKIOperation".equals(operation)) {
            return ResponseEntity.badRequest().body("Unknown POST Operation");
        }

        byte[] responseBytes = scepService.handlePkiOperation(message);

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/x-pki-message"))
                .body(responseBytes);
    }
}
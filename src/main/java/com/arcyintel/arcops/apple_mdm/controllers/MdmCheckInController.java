package com.arcyintel.arcops.apple_mdm.controllers;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;
import com.arcyintel.arcops.apple_mdm.models.enums.CheckInTypes;
import com.arcyintel.arcops.commons.web.RawResponse;
import com.arcyintel.arcops.apple_mdm.services.enrollment.AppleCheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;

/**
 * Controller handling Apple MDM check-in messages (Authenticate, TokenUpdate, CheckOut).
 */
@RestController
@RawResponse
@RequestMapping("/mdm/checkin")
@RequiredArgsConstructor
@Tag(name = "Apple MDM Check-In", description = "Handles MDM check-in messages from Apple devices.")
public class MdmCheckInController {

    private final AppleCheckinService checkinService;

    /**
     * Endpoint that handles Authenticate, TokenUpdate, and CheckOut MDM messages.
     *
     * @param requestBody the XML plist body sent by the Apple device
     * @return Minimal plist response with HTTP 200
     * @throws Exception if parsing fails or processing fails
     */
    @Operation(
            summary = "Handle Device MDM Check-In",
            description = "Handles Authenticate, TokenUpdate, CheckOut and DeclarativeManagement messages from Apple devices."
    )
    @RequestMapping(
            method = {RequestMethod.POST, RequestMethod.PUT},
            consumes = MediaType.ALL_VALUE,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<String> checkinEndpoint(
            @RequestBody(required = false) String requestBody,
            @RequestParam(name = "orgMagic", required = false) String orgMagic) throws Exception {
        if (requestBody == null || requestBody.isBlank()) {
            return ResponseEntity.badRequest().body("Empty request body");
        }

        NSDictionary dict = parsePlist(requestBody);

        String messageTypeStr = dict.objectForKey("MessageType").toString();

        CheckInTypes messageType;
        try {
            messageType = CheckInTypes.valueOf(messageTypeStr);
        } catch (IllegalArgumentException e) {
            // Unknown message type — acknowledge with 200 to avoid blocking the device
            return ResponseEntity.ok(generateMinimalPlist());
        }

        // Process based on the MessageType
        switch (messageType) {
            case Authenticate -> checkinService.authenticate(dict, orgMagic);
            case TokenUpdate -> checkinService.tokenUpdate(dict);
            case CheckOut -> checkinService.checkOut(dict);
            case DeclarativeManagement -> {
                String result = checkinService.declarativeManagement(dict);
                if (result != null) {
                    return ResponseEntity
                            .ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(result);
                }
            }
            case SetBootstrapToken, GetBootstrapToken -> {
                // macOS bootstrap token messages — acknowledge without processing
            }
        }

        // Always respond with a minimal plist and HTTP 200
        return ResponseEntity.ok(generateMinimalPlist());
    }

    @Operation(
            summary = "Get Declarative Asset Document",
            description = "Returns a declarative management asset document for a specific device."
    )
    @GetMapping(value = "/{deviceId}/{assetIdentifier}", produces = "application/json")
    public ResponseEntity<Map<String, Object>> getDeclarativeAssetDocument(@PathVariable("deviceId") String deviceId,
                                                                           @PathVariable("assetIdentifier") String assetIdentifier) {
        Map<String, Object> doc = checkinService.getDeclarativeAssetDocument(UUID.fromString(deviceId), assetIdentifier);
        return ResponseEntity.ok(doc);
    }

    /**
     * Parses a plist string into a NSDictionary object.
     */
    private NSDictionary parsePlist(String plistContent) throws Exception {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(plistContent.getBytes())) {
            return (NSDictionary) PropertyListParser.parse(inputStream);
        }
    }

    /**
     * Generates a minimal valid plist XML response.
     */
    private String generateMinimalPlist() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                  <dict/>
                </plist>
                """;
    }
}
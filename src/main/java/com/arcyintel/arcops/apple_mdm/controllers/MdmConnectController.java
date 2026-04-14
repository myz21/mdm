package com.arcyintel.arcops.apple_mdm.controllers;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandQueueService;
import com.arcyintel.arcops.commons.web.RawResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;

/**
 * Controller handling MDM Server -> Device communications (Device Check-in and Command processing).
 */
@RestController
@RawResponse
@RequestMapping("/mdm/connect")
@RequiredArgsConstructor
@Tag(name = "Apple MDM Connect", description = "Handles command communication between MDM server and enrolled Apple devices.")
public class MdmConnectController {

    private final AppleCommandQueueService appleCommandQueueService;

    /**
     * Handles incoming /connect requests from Apple devices.
     *
     * @param requestBody XML property list sent by the device
     * @return Next pending command for the device or empty response
     * @throws Exception if parsing or processing fails
     */
    @Operation(
            summary = "Handle Device Connect",
            description = "Handles device responses for MDM commands and sends pending commands back to the device."
    )
    @PutMapping(consumes = MediaType.ALL_VALUE, produces = {MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<String> connectEndpoint(@RequestBody(required = false) String requestBody) throws Exception {

        // Sleep for 2 s
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        if (requestBody == null || requestBody.isBlank()) {
            return ResponseEntity.badRequest().body("Empty request body");
        }

        NSDictionary dict = parsePlist(requestBody);

        // User Enrollment devices send EnrollmentID instead of UDID
        String udid = getOptionalStringFromDict(dict, "UDID");
        String enrollmentId = getOptionalStringFromDict(dict, "EnrollmentID");
        String deviceIdentifier = udid != null ? udid : enrollmentId;
        String commandUUID = getOptionalStringFromDict(dict, "CommandUUID");
        String status = getOptionalStringFromDict(dict, "Status");

        if (deviceIdentifier == null) {
            return ResponseEntity.badRequest().body("Missing device identifier (UDID or EnrollmentID)");
        }

        // Handle device responses
        if (status != null && commandUUID != null) {
            switch (status) {
                case "Acknowledged" -> appleCommandQueueService.handleDeviceResponse(dict);
                case "Error" -> appleCommandQueueService.handleDeviceErrorResponse(dict);
                case "NotNow" -> appleCommandQueueService.handleDeviceNotNowResponse(dict);
            }
        }

        // Check for pending commands
        var command = appleCommandQueueService.popCommand(deviceIdentifier);
        if (command == null || command.getValue() == null) {
            return ResponseEntity.ok().build(); // No command to send
        }

        // Sleep for 2 s
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok(command.getValue().toXMLPropertyList());
    }

    /**
     * Parses a plist XML string into NSDictionary.
     */
    private NSDictionary parsePlist(String plistContent) throws Exception {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(plistContent.getBytes())) {
            return (NSDictionary) PropertyListParser.parse(inputStream);
        }
    }

    /**
     * Safely retrieves an optional string value from NSDictionary (returns null if missing).
     */
    private String getOptionalStringFromDict(NSDictionary dict, String key) {
        return dict.objectForKey(key) != null ? dict.objectForKey(key).toString() : null;
    }
}

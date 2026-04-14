package com.arcyintel.arcops.apple_mdm.controllers;

import com.arcyintel.arcops.apple_mdm.models.api.account.CreateAppleAccountDto;
import com.arcyintel.arcops.apple_mdm.models.api.account.GetAppleAccountDto;
import com.arcyintel.arcops.apple_mdm.models.api.account.UpdateAppleAccountDto;
import com.arcyintel.arcops.apple_mdm.services.account.AppleAccountService;
import com.arcyintel.arcops.commons.api.DynamicListRequestDto;
import org.springframework.data.web.PagedModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
@Tag(name = "Apple Account Management", description = "CRUD operations for Apple accounts and device-account assignments.")
public class AppleAccountController {

    private final AppleAccountService accountService;

    @Operation(summary = "Create Account", description = "Creates a new Apple account.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    @PostMapping
    public ResponseEntity<Void> createAccount(@RequestBody CreateAppleAccountDto dto) {
        accountService.createAccount(dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Update Account", description = "Updates an existing Apple account.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account updated successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateAccount(
            @PathVariable @Parameter(description = "Account ID") String id,
            @RequestBody UpdateAppleAccountDto dto
    ) {
        accountService.updateAccount(id, dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete Account", description = "Soft deletes an Apple account.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable @Parameter(description = "Account ID") String id
    ) {
        accountService.deleteAccount(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get Account", description = "Retrieves an Apple account by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<GetAppleAccountDto> getAccount(
            @PathVariable @Parameter(description = "Account ID") String id
    ) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    @Operation(summary = "List Accounts (Simple)", description = "Retrieves all active Apple accounts.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<List<GetAppleAccountDto>> getAccounts() {
        return ResponseEntity.ok(accountService.getAccounts());
    }

    @Operation(summary = "List Accounts", description = "Retrieves Apple accounts with dynamic filtering and field selection.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content)
    })
    @PostMapping("/list")
    public ResponseEntity<PagedModel<Map<String, Object>>> listAccounts(@RequestBody DynamicListRequestDto request) {
        return ResponseEntity.ok(accountService.listAccounts(request));
    }

    @Operation(summary = "Assign Account to Device", description = "Assigns an Apple account to a device.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account assigned to device"),
            @ApiResponse(responseCode = "400", description = "Device does not support multiple users", content = @Content),
            @ApiResponse(responseCode = "404", description = "Account or device not found", content = @Content)
    })
    @PostMapping("/{accountId}/assign/{deviceId}")
    public ResponseEntity<Void> assignAccountToDevice(
            @PathVariable @Parameter(description = "Account ID") String accountId,
            @PathVariable @Parameter(description = "Device ID") String deviceId
    ) {
        accountService.assignAccountToDevice(accountId, deviceId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Remove Account from Device", description = "Removes an Apple account assignment from a device.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account removed from device"),
            @ApiResponse(responseCode = "404", description = "Account or assignment not found", content = @Content)
    })
    @DeleteMapping("/{accountId}/assign/{deviceId}")
    public ResponseEntity<Void> removeAccountFromDevice(
            @PathVariable @Parameter(description = "Account ID") String accountId,
            @PathVariable @Parameter(description = "Device ID") String deviceId
    ) {
        accountService.removeAccountFromDevice(accountId, deviceId);
        return ResponseEntity.ok().build();
    }
}
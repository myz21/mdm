package com.arcyintel.arcops.apple_mdm.models.api.enrollment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUser {
    private String username;
    private String email;
    private String fullName;
    private String managedAppleId;
    private String externalId;
    private String identitySource;
}

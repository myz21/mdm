package com.arcyintel.arcops.apple_mdm.models.api.enrollment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for sending enrollment invitation email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendEnrollmentEmailDto {

    @NotBlank(message = "Email address is required")
    @Email(message = "Invalid email address")
    private String email;

    /**
     * Optional user name for personalization.
     */
    private String userName;

    /**
     * Optional custom message to include in the email.
     */
    private String customMessage;
}

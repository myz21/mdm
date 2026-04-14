package com.arcyintel.arcops.apple_mdm.services.enrollment;

import com.arcyintel.arcops.apple_mdm.domains.OAuth2ProviderConfig;
import com.arcyintel.arcops.apple_mdm.models.api.enrollment.AuthenticatedUser;
import org.springframework.http.ResponseEntity;

/**
 * Strategy for handling authentication in Account-Driven Enrollment.
 * Implementations handle either Simple (HTTP Basic) or OAuth2 (Bearer JWT) authentication.
 */
public interface AccountDrivenAuthStrategy {

    /**
     * Returns true if this strategy can handle the given Authorization header.
     */
    boolean supports(String authorizationHeader);

    /**
     * Build the 401 challenge response for unauthenticated requests.
     *
     * @param config OAuth2 provider config (null for Simple auth)
     */
    ResponseEntity<?> buildChallengeResponse(OAuth2ProviderConfig config);

    /**
     * Validate credentials and return authenticated user info.
     *
     * @param authorizationHeader The Authorization header value
     * @param config              OAuth2 provider config (null for Simple auth)
     * @return Authenticated user or null if authentication fails
     */
    AuthenticatedUser authenticate(String authorizationHeader, OAuth2ProviderConfig config);
}

package com.arcyintel.arcops.apple_mdm.services.agent;

import com.arcyintel.arcops.apple_mdm.models.api.agent.AgentAuthRequest;
import com.arcyintel.arcops.apple_mdm.models.api.agent.AgentAuthResponse;

/**
 * Handles agent app authentication: login, logout, token validation.
 */
public interface AgentAuthService {

    AgentAuthResponse authenticate(AgentAuthRequest request, String clientIp);

    boolean sendOtp(String email);

    AgentAuthResponse authenticateWithOtp(String email, String otp, String deviceSerialNumber,
                                          String agentVersion, String clientIp);

    void logout(String token, String clientIp);

    boolean validateToken(String token);
}

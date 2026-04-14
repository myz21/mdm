package com.arcyintel.arcops.apple_mdm.services.apple.policy;

import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;

import java.util.Map;

public interface ApplePolicyApplicationService {

    void applyPolicy(AppleDevice device, Map<String, Object> policyToApply) throws Exception;
}

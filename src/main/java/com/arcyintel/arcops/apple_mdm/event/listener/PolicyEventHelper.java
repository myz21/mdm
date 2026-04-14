package com.arcyintel.arcops.apple_mdm.event.listener;

import com.arcyintel.arcops.apple_mdm.domains.Policy;
import io.swagger.v3.core.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PolicyEventHelper {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEventHelper.class);

    public Optional<Policy> convertAndValidate(Object eventPolicy, String eventType) {
        if (eventPolicy == null) {
            logger.error("Policy is null in {} event, skipping.", eventType);
            return Optional.empty();
        }

        Policy policy;
        try {
            policy = Json.mapper().convertValue(eventPolicy, Policy.class);
        } catch (Exception e) {
            logger.error("Failed to convert policy from {} event: {}", eventType, e.getMessage(), e);
            return Optional.empty();
        }

        if (policy == null || policy.getId() == null) {
            logger.error("Converted Policy is null or missing ID in {} event, skipping.", eventType);
            return Optional.empty();
        }

        return Optional.of(policy);
    }
}

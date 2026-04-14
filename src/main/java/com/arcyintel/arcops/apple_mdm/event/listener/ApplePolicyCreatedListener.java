package com.arcyintel.arcops.apple_mdm.event.listener;

import com.arcyintel.arcops.apple_mdm.domains.Policy;
import com.arcyintel.arcops.apple_mdm.repositories.PolicyRepository;
import com.arcyintel.arcops.commons.events.policy.PolicyCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.arcyintel.arcops.commons.constants.events.PolicyEvents.POLICY_CREATE_QUEUE_APPLE;

@Component
@RequiredArgsConstructor
public class ApplePolicyCreatedListener {

    private static final Logger logger = LoggerFactory.getLogger(ApplePolicyCreatedListener.class);

    private final PolicyRepository policyRepository;
    private final PolicyEventHelper policyEventHelper;

    @RabbitListener(queues = POLICY_CREATE_QUEUE_APPLE)
    public void handlePolicyCreatedEvent(PolicyCreatedEvent event) {
        logger.info("Received PolicyCreatedEvent: {}", event);

        Optional<Policy> policyOpt = policyEventHelper.convertAndValidate(event.getPolicy(), "PolicyCreated");
        if (policyOpt.isEmpty()) return;

        Policy policy = policyOpt.get();

        if (policyRepository.existsById(policy.getId())) {
            logger.info("Policy with ID {} already exists. Skipping creation.", policy.getId());
            return;
        }

        logger.info("Saving new policy to database: {}", policy);
        try {
            policy.setStatus("ACTIVE");
            policyRepository.save(policy);
            logger.info("Policy saved successfully.");
        } catch (Exception e) {
            logger.error("Error saving policy to DB: {}", e.getMessage(), e);
        }
    }
}

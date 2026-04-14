package com.arcyintel.arcops.apple_mdm.event.listener;

import com.arcyintel.arcops.apple_mdm.domains.Policy;
import com.arcyintel.arcops.apple_mdm.repositories.PolicyRepository;
import com.arcyintel.arcops.commons.events.policy.PolicyUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.arcyintel.arcops.commons.constants.events.PolicyEvents.POLICY_UPDATE_QUEUE_APPLE;

@Component
@RequiredArgsConstructor
public class ApplePolicyUpdatedListener {

    private static final Logger logger = LoggerFactory.getLogger(ApplePolicyUpdatedListener.class);

    private final PolicyRepository policyRepository;
    private final PolicyEventHelper policyEventHelper;

    @RabbitListener(queues = POLICY_UPDATE_QUEUE_APPLE)
    public void handlePolicyUpdateEvent(PolicyUpdatedEvent event) {
        logger.info("Received PolicyUpdatedEvent: {}", event);

        Optional<Policy> policyOpt = policyEventHelper.convertAndValidate(event.getPolicy(), "PolicyUpdated");
        if (policyOpt.isEmpty()) return;

        Policy updatedPolicy = policyOpt.get();

        if (!policyRepository.existsById(updatedPolicy.getId())) {
            logger.warn("Policy with ID {} does not exist in DB. Skipping update.", updatedPolicy.getId());
            return;
        }

        try {
            updatedPolicy.setStatus("ACTIVE");
            policyRepository.save(updatedPolicy);
            logger.info("Policy with ID {} updated successfully.", updatedPolicy.getId());
        } catch (Exception e) {
            logger.error("Error updating policy in DB: {}", e.getMessage(), e);
        }
    }
}

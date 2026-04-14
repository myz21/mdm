package com.arcyintel.arcops.apple_mdm.event.listener;

import com.arcyintel.arcops.apple_mdm.repositories.PolicyRepository;
import com.arcyintel.arcops.commons.events.policy.PolicyDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.arcyintel.arcops.commons.constants.events.PolicyEvents.POLICY_DELETE_QUEUE_APPLE;

@Component
@RequiredArgsConstructor
public class ApplePolicyDeletedListener {

    private static final Logger logger = LoggerFactory.getLogger(ApplePolicyDeletedListener.class);

    private final PolicyRepository policyRepository;

    @RabbitListener(queues = POLICY_DELETE_QUEUE_APPLE)
    public void handlePolicyDeletedEvent(PolicyDeletedEvent event) {
        logger.info("[Policy] Received PolicyDeletedEvent: {}", event);

        if (event.getPolicyId() == null) {
            logger.warn("[WARN] PolicyDeletedEvent does not contain a policy ID. Skipping.");
            return;
        }

        UUID policyId = event.getPolicyId();

        if (!policyRepository.existsById(policyId)) {
            logger.info("[INFO] Policy with ID {} does not exist. Nothing to delete.", policyId);
            return;
        }

        try {
            policyRepository.delete(policyRepository.findById(policyId).get());
            logger.info("Successfully deleted policy with ID {}", policyId);
        } catch (Exception e) {
            logger.error("[ERROR] Failed to delete policy with ID {}: {}", policyId, e.getMessage(), e);
        }
    }
}
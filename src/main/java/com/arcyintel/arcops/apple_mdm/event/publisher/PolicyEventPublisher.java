package com.arcyintel.arcops.apple_mdm.event.publisher;

import com.arcyintel.arcops.commons.events.policy.PolicyApplicationFailedEvent;
import com.arcyintel.arcops.commons.events.policy.PolicyAppliedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.arcyintel.arcops.commons.constants.events.PolicyEvents.*;

@Service
@RequiredArgsConstructor
public class PolicyEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Async
    public void publishPolicyAppliedEvent(PolicyAppliedEvent event) {
        rabbitTemplate.convertAndSend(POLICY_EVENT_EXCHANGE, POLICY_APPLIED_ROUTE_KEY, event);
    }

    @Async
    public void publishPolicyApplicationFailedEvent(PolicyApplicationFailedEvent event) {
        rabbitTemplate.convertAndSend(POLICY_EVENT_EXCHANGE, POLICY_APPLICATION_FAILED_ROUTE_KEY, event);
    }
}
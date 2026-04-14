package com.arcyintel.arcops.apple_mdm.event.publisher;

import com.arcyintel.arcops.commons.events.account.AccountCreatedEvent;
import com.arcyintel.arcops.commons.events.account.AccountDeletedEvent;
import com.arcyintel.arcops.commons.events.account.AccountSyncEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.arcyintel.arcops.commons.constants.events.AccountEvents.*;

@Service
@RequiredArgsConstructor
public class AccountEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Async
    public void publishAccountSyncEvent(AccountSyncEvent event) {
        rabbitTemplate.convertAndSend(ACCOUNT_EVENT_EXCHANGE, ACCOUNT_SYNC_ROUTE_KEY_APPLE, event);
    }

    @Async
    public void publishAccountCreatedEvent(AccountCreatedEvent event) {
        rabbitTemplate.convertAndSend(ACCOUNT_EVENT_EXCHANGE, ACCOUNT_CREATED_ROUTE_KEY_APPLE, event);
    }

    @Async
    public void publishAccountDeletedEvent(AccountDeletedEvent event) {
        rabbitTemplate.convertAndSend(ACCOUNT_EVENT_EXCHANGE, ACCOUNT_DELETED_ROUTE_KEY_APPLE, event);
    }
}
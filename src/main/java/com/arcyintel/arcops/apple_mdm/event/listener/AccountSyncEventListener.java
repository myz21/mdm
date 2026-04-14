package com.arcyintel.arcops.apple_mdm.event.listener;

import com.arcyintel.arcops.commons.events.account.AccountSyncEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.arcyintel.arcops.commons.constants.events.AccountEvents.*;

@Component
@RequiredArgsConstructor
public class AccountSyncEventListener {

    private static final Logger logger = LoggerFactory.getLogger(AccountSyncEventListener.class);

    private final JdbcTemplate jdbcTemplate;

    @RabbitListener(queues = ACCOUNT_CORE_SYNC_QUEUE_APPLE)
    @Transactional
    public void handleAccountSync(AccountSyncEvent event) {
        logger.info("AccountSyncEvent (core→apple) received: accountId={}, username={}",
                event.getAccountId(), event.getUsername());

        UUID id = event.getAccountId();
        if (id == null) {
            logger.warn("AccountSyncEvent has no accountId. Skipping.");
            return;
        }

        int updated = jdbcTemplate.update("""
                UPDATE apple_account
                SET username = ?, email = ?, full_name = ?, status = ?, identity_id = ?, last_modified_date = NOW()
                WHERE id = ?
                """,
                event.getUsername(), event.getEmail(), event.getFullName(),
                event.getStatus(), event.getIdentityId(), id);

        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO apple_account (id, username, email, full_name, status, identity_id, creation_date, last_modified_date)
                    VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """,
                    id, event.getUsername(), event.getEmail(), event.getFullName(),
                    event.getStatus(), event.getIdentityId());
            logger.info("AppleAccount created: id={}", id);
        } else {
            logger.info("AppleAccount updated: id={}", id);
        }
    }
}

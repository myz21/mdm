package com.arcyintel.arcops.apple_mdm.event.listener;

import com.arcyintel.arcops.commons.events.identity.IdentityDeletedEvent;
import com.arcyintel.arcops.commons.events.identity.IdentitySyncEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.arcyintel.arcops.commons.constants.events.IdentityEvents.*;

@Component
@RequiredArgsConstructor
public class IdentitySyncEventListener {

    private static final Logger logger = LoggerFactory.getLogger(IdentitySyncEventListener.class);

    private final JdbcTemplate jdbcTemplate;

    @RabbitListener(queues = IDENTITY_SYNC_QUEUE_APPLE)
    @Transactional
    public void handleIdentitySync(IdentitySyncEvent event) {
        logger.info("IdentitySyncEvent received: identityId={}, username={}", event.getIdentityId(), event.getUsername());

        UUID id = event.getIdentityId();

        int updated = jdbcTemplate.update("""
                UPDATE apple_identity
                SET username = ?, email = ?, full_name = ?, source = ?,
                    external_id = ?, password_hash = ?, status = ?, last_modified_date = NOW()
                WHERE id = ?
                """,
                event.getUsername(), event.getEmail(), event.getFullName(), event.getSource(),
                event.getExternalId(), event.getPasswordHash(), event.getStatus(), id);

        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO apple_identity (id, username, email, full_name, source, external_id,
                                                password_hash, status, creation_date, last_modified_date)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """,
                    id, event.getUsername(), event.getEmail(), event.getFullName(), event.getSource(),
                    event.getExternalId(), event.getPasswordHash(), event.getStatus());
            logger.info("AppleIdentity created: {}", id);
        } else {
            logger.info("AppleIdentity updated: {}", id);
        }
    }

    @RabbitListener(queues = IDENTITY_DELETED_QUEUE_APPLE)
    @Transactional
    public void handleIdentityDeleted(IdentityDeletedEvent event) {
        logger.info("IdentityDeletedEvent received: identityId={}", event.getIdentityId());

        // Cascade soft-delete associated apple_accounts
        int accountsDeleted = jdbcTemplate.update(
                "UPDATE apple_account SET status = 'DELETED', last_modified_date = NOW() WHERE identity_id = ? AND status <> 'DELETED'",
                event.getIdentityId());
        if (accountsDeleted > 0) {
            logger.info("Cascade soft-deleted {} apple_accounts for identity {}", accountsDeleted, event.getIdentityId());
        }

        // Soft-delete apple_identity
        int updated = jdbcTemplate.update(
                "UPDATE apple_identity SET status = 'DELETED', last_modified_date = NOW() WHERE id = ?",
                event.getIdentityId());
        if (updated > 0) {
            logger.info("AppleIdentity soft-deleted: {}", event.getIdentityId());
        } else {
            logger.warn("AppleIdentity not found for deletion: {}", event.getIdentityId());
        }
    }
}

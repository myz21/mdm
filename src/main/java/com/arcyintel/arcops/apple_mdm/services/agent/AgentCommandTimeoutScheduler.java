package com.arcyintel.arcops.apple_mdm.services.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Periodically marks stale SENT agent commands as FAILED.
 * Commands older than 1 hour without a response are considered timed out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentCommandTimeoutScheduler {

    private static final Duration COMMAND_TIMEOUT = Duration.ofHours(1);

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void timeoutStaleAgentCommands() {
        Instant cutoff = Instant.now().minus(COMMAND_TIMEOUT);
        int count = jdbcTemplate.update(
                "UPDATE agent_command SET status = 'FAILED', " +
                "error_message = 'Command timed out (no device response)', " +
                "response_time = NOW() " +
                "WHERE status = 'SENT' AND request_time < ?",
                Timestamp.from(cutoff));
        if (count > 0) {
            log.info("Marked {} stale agent commands as FAILED (older than {})", count, COMMAND_TIMEOUT);
        }
    }
}

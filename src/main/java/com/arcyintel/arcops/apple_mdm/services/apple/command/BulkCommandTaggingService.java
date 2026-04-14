package com.arcyintel.arcops.apple_mdm.services.apple.command;

import com.arcyintel.arcops.apple_mdm.repositories.AppleCommandRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service responsible for tagging apple_command records with a bulk_command_id.
 * Extracted from BulkDeviceCommandController to move JdbcTemplate usage to the repository layer.
 */
@Service
@RequiredArgsConstructor
public class BulkCommandTaggingService {

    private static final Logger logger = LoggerFactory.getLogger(BulkCommandTaggingService.class);

    private final AppleCommandRepository appleCommandRepository;

    /**
     * Tags apple_command records with the bulk_command_id.
     * Runs with a delay because apple_command records are saved
     * asynchronously by RedisAppleCommandQueueServiceImpl.saveAppleCommandAsync().
     */
    public void scheduleTagging(UUID bulkCommandId, List<String> udids) {
        if (udids.isEmpty()) return;
        try {
            // Wait for async command saves to complete
            Thread.sleep(3000);
            tagCommands(bulkCommandId, udids);
        } catch (Exception e) {
            logger.warn("Failed to tag commands with bulk_command_id: {}", e.getMessage());
        }
    }

    @Transactional
    public void tagCommands(UUID bulkCommandId, List<String> udids) {
        int tagged = appleCommandRepository.tagCommandsWithBulkId(bulkCommandId, udids);
        logger.info("Tagged {} commands with bulk_command_id={}", tagged, bulkCommandId);
    }
}

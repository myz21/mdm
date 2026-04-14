package com.arcyintel.arcops.apple_mdm.services.apple.command;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;
import com.arcyintel.arcops.apple_mdm.domains.AppleCommand;
import com.arcyintel.arcops.apple_mdm.domains.AppleDevice;
import com.arcyintel.arcops.apple_mdm.domains.Policy;
import com.arcyintel.arcops.apple_mdm.event.publisher.PolicyEventPublisher;
import com.arcyintel.arcops.apple_mdm.models.enums.CommandTypes;
import com.arcyintel.arcops.apple_mdm.repositories.AppleCommandRepository;
import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.repositories.PolicyRepository;
import com.arcyintel.arcops.apple_mdm.services.apple.apns.ApplePushService;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandHandlerService;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandQueueService;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandSenderService;
import com.arcyintel.arcops.commons.events.policy.PolicyApplicationFailedEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static com.arcyintel.arcops.commons.constants.apple.CommandSpecificConfigurations.APPLICATIONS_MANAGEMENT;
import static com.arcyintel.arcops.commons.constants.policy.PolicyStatus.STATUS;
import static com.arcyintel.arcops.commons.constants.policy.PolicyStatus.STATUS_FAILED;
import static com.arcyintel.arcops.commons.constants.policy.PolicyTypes.IOS;
import static com.arcyintel.arcops.commons.constants.policy.PolicyTypes.PAYLOAD;

@Service
@ConditionalOnProperty(name = "apple.command.queue.type", havingValue = "redis")
public class RedisAppleCommandQueueServiceImpl implements AppleCommandQueueService {

    private static final Logger logger = LoggerFactory.getLogger(RedisAppleCommandQueueServiceImpl.class);

    private static final String QUEUE_KEY_PREFIX = "apple:command:queue:";
    private static final String INFLIGHT_KEY_PREFIX = "apple:command:inflight:";
    private static final String LOCK_KEY_PREFIX = "apple:command:lock:";
    private static final String REAPER_LOCK_KEY = "apple:command:reaper:lock";
    private static final String STARTUP_LOAD_LOCK = "apple:command:startup-load-lock";

    private static final Duration IN_FLIGHT_TTL = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    // Lua script: release distributed lock only if we still hold it
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    // Lua script: atomic peek-verify-pop for queue head
    // Returns remaining queue size on success, -1 if head doesn't match or queue is empty
    private static final DefaultRedisScript<Long> REMOVE_QUEUE_HEAD_SCRIPT = new DefaultRedisScript<>(
            "local item = redis.call('LINDEX', KEYS[1], 0) " +
                    "if not item then return -1 end " +
                    "if string.find(item, ARGV[1], 1, true) then " +
                    "  redis.call('LPOP', KEYS[1]) " +
                    "  return redis.call('LLEN', KEYS[1]) " +
                    "end " +
                    "return -1",
            Long.class
    );

    private final PolicyEventPublisher policyEventPublisher;
    private final AppleCommandRepository appleCommandRepository;
    private final AppleDeviceRepository appleDeviceRepository;
    private final AppleCommandHandlerService appleCommandHandlerService;
    private final PolicyRepository policyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ApplePushService applePushService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppleCommandSenderService appleCommandSenderService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "redis-command-reaper");
        t.setDaemon(true);
        return t;
    });

    public RedisAppleCommandQueueServiceImpl(
            PolicyEventPublisher policyEventPublisher,
            AppleCommandRepository appleCommandRepository,
            AppleDeviceRepository appleDeviceRepository,
            AppleCommandHandlerService appleCommandHandlerService,
            PolicyRepository policyRepository,
            JdbcTemplate jdbcTemplate,
            ApplePushService applePushService,
            RedisTemplate<String, Object> redisTemplate,
            @Lazy AppleCommandSenderService appleCommandSenderService
    ) {
        this.policyEventPublisher = policyEventPublisher;
        this.appleCommandRepository = appleCommandRepository;
        this.appleDeviceRepository = appleDeviceRepository;
        this.appleCommandHandlerService = appleCommandHandlerService;
        this.policyRepository = policyRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.applePushService = applePushService;
        this.redisTemplate = redisTemplate;
        this.appleCommandSenderService = appleCommandSenderService;
    }

    @PostConstruct
    public void loadPendingCommandsFromDatabase() {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(STARTUP_LOAD_LOCK, "loading", Duration.ofMinutes(2));
        if (!Boolean.TRUE.equals(acquired)) {
            logger.info("Another instance is loading pending commands, skipping");
            return;
        }

        logger.info("Loading pending and executing commands from database into Redis on startup...");
        try {
            List<AppleCommand> pendingCommands = appleCommandRepository.findPendingAndExecutingCommands();
            int loadedCount = 0;

            for (AppleCommand command : pendingCommands) {
                String udid = command.getAppleDeviceUdid();
                if (udid == null || udid.isEmpty()) {
                    logger.warn("Skipping command {} - no device UDID", command.getCommandUUID());
                    continue;
                }

                try {
                    String commandXml = command.getTemplate();

                    // If command was EXECUTING from a previous run, reset to PENDING
                    if (CommandStatus.EXECUTING.name().equals(command.getStatus())) {
                        logger.info("Resetting stale EXECUTING command {} for device {} back to PENDING",
                                command.getCommandUUID(), udid);
                        String resetQuery = "UPDATE apple_command SET status = ?, execution_time = NULL WHERE command_uuid = ?";
                        jdbcTemplate.update(resetQuery, CommandStatus.PENDING.name(), command.getCommandUUID());
                    }

                    // Clear any stale inflight key for this device
                    redisTemplate.delete(getInflightKey(udid));

                    // Push to Redis queue (idempotent: check if already present by queue size)
                    String queueKey = getQueueKey(udid);
                    RedisQueueItem item = new RedisQueueItem(command.getCommandUUID(), commandXml);
                    redisTemplate.opsForList().rightPush(queueKey, serializeQueueItem(item));

                    loadedCount++;
                    logger.debug("Loaded command {} for device {}", command.getCommandUUID(), udid);
                } catch (Exception e) {
                    logger.error("Failed to load command {} for device {}: {}",
                            command.getCommandUUID(), udid, e.getMessage());
                }
            }

            logger.info("Loaded {} pending/executing commands from database into Redis", loadedCount);

            // Send wake-up to each device with pending commands
            Set<String> udidsWithPending = new HashSet<>();
            for (AppleCommand command : pendingCommands) {
                if (command.getAppleDeviceUdid() != null) {
                    udidsWithPending.add(command.getAppleDeviceUdid());
                }
            }
            if (!udidsWithPending.isEmpty()) {
                logger.info("Sending wake-up push to {} devices with pending commands", udidsWithPending.size());
                for (String udid : udidsWithPending) {
                    try {
                        sendWakeUp(udid);
                    } catch (Exception e) {
                        logger.warn("Failed to send startup wake-up to UDID {}: {}", udid, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load pending commands from database: {}", e.getMessage(), e);
        }

        // Start periodic reaper for stale in-flight commands
        scheduler.scheduleAtFixedRate(this::expireStaleInFlightCommands, 30, 30, TimeUnit.SECONDS);
        logger.info("Redis in-flight command reaper started (TTL: {} minutes, check interval: 30s)", IN_FLIGHT_TTL.toMinutes());
    }

    /**
     * Expires in-flight commands stuck in EXECUTING state for longer than IN_FLIGHT_TTL.
     * Uses a distributed lock to ensure only one instance runs the reaper at a time.
     */
    private void expireStaleInFlightCommands() {
        try {
            // Distributed lock: only one instance runs the reaper
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(REAPER_LOCK_KEY, "1", Duration.ofSeconds(25));
            if (!Boolean.TRUE.equals(acquired)) {
                return; // Another instance is running the reaper
            }

            try {
                Instant cutoff = Instant.now().minus(IN_FLIGHT_TTL);
                String query = "SELECT command_uuid, apple_device_udid FROM apple_command " +
                        "WHERE status = 'EXECUTING' AND execution_time < ?";
                List<Map<String, Object>> staleCommands = jdbcTemplate.queryForList(query, Timestamp.from(cutoff));

                for (Map<String, Object> row : staleCommands) {
                    String commandUuid = (String) row.get("command_uuid");
                    String udid = (String) row.get("apple_device_udid");

                    logger.warn("Reaper: expiring stale command {} for device {} (exceeded {} min TTL)",
                            commandUuid, udid, IN_FLIGHT_TTL.toMinutes());

                    // Mark as FAILED in DB
                    failCommand(commandUuid,
                            "Command timed out after " + IN_FLIGHT_TTL.toMinutes() + " minutes (no device response)");

                    // Clear Redis state
                    if (udid != null) {
                        String queueKey = getQueueKey(udid);
                        String inflightKey = getInflightKey(udid);

                        removeCommandFromQueue(queueKey, commandUuid);
                        redisTemplate.delete(inflightKey);

                        try {
                            sendWakeUp(udid);
                        } catch (Exception ignored) {
                        }
                    }
                }

                if (!staleCommands.isEmpty()) {
                    logger.info("Reaper: expired {} stale commands", staleCommands.size());
                }

                // Also clear orphaned in-flight slots where the command is already COMPLETED/FAILED
                // This handles the case where response handler completed but in-flight wasn't cleared
                String orphanQuery = "SELECT DISTINCT apple_device_udid FROM apple_command " +
                        "WHERE status IN ('COMPLETED', 'FAILED', 'CANCELED') " +
                        "AND completion_time < NOW() - INTERVAL '1 minute'";
                List<String> deviceUdids = jdbcTemplate.queryForList(orphanQuery, String.class);

                int orphansCleaned = 0;
                for (String udid : deviceUdids) {
                    String inflightKey = getInflightKey(udid);
                    Object inflightVal = redisTemplate.opsForValue().get(inflightKey);
                    if (inflightVal != null) {
                        String inflightUuid = inflightVal.toString();
                        // Check if this in-flight command is actually completed
                        String statusCheck = "SELECT status FROM apple_command WHERE command_uuid = ?";
                        List<String> statuses = jdbcTemplate.queryForList(statusCheck, String.class, inflightUuid);
                        if (!statuses.isEmpty()) {
                            String cmdStatus = statuses.get(0);
                            if ("COMPLETED".equals(cmdStatus) || "FAILED".equals(cmdStatus) || "CANCELED".equals(cmdStatus)) {
                                redisTemplate.delete(inflightKey);
                                orphansCleaned++;
                                logger.warn("Reaper: cleared orphaned in-flight {} for device {} (status={})",
                                        inflightUuid, udid, cmdStatus);
                                try { sendWakeUp(udid); } catch (Exception ignored) {}
                            }
                        }
                    }
                }
                if (orphansCleaned > 0) {
                    logger.info("Reaper: cleared {} orphaned in-flight slots", orphansCleaned);
                }
            } finally {
                redisTemplate.delete(REAPER_LOCK_KEY);
            }
        } catch (Exception e) {
            logger.error("Error in Redis in-flight command reaper: {}", e.getMessage(), e);
        }
    }

    private String getQueueKey(String udid) {
        return QUEUE_KEY_PREFIX + udid;
    }

    private String getInflightKey(String udid) {
        return INFLIGHT_KEY_PREFIX + udid;
    }

    private static StringBuilder getFailureReasons(NSDictionary response) {
        StringBuilder failureReason = new StringBuilder();
        if (response.containsKey("ErrorChain")) {
            logger.info("ErrorChain found in the response. Extracting failure reasons.");
            NSObject[] errorChain = ((NSArray) response.get("ErrorChain")).getArray();

            for (NSObject error : errorChain) {
                String localizedDescription = ((NSDictionary) error).get("LocalizedDescription").toString();
                failureReason.append(localizedDescription);
                failureReason.append("\n");
                logger.debug("Extracted failure reason: {}", localizedDescription);
            }
        } else {
            logger.warn("ErrorChain is missing in the response. No failure reasons to extract.");
        }
        return failureReason;
    }

    @Override
    public Map.Entry<String, NSDictionary> popCommand(String udid) throws Exception {
        logger.info("Attempting to pop the next command for device with UDID: {}", udid);

        // Acquire per-device distributed lock to prevent double-pop in active-active
        String lockKey = LOCK_KEY_PREFIX + udid;
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            logger.info("Device {} is being processed by another instance. Skipping.", udid);
            return null;
        }

        try {
            String inflightKey = getInflightKey(udid);
            String queueKey = getQueueKey(udid);

            // If this device already has an in-flight command, do NOT issue another one
            Object inflightCommand = redisTemplate.opsForValue().get(inflightKey);
            if (inflightCommand != null) {
                logger.info("Device {} already has an in-flight command ({}). Returning no command.", udid, inflightCommand);
                return null;
            }

            // Get the first command from the queue (FIFO)
            Object queueItem = redisTemplate.opsForList().index(queueKey, 0);
            if (queueItem == null) {
                logger.debug("No commands found in the queue for device with UDID: {}", udid);
                return null;
            }

            RedisQueueItem item = deserializeQueueItem(queueItem);
            if (item == null) {
                logger.warn("Failed to deserialize queue item for UDID: {}", udid);
                return null;
            }

            logger.info("Issuing command with UUID: {} for device with UDID: {}", item.uuid(), udid);

            this.executeCommandAsync(item.uuid());

            // Mark device as in-flight with TTL to prevent permanent blocking
            redisTemplate.opsForValue().set(inflightKey, item.uuid(), IN_FLIGHT_TTL);

            // Parse the command template
            NSDictionary commandDict = (NSDictionary) PropertyListParser.parse(
                    item.commandXml().getBytes(StandardCharsets.UTF_8)
            );

            return new AbstractMap.SimpleEntry<>(item.uuid(), commandDict);
        } finally {
            // Release the distributed lock (only if we still hold it)
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockValue);
        }
    }

    // NOTE: pushCommand must NOT be @Async — callers push multiple commands sequentially
    // and rely on FIFO ordering. Making this async breaks deterministic queue order.
    @Override
    public void pushCommand(String udid, String commandUUID, NSDictionary command, String commandType, boolean isSystem, boolean fromPolicy, UUID policyId) throws Exception {

        logger.info("Pushing command to the queue. UDID: {}, Command UUID: {}, Command Type: {}, From Policy: {}, Policy ID: {}",
                udid, commandUUID, commandType, fromPolicy, policyId);

        // Convert command to XML for storage
        String commandXml = ddPlistToXml(command);

        logger.debug("Saving AppleCommand to the database. UDID: {}, Command UUID: {}", udid, commandUUID);
        this.saveAppleCommandAsync(udid, commandUUID, command, commandType, fromPolicy, policyId, isSystem);

        logger.debug("Adding command to the Redis queue. UDID: {}, Command UUID: {}", udid, commandUUID);
        String queueKey = getQueueKey(udid);
        String inflightKey = getInflightKey(udid);

        // Check if queue was empty before adding
        Long queueSize = redisTemplate.opsForList().size(queueKey);
        boolean wasEmpty = queueSize == null || queueSize == 0;

        // Add to queue
        RedisQueueItem item = new RedisQueueItem(commandUUID, commandXml);
        redisTemplate.opsForList().rightPush(queueKey, serializeQueueItem(item));

        // Wake device only when a first item enters an idle queue and no command is in-flight
        Object inflightCommand = redisTemplate.opsForValue().get(inflightKey);
        if (wasEmpty && inflightCommand == null) {
            try {
                sendWakeUp(udid);
            } catch (Exception e) {
                logger.warn("Failed to send wake-up push for UDID {}: {}", udid, e.getMessage());
            }
        }

        logger.info("Command successfully pushed to the queue. UDID: {}, Command UUID: {}", udid, commandUUID);
    }

    private void sendWakeUp(String deviceIdentifier) {
        Optional<AppleDevice> deviceOpt = appleDeviceRepository.findByUdid(deviceIdentifier);
        if (deviceOpt.isEmpty()) {
            deviceOpt = appleDeviceRepository.findByEnrollmentId(deviceIdentifier);
        }
        deviceOpt.ifPresent(device -> {
            try {
                applePushService.sendMdmWakeUp(device.getToken(), device.getPushMagic());
                logger.info("Wake-up push sent to device '{}'", deviceIdentifier);
            } catch (Exception ex) {
                logger.error("Error sending wake-up push to device '{}': {}", deviceIdentifier, ex.getMessage(), ex);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    @Transactional
    public void handleDeviceResponse(NSDictionary response) {

        logger.info("Handling device response: {}", response);

        CommandCommons cc = getCommandCommons(response);
        if (cc == null) {
            logger.warn("No command found for the given response: {}", response);
            return;
        }

        // Idempotency: skip if already processed (prevents duplicate handling in active-active)
        String status = cc.command().getStatus();
        if (CommandStatus.COMPLETED.name().equals(status) || CommandStatus.FAILED.name().equals(status)) {
            logger.info("Command {} already processed (status={}). Skipping.", cc.commandUUID(), status);
            return;
        }

        // Capture in-flight value before processing
        Object inflightObj = redisTemplate.opsForValue().get(getInflightKey(cc.udid()));
        String inflight = inflightObj != null ? inflightObj.toString() : null;

        logger.info("Processing command with UUID: {} and type: {}", cc.commandUUID(), cc.command().getCommandType());

        try {
            String commandType = cc.command().getCommandType();

            if (commandType.equals(CommandTypes.DEVICE_INFO_COMMAND.getRequestType())) {
                logger.debug("Handling DEVICE_INFO_COMMAND for device with UDID: {}", cc.udid());
                NSObject queryResponses = response.objectForKey("QueryResponses");
                if (queryResponses != null) {
                    HashMap<String, Object> deviceInformation = (HashMap<String, Object>) queryResponses.toJavaObject();
                    appleCommandHandlerService.updateDeviceInfo(cc.udid(), deviceInformation);
                    logger.info("Device information updated successfully for device with UDID: {}", cc.udid());
                } else {
                    logger.warn("QueryResponses missing in DEVICE_INFO response for UDID: {}", cc.udid());
                }
            } else if (commandType.equals(CommandTypes.DEVICE_INSTALL_PROFILE_COMMAND.getRequestType())) {
                logger.debug("Handling DEVICE_INSTALL_PROFILE_COMMAND for device with UDID: {}", cc.udid());
                appleCommandHandlerService.handleInstallProfileCommand(cc.udid(), cc.commandUUID());
                logger.info("Install profile command handled successfully for device with UDID: {}", cc.udid());
            } else if (commandType.equals(CommandTypes.DEVICE_INSTALLED_APPLICATION_LIST_COMMAND.getRequestType())) {
                logger.debug("Handling InstalledApplicationList for UDID: {}", cc.udid());
                NSObject appList = response.get("InstalledApplicationList");
                if (appList != null) {
                    appleCommandHandlerService.handleInstalledApplicationList(cc.udid(), (Object[]) appList.toJavaObject());
                    logger.info("Installed application list handled successfully for device with UDID: {}", cc.udid());
                } else {
                    logger.warn("InstalledApplicationList missing in response for UDID: {}", cc.udid());
                }
            } else if (commandType.equals(CommandTypes.DEVICE_MANAGED_APPLICATION_LIST_COMMAND.getRequestType())) {
                logger.debug("Handling ManagedApplicationList for UDID: {}", cc.udid());
                NSObject managedList = response.get("ManagedApplicationList");
                if (managedList != null) {
                    appleCommandHandlerService.handleManagedApplicationList(cc.udid(), (Map<String, Object>) managedList.toJavaObject());
                    logger.info("Managed application list handled successfully for device with UDID: {}", cc.udid());
                } else {
                    logger.warn("ManagedApplicationList missing in response for UDID: {}", cc.udid());
                }
            } else if (commandType.equals(CommandTypes.DEVICE_INSTALL_APP_COMMAND.getRequestType())
                    || commandType.equals(CommandTypes.DEVICE_INSTALL_ENTERPRISE_APP_COMMAND.getRequestType())
                    || commandType.equals(CommandTypes.DEVICE_REMOVE_APP_COMMAND.getRequestType())) {
                logger.debug("Handling app install/remove response for UDID: {}", cc.udid());

                boolean shouldSync = true;
                Policy commandPolicy = cc.command().getPolicy();
                if (commandPolicy != null) {
                    String policyId = commandPolicy.getId().toString();
                    String installPrefix = cc.udid() + "_" + CommandTypes.DEVICE_INSTALL_APP_COMMAND.getRequestType() + "_" + policyId + "_";
                    String enterpriseInstallPrefix = cc.udid() + "_" + CommandTypes.DEVICE_INSTALL_ENTERPRISE_APP_COMMAND.getRequestType() + "_" + policyId + "_";
                    String removePrefix = cc.udid() + "_" + CommandTypes.DEVICE_REMOVE_APP_COMMAND.getRequestType() + "_" + policyId + "_";

                    String queueKey = getQueueKey(cc.udid());
                    List<Object> queueItems = redisTemplate.opsForList().range(queueKey, 0, -1);
                    if (queueItems != null) {
                        shouldSync = queueItems.stream()
                                .map(this::deserializeQueueItem)
                                .filter(Objects::nonNull)
                                .noneMatch(item -> !item.uuid().equals(cc.commandUUID())
                                        && (item.uuid().startsWith(installPrefix)
                                            || item.uuid().startsWith(enterpriseInstallPrefix)
                                            || item.uuid().startsWith(removePrefix)));
                    }
                }

                if (shouldSync) {
                    try {
                        appleCommandSenderService.syncAppInventory(cc.udid());
                    } catch (Exception e) {
                        logger.error("Failed to trigger app inventory sync for UDID: {}", cc.udid(), e);
                    }
                    logger.info("App command handled and sync triggered for UDID: {}", cc.udid());
                } else {
                    logger.info("Skipping app inventory sync for UDID: {} — more policy app commands remain in queue", cc.udid());
                }
            } else if (commandType.equals(CommandTypes.DEVICE_SECURITY_INFO_COMMAND.getRequestType())) {
                logger.debug("Handling DEVICE_SECURITY_INFO_COMMAND response for UDID: {}", cc.udid());
                NSObject securityInfo = response.get("SecurityInfo");
                if (securityInfo != null) {
                    appleCommandHandlerService.handleSecurityInfoResponse(cc.udid(), (Map<String, Object>) securityInfo.toJavaObject());
                    logger.info("SecurityInfo updated successfully for device: {}", cc.udid());
                } else {
                    logger.warn("SecurityInfo missing in response for UDID: {}", cc.udid());
                }
            } else if (commandType.equals(CommandTypes.DEVICE_LOCATION_COMMAND.getRequestType())) {
                logger.debug("Handling DEVICE_LOCATION_COMMAND response for UDID: {}", cc.udid());
                appleCommandHandlerService.handleDeviceLocationResponse(cc.udid(), response);
                logger.info("Device location handled successfully for device: {}", cc.udid());
            } else if (commandType.equals(CommandTypes.DEVICE_CERTIFICATE_LIST_COMMAND.getRequestType())) {
                logger.debug("Handling DEVICE_CERTIFICATE_LIST_COMMAND response for UDID: {}", cc.udid());
                NSObject certList = response.get("CertificateList");
                if (certList != null) {
                    appleCommandHandlerService.handleCertificateListResponse(cc.udid(), (Object[]) certList.toJavaObject());
                    logger.info("Device Certificate list updated successfully for device: {}", cc.udid());
                } else {
                    logger.warn("CertificateList missing in response for UDID: {}", cc.udid());
                }
            } else if (cc.command().getCommandType().equals(CommandTypes.DEVICE_USER_LIST_COMMAND.getRequestType())) {
                logger.debug("Handling DEVICE_USER_LIST_COMMAND response for UDID: {}", cc.udid());
                NSObject userList = response.get("Users");
                if (userList != null) {
                    appleCommandHandlerService.handleUserListResponse(cc.udid(), (Object[]) userList.toJavaObject());
                    logger.info("Device User list updated successfully for device: {}", cc.udid());
                } else {
                    logger.warn("Users array missing in response for UDID: {}", cc.udid());
                }
            } else {
                appleCommandHandlerService.handleCommandSuccess(cc.udid(), cc.commandUUID(), commandType);
                logger.info("Command {} handled successfully for device with UDID: {}", commandType, cc.udid());
            }

            this.completeCommand(cc.commandUUID());
        } catch (Exception e) {
            logger.error("Error processing command response for UUID: {}. Error: {}", cc.commandUUID(), e.getMessage(), e);
            try {
                this.failCommand(cc.commandUUID(), "Response handler failed: " + e.getMessage());
            } catch (Exception ex) {
                logger.error("Failed to mark command {} as failed after error: {}", cc.commandUUID(), ex.getMessage());
            }
        } finally {
            cleanupAfterResponse(cc.udid(), cc.commandUUID(), inflight);
        }
    }

    /**
     * Atomically removes the head of the queue if it matches the given commandUUID.
     * Uses Lua script to prevent race conditions between peek and pop.
     *
     * @return remaining queue size after removal, or -1 if not removed
     */
    private long removeCommandFromQueue(String queueKey, String commandUUID) {
        Long result = redisTemplate.execute(
                REMOVE_QUEUE_HEAD_SCRIPT,
                List.of(queueKey),
                commandUUID
        );
        long remaining = result != null ? result : -1;
        if (remaining >= 0) {
            logger.info("Removed command {} from queue (remaining: {})", commandUUID, remaining);
        }
        return remaining;
    }

    /**
     * Common cleanup after a device response (success, error, or NotNow).
     * Atomically removes command from queue, clears inflight, and sends wake-up if needed.
     */
    private void cleanupAfterResponse(String udid, String commandUUID, String inflight) {
        String queueKey = getQueueKey(udid);
        String inflightKey = getInflightKey(udid);

        long remaining = removeCommandFromQueue(queueKey, commandUUID);

        // Free the in-flight slot if the completed command is the one we marked
        if (commandUUID.equals(inflight)) {
            redisTemplate.delete(inflightKey);
            logger.debug("Cleared in-flight slot for UDID: {}", udid);
        }

        // Determine remaining size (if removeCommandFromQueue didn't return it, check manually)
        if (remaining < 0) {
            Long size = redisTemplate.opsForList().size(queueKey);
            remaining = size != null ? size : 0;
        }

        if (remaining > 0) {
            try {
                sendWakeUp(udid);
                logger.info("Pending commands remain for UDID {}. Wake-up push sent.", udid);
            } catch (Exception e) {
                logger.warn("Failed to send wake-up for UDID {}: {}", udid, e.getMessage());
            }
        }
    }

    private CommandCommons getCommandCommons(NSDictionary response) {

        String commandUUID = response.get("CommandUUID") != null ? response.get("CommandUUID").toString() : null;
        if (commandUUID == null) {
            logger.warn("CommandUUID is missing in the response: {}", response);
            return null;
        }
        logger.info("Extracted CommandUUID: {}", commandUUID);

        Optional<AppleCommand> commandOpt = appleCommandRepository.findByCommandUUID(commandUUID);
        if (commandOpt.isEmpty()) {
            logger.warn("No AppleCommand found in the repository for CommandUUID: {}", commandUUID);
            return null;
        }
        logger.info("AppleCommand found for CommandUUID: {}", commandUUID);

        String udid = response.containsKey("UDID") ? response.get("UDID").toString() : null;
        String enrollmentId = response.containsKey("EnrollmentID") ? response.get("EnrollmentID").toString() : null;
        String deviceIdentifier = udid != null ? udid : (enrollmentId != null ? enrollmentId : "");

        if (deviceIdentifier.isEmpty()) {
            logger.warn("Neither UDID nor EnrollmentID found in the response for CommandUUID: {}", commandUUID);
        } else {
            logger.info("Device identifier: {} for CommandUUID: {}", deviceIdentifier, commandUUID);
        }

        return new CommandCommons(commandUUID, commandOpt.get(), deviceIdentifier);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Transactional
    public void handleDeviceErrorResponse(NSDictionary response) {

        CommandCommons cc = getCommandCommons(response);
        if (cc == null) {
            logger.error("Failed to process device error response: CommandCommons could not be retrieved from the response: {}", response);
            return;
        }

        // Idempotency check
        String cmdStatus = cc.command().getStatus();
        if (CommandStatus.COMPLETED.name().equals(cmdStatus) || CommandStatus.FAILED.name().equals(cmdStatus)) {
            logger.info("Command {} already processed (status={}). Skipping error handler.", cc.commandUUID(), cmdStatus);
            return;
        }

        Object inflightObj = redisTemplate.opsForValue().get(getInflightKey(cc.udid()));
        String inflight = inflightObj != null ? inflightObj.toString() : null;

        logger.info("Processing error response for command with UUID: {} and device UDID: {}", cc.commandUUID(), cc.udid());

        try {
            String errorMessage = getFailureReasons(response).toString();

            // Handle generic command failure for compliance tracking
            appleCommandHandlerService.handleCommandFailure(cc.udid(), cc.commandUUID(), cc.command().getCommandType(), errorMessage);

            // Handles policy failures; updates application management status
            if (cc.command().getCommandType().equals(CommandTypes.DEVICE_INSTALL_PROFILE_COMMAND.getRequestType())) {
                appleCommandHandlerService.handleFailedInstallProfileCommand(cc.udid(), cc.commandUUID(), errorMessage);
            } else if (cc.command().getPolicy() != null) {
                AppleDevice device = cc.command().getAppleDevice();
                Map<String, Object> appliedPolicy = device.getAppliedPolicy();

                if (!cc.commandUUID().toLowerCase(Locale.ROOT).contains("singleappmode") &&
                        !cc.commandUUID().toLowerCase(Locale.ROOT).contains("homescreen")) {

                    if (cc.command().getCommandType().equals(CommandTypes.DEVICE_INSTALL_APP_COMMAND.getRequestType())
                            || cc.command().getCommandType().equals(CommandTypes.DEVICE_INSTALL_ENTERPRISE_APP_COMMAND.getRequestType())) {

                        Map<String, Object> payload = appliedPolicy != null ? (Map<String, Object>) appliedPolicy.get(PAYLOAD) : null;
                        Map<String, Object> applicationMap = payload != null ? (Map<String, Object>) payload.get(APPLICATIONS_MANAGEMENT) : null;
                        if (applicationMap != null) {
                            applicationMap.put(STATUS, STATUS_FAILED);
                            applicationMap.put("failureReason", errorMessage);
                            device.setAppliedPolicy(appliedPolicy);
                            appleDeviceRepository.save(device);
                            logger.info("Updated applied policy for device UDID: {}. Command UUID: {} marked as failed.", cc.udid(), cc.commandUUID());
                        } else {
                            logger.warn("Cannot update application management status — appliedPolicy or applicationManagement is null for device UDID: {}", cc.udid());
                        }
                    }
                }
            }

            logger.error("Failure reason for command UUID: {}: {}", cc.commandUUID(), errorMessage);
            this.failCommand(cc.commandUUID(), errorMessage);

            policyEventPublisher.publishPolicyApplicationFailedEvent(new PolicyApplicationFailedEvent(
                    cc.command().getAppleDevice().getId(),
                    "Apple",
                    errorMessage
            ));
        } catch (Exception e) {
            logger.error("Error processing error response for command UUID: {}. Error: {}", cc.commandUUID(), e.getMessage(), e);
            try {
                this.failCommand(cc.commandUUID(), e.getMessage());
            } catch (Exception ex) {
                logger.error("Failed to mark command {} as failed after error: {}", cc.commandUUID(), ex.getMessage());
            }
        } finally {
            cleanupAfterResponse(cc.udid(), cc.commandUUID(), inflight);
        }
    }

    @Override
    @Transactional
    public void handleDeviceNotNowResponse(NSDictionary response) {

        CommandCommons cc = getCommandCommons(response);
        if (cc == null) {
            logger.warn("No command found for the given NotNow response: {}", response);
            return;
        }

        // Idempotency check
        String cmdStatus = cc.command().getStatus();
        if (CommandStatus.COMPLETED.name().equals(cmdStatus) || CommandStatus.FAILED.name().equals(cmdStatus)) {
            logger.info("Command {} already processed (status={}). Skipping NotNow handler.", cc.commandUUID(), cmdStatus);
            return;
        }

        Object inflightObj = redisTemplate.opsForValue().get(getInflightKey(cc.udid()));
        String inflight = inflightObj != null ? inflightObj.toString() : null;

        logger.info("Handling NotNow response for command UUID: {} (type: {}) on device UDID: {}",
                cc.commandUUID(), cc.command().getCommandType(), cc.udid());

        try {
            this.failCommand(cc.commandUUID(), "NotNow");
            logger.info("Command {} marked as FAILED (NotNow) for device UDID: {}", cc.commandUUID(), cc.udid());
        } catch (Exception e) {
            logger.error("Failed to mark command {} as FAILED after NotNow: {}", cc.commandUUID(), e.getMessage(), e);
        } finally {
            cleanupAfterResponse(cc.udid(), cc.commandUUID(), inflight);
        }
    }

    public void cancelCommand(String udid, String commandUUID) {
        logger.info("Attempting to cancel command with UUID: {} for device with UDID: {}", commandUUID, udid);

        String queueKey = getQueueKey(udid);
        String inflightKey = getInflightKey(udid);

        Long queueSize = redisTemplate.opsForList().size(queueKey);
        if (queueSize == null || queueSize == 0) {
            logger.warn("No commands found in the queue for device with UDID: {}. Unable to cancel command with UUID: {}", udid, commandUUID);
            return;
        }

        // Try to remove the command from the queue
        boolean removed = false;

        // Check if it's the head command
        Object firstItem = redisTemplate.opsForList().index(queueKey, 0);
        if (firstItem != null) {
            RedisQueueItem item = deserializeQueueItem(firstItem);
            if (item != null && item.uuid().equals(commandUUID)) {
                redisTemplate.opsForList().leftPop(queueKey);
                removed = true;
                // Free in-flight only if we are canceling the currently in-flight command
                Object inflightObj = redisTemplate.opsForValue().get(inflightKey);
                if (inflightObj != null && inflightObj.toString().equals(commandUUID)) {
                    redisTemplate.delete(inflightKey);
                }
            }
        }

        // If not the head, scan and remove from the queue
        if (!removed) {
            List<Object> allItems = redisTemplate.opsForList().range(queueKey, 0, -1);
            if (allItems != null) {
                for (Object obj : allItems) {
                    RedisQueueItem item = deserializeQueueItem(obj);
                    if (item != null && item.uuid().equals(commandUUID)) {
                        redisTemplate.opsForList().remove(queueKey, 1, obj);
                        removed = true;
                        break;
                    }
                }
            }
        }

        if (removed) {
            logger.debug("Marking command with UUID: {} as canceled in the database.", commandUUID);
            this.cancelCommandAsync(commandUUID);
            logger.info("Command with UUID: {} successfully canceled for device with UDID: {}", commandUUID, udid);
        } else {
            logger.warn("Command with UUID: {} not found in the queue for device with UDID: {}", commandUUID, udid);
        }
    }

    public void completeCommand(String commandUUID) {
        logger.info("Marking command with UUID: {} as COMPLETED.", commandUUID);
        this.completeCommandAsync(commandUUID);
        logger.info("Command with UUID: {} has been successfully marked as COMPLETED.", commandUUID);
    }

    public void failCommand(String commandUUID, String failureReason) {
        logger.info("Marking command with UUID: {} as FAILED. Reason: {}", commandUUID, failureReason);
        this.failCommandAsync(commandUUID, failureReason);
        logger.info("Command with UUID: {} has been successfully marked as FAILED.", commandUUID);
    }

    private String ddPlistToXml(NSDictionary dict) throws Exception {
        logger.debug("Converting NSDictionary to XML format.");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PropertyListParser.saveAsXML(dict, baos);
        String xmlString = baos.toString(StandardCharsets.UTF_8);
        logger.debug("NSDictionary successfully converted to XML format.");
        return xmlString;
    }

    @Async
    public void saveAppleCommandAsync(String udid, String commandUUID, NSDictionary commandTemplate, String commandType, boolean fromPolicy, UUID policyId, boolean isSystem) throws Exception {

        logger.info("Starting to save AppleCommand asynchronously. UDID: {}, Command UUID: {}, Command Type: {}, From Policy: {}, Policy ID: {}",
                udid, commandUUID, commandType, fromPolicy, policyId);

        logger.debug("Fetching device with identifier '{}' from the repository.", udid);
        var deviceOpt = appleDeviceRepository.findByUdid(udid);
        if (deviceOpt.isEmpty()) {
            deviceOpt = appleDeviceRepository.findByEnrollmentId(udid);
        }

        if (deviceOpt.isEmpty()) {
            logger.warn("Device with identifier '{}' not found in the repository. Unable to save AppleCommand.", udid);
            return;
        }

        AppleDevice device = deviceOpt.get();
        logger.info("Device with identifier '{}' found. Proceeding to save AppleCommand.", udid);

        logger.debug("Building AppleCommand object for Command UUID: {}.", commandUUID);
        AppleCommand command = AppleCommand.builder()
                .appleDevice(device)
                .commandUUID(commandUUID)
                .commandType(commandType)
                .template(ddPlistToXml(commandTemplate))
                .status(CommandStatus.PENDING.name())
                .requestTime(Instant.now())
                .build();

        if (fromPolicy) {
            logger.debug("Command is associated with a policy. Fetching policy with ID: {}.", policyId);
            Optional<Policy> policyOpt = policyRepository.findById(policyId);
            if (policyOpt.isPresent()) {
                command.setPolicy(policyOpt.get());
                logger.info("Policy with ID: {} associated with Command UUID: {}.", policyId, commandUUID);
            } else {
                logger.warn("Policy with ID: {} not found. Command UUID: {} will not have an associated policy.", policyId, commandUUID);
            }
        }

        if (isSystem) {
            logger.debug("Command is a system command. Setting system flag for Command UUID: {}.", commandUUID);
            command.setCreatedBy("system");
            command.setLastModifiedBy("system");
        }

        logger.debug("Saving AppleCommand object to the database for Command UUID: {}.", commandUUID);
        appleCommandRepository.save(command);
        logger.info("AppleCommand with Command UUID: {} successfully saved to the database.", commandUUID);
    }

    @Async
    public void executeCommandAsync(String commandUUID) {
        logger.info("Starting execution of command with UUID: {}", commandUUID);

        String updateQuery = "UPDATE apple_command SET status = ?, execution_time = ? WHERE command_uuid = ?";
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(updateQuery);
            ps.setString(1, CommandStatus.EXECUTING.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setObject(3, commandUUID, Types.VARCHAR);
            logger.debug("Prepared statement created for updating command status to EXECUTING. Command UUID: {}", commandUUID);
            return ps;
        });

        logger.info("Command with UUID: {} has been marked as EXECUTING in the database.", commandUUID);
    }

    @Async
    public void completeCommandAsync(String commandUUID) {
        logger.info("Starting the process to mark command with UUID: {} as COMPLETED.", commandUUID);

        String updateQuery = "UPDATE apple_command SET status = ?, completion_time = ? WHERE command_uuid = ?";
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(updateQuery);
            ps.setString(1, CommandStatus.COMPLETED.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setObject(3, commandUUID, Types.VARCHAR);
            logger.debug("Prepared statement created for marking command with UUID: {} as COMPLETED.", commandUUID);
            return ps;
        });

        logger.info("Command with UUID: {} has been successfully marked as COMPLETED in the database.", commandUUID);
    }

    @Async
    public void failCommandAsync(String commandUUID, String failureReason) {
        logger.info("Starting the process to mark command with UUID: {} as FAILED. Reason: {}", commandUUID, failureReason);

        String updateQuery = "UPDATE apple_command SET status = ?, completion_time = ?, failure_reason = ? WHERE command_uuid = ?";
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(updateQuery);
            ps.setString(1, CommandStatus.FAILED.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, failureReason);
            ps.setObject(4, commandUUID, Types.VARCHAR);
            logger.debug("Prepared statement created for marking command with UUID: {} as FAILED in the database.", commandUUID);
            return ps;
        });

        logger.info("Command with UUID: {} has been successfully marked as FAILED in the database.", commandUUID);
    }

    @Async
    public void cancelCommandAsync(String commandUUID) {
        logger.info("Starting the process to cancel command with UUID: {}", commandUUID);

        String updateQuery = "UPDATE apple_command SET status = ?, completion_time = ?, failure_reason = ? WHERE command_uuid = ?";
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(updateQuery);
            ps.setString(1, CommandStatus.CANCELED.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, "Command was canceled");
            ps.setObject(4, commandUUID, Types.VARCHAR);
            logger.debug("Prepared statement created for marking command with UUID: {} as CANCELED in the database.", commandUUID);
            return ps;
        });

        logger.info("Command with UUID: {} has been successfully marked as CANCELED in the database.", commandUUID);
    }

    // Serialization helpers for Redis queue items
    private Map<String, String> serializeQueueItem(RedisQueueItem item) {
        Map<String, String> map = new HashMap<>();
        map.put("uuid", item.uuid());
        map.put("commandXml", item.commandXml());
        return map;
    }

    @SuppressWarnings("unchecked")
    private RedisQueueItem deserializeQueueItem(Object obj) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            String uuid = map.get("uuid") != null ? map.get("uuid").toString() : null;
            String commandXml = map.get("commandXml") != null ? map.get("commandXml").toString() : null;
            if (uuid != null && commandXml != null) {
                return new RedisQueueItem(uuid, commandXml);
            }
        }
        return null;
    }

    public enum CommandStatus {
        PENDING,
        EXECUTING,
        COMPLETED,
        FAILED,
        CANCELED
    }

    // Redis queue item - stores command UUID and XML string
    private record RedisQueueItem(String uuid, String commandXml) {
    }

    private record CommandCommons(String commandUUID, AppleCommand command, String udid) {
    }
}
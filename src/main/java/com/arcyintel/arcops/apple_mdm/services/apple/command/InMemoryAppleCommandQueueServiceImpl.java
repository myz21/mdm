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
import jakarta.annotation.PreDestroy;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static com.arcyintel.arcops.commons.constants.apple.CommandSpecificConfigurations.APPLICATIONS_MANAGEMENT;
import static com.arcyintel.arcops.commons.constants.policy.PolicyStatus.STATUS;
import static com.arcyintel.arcops.commons.constants.policy.PolicyStatus.STATUS_FAILED;
import static com.arcyintel.arcops.commons.constants.policy.PolicyTypes.IOS;
import static com.arcyintel.arcops.commons.constants.policy.PolicyTypes.PAYLOAD;

@Service
@ConditionalOnProperty(name = "apple.command.queue.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryAppleCommandQueueServiceImpl implements AppleCommandQueueService {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryAppleCommandQueueServiceImpl.class);
    private final PolicyEventPublisher policyEventPublisher;
    private final AppleCommandRepository appleCommandRepository;
    private final AppleDeviceRepository appleDeviceRepository;
    private final AppleCommandHandlerService appleCommandHandlerService;
    private final PolicyRepository policyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ApplePushService applePushService;
    private final AppleCommandSenderService appleCommandSenderService;


    private static final Duration IN_FLIGHT_TTL = Duration.ofMinutes(5);

    // Per-device FIFO command queues (ConcurrentLinkedDeque for thread-safety across async threads)
    private final ConcurrentHashMap<String, Deque<QueueItem>> commandQueues;
    // Tracks in-flight command per device (UDID -> InFlightEntry)
    private final ConcurrentMap<String, InFlightEntry> inFlightByDevice = new ConcurrentHashMap<>();
    // Scheduled reaper for stale in-flight commands
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "command-timeout-checker");
        t.setDaemon(true);
        return t;
    });

    public InMemoryAppleCommandQueueServiceImpl(
            PolicyEventPublisher policyEventPublisher,
            AppleCommandRepository appleCommandRepository,
            AppleDeviceRepository appleDeviceRepository,
            AppleCommandHandlerService appleCommandHandlerService,
            PolicyRepository policyRepository,
            JdbcTemplate jdbcTemplate,
            ApplePushService applePushService,
            @Lazy AppleCommandSenderService appleCommandSenderService
    ) {
        this.policyEventPublisher = policyEventPublisher;
        this.appleCommandRepository = appleCommandRepository;
        this.appleDeviceRepository = appleDeviceRepository;
        this.appleCommandHandlerService = appleCommandHandlerService;
        this.policyRepository = policyRepository;
        commandQueues = new ConcurrentHashMap<>();
        this.jdbcTemplate = jdbcTemplate;
        this.applePushService = applePushService;
        this.appleCommandSenderService = appleCommandSenderService;
    }

    /**
     * Loads pending and executing commands from the database on application startup.
     * This ensures commands are not lost if the application restarts.
     */
    @PostConstruct
    public void loadPendingCommandsFromDatabase() {
        logger.info("Loading pending and executing commands from database on startup...");
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
                    // Parse the XML template back to NSDictionary
                    NSDictionary commandDict = (NSDictionary) PropertyListParser.parse(
                            command.getTemplate().getBytes(StandardCharsets.UTF_8)
                    );

                    // If command was EXECUTING from a previous run, reset it to PENDING
                    // so the queue starts clean without stale in-flight slots blocking devices
                    if (CommandStatus.EXECUTING.name().equals(command.getStatus())) {
                        logger.info("Resetting stale EXECUTING command {} for device {} back to PENDING",
                                command.getCommandUUID(), udid);
                        String resetQuery = "UPDATE apple_command SET status = ?, execution_time = NULL WHERE command_uuid = ?";
                        jdbcTemplate.update(resetQuery, CommandStatus.PENDING.name(), command.getCommandUUID());
                    }

                    // Add to the in-memory queue (no in-flight entries on startup)
                    Deque<QueueItem> queue = commandQueues.computeIfAbsent(udid, k -> new ConcurrentLinkedDeque<>());
                    queue.addLast(new QueueItem(command.getCommandUUID(), commandDict));

                    loadedCount++;
                    logger.debug("Loaded command {} for device {}", command.getCommandUUID(), udid);
                } catch (Exception e) {
                    logger.error("Failed to parse command template for command {}: {}",
                            command.getCommandUUID(), e.getMessage());
                }
            }

            logger.info("Loaded {} pending/executing commands from database", loadedCount);

            // Send wake-up to each device that has pending commands so they start checking in
            Set<String> udidsWithPending = commandQueues.keySet();
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
        logger.info("In-flight command timeout checker started (TTL: {} minutes, check interval: 30s)", IN_FLIGHT_TTL.toMinutes());
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down command timeout checker scheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Command timeout checker scheduler shut down.");
    }

    /**
     * Expires in-flight commands that have been executing for longer than IN_FLIGHT_TTL.
     * This prevents the command queue from being permanently blocked if a device response is missed.
     */
    private void expireStaleInFlightCommands() {
        try {
            Instant cutoff = Instant.now().minus(IN_FLIGHT_TTL);
            for (var entry : inFlightByDevice.entrySet()) {
                String udid = entry.getKey();
                InFlightEntry inflight = entry.getValue();
                if (inflight.startedAt().isBefore(cutoff)) {
                    logger.warn("In-flight command {} for device {} expired after {} minutes. Releasing slot.",
                            inflight.commandUuid(), udid, IN_FLIGHT_TTL.toMinutes());
                    inFlightByDevice.remove(udid, inflight);

                    // Remove the stale head from the queue
                    Deque<QueueItem> queue = commandQueues.get(udid);
                    if (queue != null && !queue.isEmpty()) {
                        QueueItem head = queue.peekFirst();
                        if (head != null && head.uuid().equals(inflight.commandUuid())) {
                            queue.removeFirst();
                        }
                        if (queue.isEmpty()) {
                            commandQueues.remove(udid, queue);
                        }
                    }

                    // Mark as FAILED in DB
                    failCommand(inflight.commandUuid(),
                            "Command timed out after " + IN_FLIGHT_TTL.toMinutes() + " minutes (no device response)");

                    // Wake device to process next command if any remain
                    try {
                        sendWakeUp(udid);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in in-flight command reaper: {}", e.getMessage(), e);
        }
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

    public Map.Entry<String, NSDictionary> popCommand(String udid) throws Exception {
        logger.info("Attempting to pop the next command for device with UDID: {}", udid);

        // If this device already has an in-flight command, do NOT issue another one
        InFlightEntry existing = inFlightByDevice.get(udid);
        if (existing != null) {
            logger.info("Device {} already has an in-flight command ({}). Returning no command.", udid, existing.commandUuid());
            return null;
        }

        Deque<QueueItem> queue = commandQueues.get(udid);
        if (queue == null || queue.isEmpty()) {
            logger.warn("No commands found in the queue for device with UDID: {}", udid);
            return null;
        }

        QueueItem head = queue.peekFirst(); // do not remove yet; remove when ACK/ERROR arrives
        if (head == null) {
            logger.warn("Queue head is null for UDID: {}. Returning no command.", udid);
            return null;
        }

        logger.info("Issuing command with UUID: {} for device with UDID: {}", head.uuid(), udid);

        logger.debug("Marking command with UUID: {} as EXECUTING in the database.", head.uuid());
        this.executeCommandAsync(head.uuid());

        // mark device as in-flight with the specific command UUID and start time
        inFlightByDevice.put(udid, new InFlightEntry(head.uuid(), Instant.now()));

        return new AbstractMap.SimpleEntry<>(head.uuid(), head.command());
    }

    // NOTE: pushCommand must NOT be @Async — callers (e.g. enableLostMode, disableLostMode)
    // push multiple commands sequentially and rely on FIFO ordering.
    // Making this async would create independent tasks with non-deterministic execution order.
    public void pushCommand(String udid, String commandUUID, NSDictionary command, String commandType, boolean isSystem, boolean fromPolicy, UUID policyId) throws Exception {

        logger.info("Pushing command to the queue. UDID: {}, Command UUID: {}, Command Type: {}, From Policy: {}, Policy ID: {}",
                udid, commandUUID, commandType, fromPolicy, policyId);

        logger.debug("Saving AppleCommand to the database. UDID: {}, Command UUID: {}", udid, commandUUID);
        this.saveAppleCommandAsync(udid, commandUUID, command, commandType, fromPolicy, policyId, isSystem);

        logger.debug("Adding command to the in-memory queue. UDID: {}, Command UUID: {}", udid, commandUUID);
        Deque<QueueItem> queue = commandQueues.computeIfAbsent(udid, k -> new ConcurrentLinkedDeque<>());
        queue.addLast(new QueueItem(commandUUID, command));

        // Always send wake-up if no command is currently in-flight for this device.
        // This is safe because duplicate wake-ups are harmless (device just checks in again).
        if (!inFlightByDevice.containsKey(udid)) {
            try {
                sendWakeUp(udid);
            } catch (Exception e) {
                logger.warn("Failed to send wake-up push for UDID {}: {}", udid, e.getMessage());
            }
        }

        logger.info("Command successfully pushed to the queue. UDID: {}, Command UUID: {}", udid, commandUUID);
    }

    private void sendWakeUp(String deviceIdentifier) throws Exception {
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
    @Transactional
    public void handleDeviceResponse(NSDictionary response) {

        logger.info("Handling device response: {}", response);

        CommandCommons cc = getCommandCommons(response);
        if (cc == null) {
            logger.warn("No command found for the given response: {}", response);
            return;
        }

        // Command currently marked as in-flight for this device (if any)
        InFlightEntry inflight = inFlightByDevice.get(cc.udid());

        try {
            logger.info("Processing command with UUID: {} and type: {}", cc.commandUUID(), cc.command().getCommandType());

            if (cc.command().getCommandType().equals(CommandTypes.DEVICE_INFO_COMMAND.getRequestType())) {
                logger.debug("Handling DEVICE_INFO_COMMAND for device with UDID: {}", cc.udid());
                NSObject queryResponses = response.objectForKey("QueryResponses");
                if (queryResponses != null) {
                    HashMap<String, Object> deviceInformation = (HashMap<String, Object>) queryResponses.toJavaObject();
                    appleCommandHandlerService.updateDeviceInfo(cc.udid(), deviceInformation);
                    logger.info("Device information updated successfully for device with UDID: {}", cc.udid());
                } else {
                    logger.warn("QueryResponses missing in DEVICE_INFO response for UDID: {}", cc.udid());
                }
            } else if (cc.command().getCommandType().equals(CommandTypes.DEVICE_INSTALL_PROFILE_COMMAND.getRequestType())) {
                logger.debug("Handling DEVICE_INSTALL_PROFILE_COMMAND for device with UDID: {}", cc.udid());
                appleCommandHandlerService.handleInstallProfileCommand(cc.udid(), cc.commandUUID());
                logger.info("Install profile command handled successfully for device with UDID: {}", cc.udid());
            } else if (cc.command().getCommandType().equals(CommandTypes.DEVICE_INSTALLED_APPLICATION_LIST_COMMAND.getRequestType())) {
                logger.debug("Handling InstalledApplicationList for UDID: {}", cc.udid());
                NSObject appList = response.get("InstalledApplicationList");
                if (appList != null) {
                    appleCommandHandlerService.handleInstalledApplicationList(cc.udid(), (Object[]) appList.toJavaObject());
                    logger.info("Installed application list handled successfully for device with UDID: {}", cc.udid());
                } else {
                    logger.warn("InstalledApplicationList missing in response for UDID: {}", cc.udid());
                }
            } else if (cc.command().getCommandType().equals(CommandTypes.DEVICE_MANAGED_APPLICATION_LIST_COMMAND.getRequestType())) {
                logger.debug("Handling ManagedApplicationList for UDID: {}", cc.udid());
                NSObject managedList = response.get("ManagedApplicationList");
                if (managedList != null) {
                    appleCommandHandlerService.handleManagedApplicationList(cc.udid(), (Map<String, Object>) managedList.toJavaObject());
                    logger.info("Managed application list handled successfully for device with UDID: {}", cc.udid());
                } else {
                    logger.warn("ManagedApplicationList missing in response for UDID: {}", cc.udid());
                }
            } else if (cc.command().getCommandType().equals(CommandTypes.DEVICE_INSTALL_APP_COMMAND.getRequestType())
                    || cc.command().getCommandType().equals(CommandTypes.DEVICE_INSTALL_ENTERPRISE_APP_COMMAND.getRequestType())
                    || cc.command().getCommandType().equals(CommandTypes.DEVICE_REMOVE_APP_COMMAND.getRequestType())) {
                logger.debug("Handling app install/remove response for UDID: {}", cc.udid());

                boolean shouldSync = true;
                Policy commandPolicy = cc.command().getPolicy();
                if (commandPolicy != null) {
                    String policyId = commandPolicy.getId().toString();
                    String installPrefix = cc.udid() + "_" + CommandTypes.DEVICE_INSTALL_APP_COMMAND.getRequestType() + "_" + policyId + "_";
                    String enterpriseInstallPrefix = cc.udid() + "_" + CommandTypes.DEVICE_INSTALL_ENTERPRISE_APP_COMMAND.getRequestType() + "_" + policyId + "_";
                    String removePrefix = cc.udid() + "_" + CommandTypes.DEVICE_REMOVE_APP_COMMAND.getRequestType() + "_" + policyId + "_";

                    Deque<QueueItem> dq = commandQueues.get(cc.udid());
                    if (dq != null) {
                        shouldSync = dq.stream()
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
            } else if (cc.command().getCommandType().equals(CommandTypes.DEVICE_SECURITY_INFO_COMMAND.getRequestType())) {
                logger.debug("Handling DEVICE_SECURITY_INFO_COMMAND response for UDID: {}", cc.udid());
                NSObject securityInfo = response.get("SecurityInfo");
                if (securityInfo != null) {
                    appleCommandHandlerService.handleSecurityInfoResponse(cc.udid(), (Map<String, Object>) securityInfo.toJavaObject());
                    logger.info("SecurityInfo updated successfully for device: {}", cc.udid());
                } else {
                    logger.warn("SecurityInfo missing in response for UDID: {}", cc.udid());
                }
            } else if (cc.command().getCommandType().equals(CommandTypes.DEVICE_LOCATION_COMMAND.getRequestType())) {
                logger.debug("Handling DEVICE_LOCATION_COMMAND response for UDID: {}", cc.udid());
                appleCommandHandlerService.handleDeviceLocationResponse(cc.udid(), response);
                logger.info("Device location handled successfully for device: {}", cc.udid());
            } else if (cc.command().getCommandType().equals(CommandTypes.DEVICE_CERTIFICATE_LIST_COMMAND.getRequestType())) {
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
                appleCommandHandlerService.handleCommandSuccess(cc.udid(), cc.commandUUID(), cc.command().getCommandType());
                logger.info("Command {} handled successfully for device with UDID: {}", cc.command().getCommandType(), cc.udid());
            }

            this.completeCommand(cc.commandUUID());
        } catch (Exception e) {
            logger.error("Exception handling response for command {} (type: {}) on device {}: {}",
                    cc.commandUUID(), cc.command().getCommandType(), cc.udid(), e.getMessage(), e);
            try {
                this.failCommand(cc.commandUUID(), "Response handler failed: " + e.getMessage());
            } catch (Exception ex) {
                logger.error("Failed to mark command {} as failed after error: {}", cc.commandUUID(), ex.getMessage());
            }
        } finally {
            Deque<QueueItem> queue = commandQueues.get(cc.udid());
            if (queue != null && !queue.isEmpty()) {
                QueueItem head = queue.peekFirst();
                if (head != null && head.uuid().equals(cc.commandUUID())) {
                    queue.removeFirst();
                    logger.info("Removed completed command {} from queue of UDID {}", cc.commandUUID(), cc.udid());
                }
                if (queue.isEmpty()) {
                    commandQueues.remove(cc.udid(), queue);
                }
            }
            if (inflight != null) {
                inFlightByDevice.remove(cc.udid(), inflight);
            }

            Deque<QueueItem> qAfter = commandQueues.get(cc.udid());
            if (qAfter != null && !qAfter.isEmpty()) {
                try {
                    sendWakeUp(cc.udid());
                    logger.info("Pending commands remain for UDID {}. Wake-up push sent.", cc.udid());
                } catch (Exception e) {
                    logger.warn("Failed to send wake-up after completion for UDID {}: {}", cc.udid(), e.getMessage());
                }
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
    @Transactional
    public void handleDeviceErrorResponse(NSDictionary response) {

        // Find the command from the response
        CommandCommons cc = getCommandCommons(response);

        if (cc == null) {
            logger.error("Failed to process device error response: CommandCommons could not be retrieved from the response: {}", response);
            return;
        }

        // Command currently marked as in-flight for this device (if any)
        InFlightEntry inflight = inFlightByDevice.get(cc.udid());

        try {
            logger.info("Processing error response for command with UUID: {} and device UDID: {}", cc.commandUUID(), cc.udid());

            // Extract error message from response
            String errorMessage = getFailureReasons(response).toString();

            // Handle generic command failure for compliance tracking (called for all command types)
            appleCommandHandlerService.handleCommandFailure(cc.udid(), cc.commandUUID(), cc.command().getCommandType(), errorMessage);

            // Handles policy failures; updates application management status
            if (cc.command().getCommandType().equals(CommandTypes.DEVICE_INSTALL_PROFILE_COMMAND.getRequestType())) {
                logger.debug("Handling error for DEVICE_INSTALL_PROFILE_COMMAND. Command UUID: {}, Device UDID: {}", cc.commandUUID(), cc.udid());
                appleCommandHandlerService.handleFailedInstallProfileCommand(cc.udid(), cc.commandUUID(), errorMessage);
                logger.info("Install profile command error handled successfully for device with UDID: {}", cc.udid());
            }
            // If the command has a policy, handle the failure
            else if (cc.command().getPolicy() != null) {
                AppleDevice device = cc.command().getAppleDevice();
                Map<String, Object> appliedPolicy = device.getAppliedPolicy();

                logger.debug("Command with UUID: {} is associated with a policy. Checking policy type and status.", cc.commandUUID());

                // If the failed policy is not kiosk
                if (!cc.commandUUID().toLowerCase(Locale.ROOT).contains("singleappmode") &&
                        !cc.commandUUID().toLowerCase(Locale.ROOT).contains("homescreen")) {

                    // If the failed policy is installing an app, set policy status in the application management to failed
                    if (cc.command().getCommandType().equals(CommandTypes.DEVICE_INSTALL_APP_COMMAND.getRequestType())
                            || cc.command().getCommandType().equals(CommandTypes.DEVICE_INSTALL_ENTERPRISE_APP_COMMAND.getRequestType())) {
                        logger.info("Handling failure for DEVICE_INSTALL_APP_COMMAND. Command UUID: {}, Device UDID: {}", cc.commandUUID(), cc.udid());

                        Map<String, Object> payload = appliedPolicy != null ? (Map<String, Object>) appliedPolicy.get(PAYLOAD) : null;
                        Map<String, Object> applicationMap = payload != null ? (Map<String, Object>) payload.get(APPLICATIONS_MANAGEMENT) : null;
                        if (applicationMap != null) {
                            applicationMap.put(STATUS, STATUS_FAILED);
                            StringBuilder failureReason = getFailureReasons(response);
                            applicationMap.put("failureReason", failureReason.toString());
                            device.setAppliedPolicy(appliedPolicy);
                            appleDeviceRepository.save(device);
                            logger.info("Updated applied policy for device with UDID: {}. Marking command UUID: {} as failed.", cc.udid(), cc.commandUUID());
                        } else {
                            logger.warn("Cannot update application management status — payload or applicationManagement is null for device UDID: {}", cc.udid());
                        }
                    } else {
                        logger.warn("Unhandled command type for failure processing: {}. Command UUID: {}, Device UDID: {}",
                                cc.command().getCommandType(), cc.commandUUID(), cc.udid());
                    }
                }
            }

            StringBuilder failureReason = getFailureReasons(response);
            logger.error("Failure reason for command UUID: {}: {}", cc.commandUUID(), failureReason);
            this.failCommand(cc.commandUUID(), failureReason.toString());

            policyEventPublisher.publishPolicyApplicationFailedEvent(new PolicyApplicationFailedEvent(
                    cc.command.getAppleDevice().getId(),
                    "Apple",
                    failureReason.toString()
            ));
        } catch (Exception e) {
            logger.error("Exception handling error response for command {} on device {}: {}",
                    cc.commandUUID(), cc.udid(), e.getMessage(), e);
            try {
                this.failCommand(cc.commandUUID(), "Error handler failed: " + e.getMessage());
            } catch (Exception ignored) {}
        } finally {
            // ALWAYS release in-flight slot and clean queue, even if error handling failed
            Deque<QueueItem> queue = commandQueues.get(cc.udid());
            if (queue != null && !queue.isEmpty()) {
                QueueItem head = queue.peekFirst();
                if (head != null && head.uuid().equals(cc.commandUUID())) {
                    queue.removeFirst();
                    logger.info("Removed failed command {} from queue of UDID {}", cc.commandUUID(), cc.udid());
                }
                if (queue.isEmpty()) {
                    commandQueues.remove(cc.udid(), queue);
                }
            }
            if (inflight != null) {
                inFlightByDevice.remove(cc.udid(), inflight);
            }
            // If more commands are queued, wake device to fetch the next
            Deque<QueueItem> qAfter = commandQueues.get(cc.udid());
            if (qAfter != null && !qAfter.isEmpty()) {
                try {
                    sendWakeUp(cc.udid());
                    logger.info("Pending commands remain for UDID {} after failure. Wake-up push sent.", cc.udid());
                } catch (Exception e) {
                    logger.warn("Failed to send wake-up after failure for UDID {}: {}", cc.udid(), e.getMessage());
                }
            }
        }
    }

    @Transactional
    public void handleDeviceNotNowResponse(NSDictionary response) {

        CommandCommons cc = getCommandCommons(response);
        if (cc == null) {
            logger.warn("No command found for the given NotNow response: {}", response);
            return;
        }

        InFlightEntry inflight = inFlightByDevice.get(cc.udid());

        logger.info("Handling NotNow response for command UUID: {} (type: {}) on device UDID: {}",
                cc.commandUUID(), cc.command().getCommandType(), cc.udid());

        try {
            this.failCommand(cc.commandUUID(), "NotNow");
            logger.info("Command {} marked as FAILED (NotNow) for device UDID: {}", cc.commandUUID(), cc.udid());
        } catch (Exception e) {
            logger.error("Failed to mark command {} as FAILED after NotNow: {}", cc.commandUUID(), e.getMessage(), e);
        } finally {
            // Remove command from queue and clear in-flight slot
            Deque<QueueItem> queue = commandQueues.get(cc.udid());
            if (queue != null && !queue.isEmpty()) {
                QueueItem head = queue.peekFirst();
                if (head != null && head.uuid().equals(cc.commandUUID())) {
                    queue.removeFirst();
                    logger.info("Removed NotNow command {} from queue of UDID {}", cc.commandUUID(), cc.udid());
                }
                if (queue.isEmpty()) {
                    commandQueues.remove(cc.udid(), queue);
                }
            }
            if (inflight != null) {
                inFlightByDevice.remove(cc.udid(), inflight);
            }

            // Wake device for next command if any remain
            Deque<QueueItem> qAfter = commandQueues.get(cc.udid());
            if (qAfter != null && !qAfter.isEmpty()) {
                try {
                    sendWakeUp(cc.udid());
                    logger.info("Pending commands remain for UDID {} after NotNow. Wake-up push sent.", cc.udid());
                } catch (Exception e) {
                    logger.warn("Failed to send wake-up after NotNow for UDID {}: {}", cc.udid(), e.getMessage());
                }
            }
        }
    }

    public void cancelCommand(String udid, String commandUUID) {
        logger.info("Attempting to cancel command with UUID: {} for device with UDID: {}", commandUUID, udid);

        Deque<QueueItem> queue = commandQueues.get(udid);
        if (queue == null || queue.isEmpty()) {
            logger.warn("No commands found in the queue for device with UDID: {}. Unable to cancel command with UUID: {}", udid, commandUUID);
            return;
        }

        QueueItem head = queue.peekFirst();
        boolean removed = false;
        if (head != null && head.uuid().equals(commandUUID)) {
            // canceling the in-flight (or next up) item
            queue.removeFirst();
            removed = true;
            // Free in-flight only if we are canceling the currently in-flight command
            inFlightByDevice.computeIfPresent(udid, (k, v) -> v.commandUuid().equals(commandUUID) ? null : v);
        } else {
            // remove any matching pending item
            removed = queue.removeIf(item -> item.uuid().equals(commandUUID));
        }

        if (removed) {
            logger.debug("Marking command with UUID: {} as canceled in the database.", commandUUID);
            this.cancelCommandAsync(commandUUID);
            if (queue.isEmpty()) {
                commandQueues.remove(udid, queue);
            }
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

        // Find the device from the repository (try UDID first, then EnrollmentID for BYOD)
        logger.debug("Fetching device with identifier '{}' from the repository.", udid);
        var deviceOpt = appleDeviceRepository.findByUdid(udid);
        if (deviceOpt.isEmpty()) {
            deviceOpt = appleDeviceRepository.findByEnrollmentId(udid);
        }

        // If the device does not exist, log and return
        if (deviceOpt.isEmpty()) {
            logger.warn("Device with identifier '{}' not found in the repository. Unable to save AppleCommand.", udid);
            CompletableFuture.completedFuture(null);
            return;
        }

        // Get the device from the repository
        AppleDevice device = deviceOpt.get();
        logger.info("Device with identifier '{}' found. Proceeding to save AppleCommand.", udid);

        // Save the AppleCommand object to the database
        logger.debug("Building AppleCommand object for Command UUID: {}.", commandUUID);
        AppleCommand command = AppleCommand.builder()
                .appleDevice(device)
                .commandUUID(commandUUID)
                .commandType(commandType)
                .template(ddPlistToXml(commandTemplate))
                .status(CommandStatus.PENDING.name())
                .requestTime(Instant.now())
                .build();

        // If the command is from a policy, update the policy ID
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

        // If the command is a system command, set the system flag
        if (isSystem) {
            logger.debug("Command is a system command. Setting system flag for Command UUID: {}.", commandUUID);
            command.setCreatedBy("system");
            command.setLastModifiedBy("system");
        }

        // Save the AppleCommand object to the database
        logger.debug("Saving AppleCommand object to the database for Command UUID: {}.", commandUUID);
        appleCommandRepository.save(command);
        logger.info("AppleCommand with Command UUID: {} successfully saved to the database.", commandUUID);
    }

    @Async
    public void executeCommandAsync(String commandUUID) {

        logger.info("Starting execution of command with UUID: {}", commandUUID);

        // Update the status in the database using JdbcTemplate
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

        // Update the status in the database using JdbcTemplate
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

        // Update the status of the command to failed using JdbcTemplate
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

        CompletableFuture.completedFuture(null);
    }

    @Async
    public void cancelCommandAsync(String commandUUID) {

        logger.info("Starting the process to cancel command with UUID: {}", commandUUID);

        // Update the status of the command to canceled using JdbcTemplate
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

        CompletableFuture.completedFuture(null);
    }

    public enum CommandStatus {
        PENDING,
        EXECUTING,
        COMPLETED,
        FAILED,
        CANCELED
    }

    // Queue item wrapper to keep UUID + command together
    private record QueueItem(String uuid, NSDictionary command) {
    }

    // Tracks in-flight command UUID and when it started executing
    private record InFlightEntry(String commandUuid, Instant startedAt) {
    }

    private record CommandCommons(String commandUUID, AppleCommand command, String udid) {
    }
}

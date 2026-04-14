package com.arcyintel.arcops.apple_mdm.event.listener;

import com.arcyintel.arcops.apple_mdm.repositories.AppleDeviceRepository;
import com.arcyintel.arcops.apple_mdm.services.apple.command.AppleCommandSenderService;
import com.arcyintel.arcops.commons.events.geofence.GeofenceActionEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static com.arcyintel.arcops.commons.constants.events.GeofenceEvents.*;

@Component
@RequiredArgsConstructor
public class GeofenceActionListener {

    private static final Logger logger = LoggerFactory.getLogger(GeofenceActionListener.class);

    private final AppleCommandSenderService appleCommandSenderService;
    private final AppleDeviceRepository appleDeviceRepository;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = GEOFENCE_ACTION_EXECUTE_QUEUE_APPLE, durable = "true"),
            exchange = @Exchange(value = GEOFENCE_EVENT_EXCHANGE, type = "topic"),
            key = GEOFENCE_ACTION_EXECUTE_ROUTE_KEY_APPLE
    ))
    @Transactional
    public void handleActionExecute(GeofenceActionEvent event) {
        try {
            String commandType = event.getCommandType();
            String udid = event.getDeviceIdentifier();

            if (commandType == null || commandType.isBlank()) {
                logger.error("Geofence action event has no command type, skipping");
                return;
            }
            if (udid == null || udid.isBlank()) {
                logger.error("Geofence action event has no device identifier, skipping");
                return;
            }

            logger.info("Geofence action received — type: {}, device: {}", commandType, udid);

            var deviceOpt = appleDeviceRepository.findByUdid(udid);
            if (deviceOpt.isEmpty()) {
                logger.error("Device not found for geofence action — udid: {}", udid);
                return;
            }

            Map<String, Object> config = event.getCommandData() != null ? event.getCommandData() : Map.of();

            switch (commandType) {
                case "LOCK_DEVICE" -> {
                    String message = config.containsKey("message") ? config.get("message").toString() : null;
                    String phone = config.containsKey("phoneNumber") ? config.get("phoneNumber").toString() : null;
                    appleCommandSenderService.lockDevice(udid, message, phone);
                    logger.info("LOCK_DEVICE command sent to device: {}", udid);
                }
                case "ENABLE_LOST_MODE" -> {
                    String message = config.containsKey("message") ? config.get("message").toString() : "Device is in lost mode";
                    String phone = config.containsKey("phoneNumber") ? config.get("phoneNumber").toString() : null;
                    String footnote = config.containsKey("footnote") ? config.get("footnote").toString() : null;
                    appleCommandSenderService.enableLostMode(udid, message, phone, footnote);
                    logger.info("ENABLE_LOST_MODE command sent to device: {}", udid);
                }
                case "WIPE_DEVICE" -> {
                    String pin = config.containsKey("pin") ? config.get("pin").toString() : null;
                    boolean preserveDataPlan = config.containsKey("preserveDataPlan")
                            && Boolean.parseBoolean(config.get("preserveDataPlan").toString());
                    appleCommandSenderService.eraseDevice(udid, pin, preserveDataPlan);
                    logger.info("WIPE_DEVICE command sent to device: {}", udid);
                }
                case "DISABLE_LOST_MODE" -> {
                    appleCommandSenderService.disableLostMode(udid);
                    logger.info("DISABLE_LOST_MODE command sent to device: {}", udid);
                }
                case "CLEAR_PASSCODE" -> {
                    appleCommandSenderService.clearPasscode(udid);
                    logger.info("CLEAR_PASSCODE command sent to device: {}", udid);
                }
                default -> logger.warn("Unknown geofence action command type: {}", commandType);
            }
        } catch (Exception e) {
            logger.error("Failed to execute geofence action {} on device {}: {}",
                    event.getCommandType(), event.getDeviceIdentifier(), e.getMessage(), e);
        }
    }
}

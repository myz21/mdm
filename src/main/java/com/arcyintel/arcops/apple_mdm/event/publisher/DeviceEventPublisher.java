package com.arcyintel.arcops.apple_mdm.event.publisher;


import com.arcyintel.arcops.commons.events.device.DeviceDisenrolledEvent;
import com.arcyintel.arcops.commons.events.device.DeviceEnrolledEvent;
import com.arcyintel.arcops.commons.events.device.DeviceInformationChangedEvent;
import com.arcyintel.arcops.commons.events.device.DevicePresenceChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.arcyintel.arcops.commons.constants.events.DeviceEvents.*;

@Service
@RequiredArgsConstructor
public class DeviceEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Async
    public void publishDeviceEnrolledEvent(DeviceEnrolledEvent event) {
        rabbitTemplate.convertAndSend(DEVICE_EVENT_EXCHANGE, DEVICE_ENROLLED_ROUTE_KEY_APPLE, event);
    }

    @Async
    public void publishDeviceDisenrolledEvent(DeviceDisenrolledEvent event) {
        rabbitTemplate.convertAndSend(DEVICE_EVENT_EXCHANGE, DEVICE_DISENROLLED_ROUTE_KEY_APPLE, event);
    }

    @Async
    public void publishDeviceInformationChangedEvent(DeviceInformationChangedEvent event) {
        rabbitTemplate.convertAndSend(DEVICE_EVENT_EXCHANGE, DEVICE_INFORMATION_CHANGED_ROUTE_KEY_APPLE, event);
    }

    @Async
    public void publishDevicePresenceChangedEvent(DevicePresenceChangedEvent event) {
        rabbitTemplate.convertAndSend(DEVICE_EVENT_EXCHANGE, DEVICE_PRESENCE_CHANGED_ROUTE_KEY_APPLE, event);
    }
}

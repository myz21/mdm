package com.arcyintel.arcops.apple_mdm.configs.mqtt;

import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
@RequiredArgsConstructor
public class MqttConfig {

    private static final Logger logger = LoggerFactory.getLogger(MqttConfig.class);

    private final MqttProperties mqttProperties;

    /**
     * MQTT shared subscription group name for this service.
     * Shared subscriptions ($share/{group}/{topic}) ensure each message is delivered
     * to only one instance in the group — enabling horizontal scaling.
     */
    private static final String SHARED_GROUP = "$share/apple-mdm/";

    /**
     * Build subscribe topics with platform segment and shared subscription prefix.
     * Topic structure: $share/apple-mdm/arcops/{platform}/devices/+/{subtopic}
     */
    private String[] buildSubscribeTopics() {
        String prefix = SHARED_GROUP + "arcops/" + mqttProperties.getPlatform() + "/devices/+/";
        return new String[]{
                prefix + "status",
                prefix + "telemetry",
                prefix + "location",
                prefix + "events",
                prefix + "responses"
        };
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttProperties.getBrokerUrl()});
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);

        if (mqttProperties.getUsername() != null && !mqttProperties.getUsername().isBlank()) {
            options.setUserName(mqttProperties.getUsername());
            options.setPassword(mqttProperties.getPassword().toCharArray());
        }

        factory.setConnectionOptions(options);
        logger.info("MQTT client factory configured for broker: {}", mqttProperties.getBrokerUrl());
        return factory;
    }

    // --- Inbound: Subscribe to device topics ---

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(
            MqttPahoClientFactory mqttClientFactory) {

        String clientId = mqttProperties.getUniqueClientId() + "-inbound";
        String[] topics = buildSubscribeTopics();
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory, topics);

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(mqttProperties.getDefaultQos());
        adapter.setOutputChannel(mqttInputChannel());
        adapter.setAutoStartup(true);

        logger.info("MQTT inbound adapter configured — subscribing to {} topics: {}", topics.length, String.join(", ", topics));
        return adapter;
    }

    // --- Outbound: Publish commands to devices ---

    @Bean
    public MessageChannel mqttOutputChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutputChannel")
    public MessageHandler mqttOutboundHandler(MqttPahoClientFactory mqttClientFactory) {
        String clientId = mqttProperties.getUniqueClientId() + "-outbound";
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(clientId, mqttClientFactory);

        handler.setAsync(true);
        handler.setDefaultQos(mqttProperties.getDefaultQos());
        handler.setDefaultTopic("arcops/server/default");

        logger.info("MQTT outbound handler configured");
        return handler;
    }
}

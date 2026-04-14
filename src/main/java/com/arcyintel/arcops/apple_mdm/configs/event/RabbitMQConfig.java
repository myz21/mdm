package com.arcyintel.arcops.apple_mdm.configs.event;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.arcyintel.arcops.commons.constants.events.AccountEvents.*;
import static com.arcyintel.arcops.commons.constants.events.DeviceEvents.*;
import static com.arcyintel.arcops.commons.constants.events.IdentityEvents.*;
import static com.arcyintel.arcops.commons.constants.events.GeofenceEvents.*;
import static com.arcyintel.arcops.commons.constants.events.MailGatewayEvents.MAIL_GATEWAY_EXCHANGE;
import static com.arcyintel.arcops.commons.constants.events.PolicyEvents.*;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    // Exchanges (both needed: publishes device events, consumes policy events)
    @Bean
    public TopicExchange deviceExchange() {
        return new TopicExchange(DEVICE_EVENT_EXCHANGE);
    }

    @Bean
    public TopicExchange policyExchange() {
        return new TopicExchange(POLICY_EVENT_EXCHANGE);
    }

    @Bean
    public TopicExchange accountExchange() {
        return new TopicExchange(ACCOUNT_EVENT_EXCHANGE);
    }

    // Queues this service CONSUMES
    @Bean
    public Queue policyApplyQueue() {
        return QueueBuilder.durable(POLICY_APPLY_QUEUE_APPLE)
                .withArgument("x-dead-letter-exchange", POLICY_DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", POLICY_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue policyCreateQueue() {
        return new Queue(POLICY_CREATE_QUEUE_APPLE, true);
    }

    @Bean
    public Queue policyDeleteQueue() {
        return new Queue(POLICY_DELETE_QUEUE_APPLE, true);
    }

    @Bean
    public Queue policyUpdateQueue() {
        return new Queue(POLICY_UPDATE_QUEUE_APPLE, true);
    }

    // Identity exchange (consumed from back_core)
    @Bean
    public TopicExchange identityExchange() {
        return new TopicExchange(IDENTITY_EVENT_EXCHANGE);
    }

    // Identity queues
    @Bean
    public Queue identitySyncQueue() {
        return new Queue(IDENTITY_SYNC_QUEUE_APPLE, true);
    }

    @Bean
    public Queue identityDeletedQueue() {
        return new Queue(IDENTITY_DELETED_QUEUE_APPLE, true);
    }

    // Identity bindings
    @Bean
    public Binding identitySyncBinding() {
        return BindingBuilder.bind(identitySyncQueue()).to(identityExchange()).with(IDENTITY_SYNC_ROUTE_KEY_APPLE);
    }

    @Bean
    public Binding identityDeletedBinding() {
        return BindingBuilder.bind(identityDeletedQueue()).to(identityExchange()).with(IDENTITY_DELETED_ROUTE_KEY_APPLE);
    }

    // Account core-sync queue (back_core → apple_mdm)
    @Bean
    public Queue accountCoreSyncQueue() {
        return QueueBuilder.durable(ACCOUNT_CORE_SYNC_QUEUE_APPLE)
                .withArgument("x-dead-letter-exchange", ACCOUNT_DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ACCOUNT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding accountCoreSyncBinding() {
        return BindingBuilder.bind(accountCoreSyncQueue()).to(accountExchange()).with(ACCOUNT_CORE_SYNC_ROUTE_KEY_APPLE);
    }

    // Geofence exchange + queues
    @Bean
    public TopicExchange geofenceExchange() {
        return new TopicExchange(GEOFENCE_EVENT_EXCHANGE);
    }

    @Bean
    public Queue geofenceConfigApplyQueue() {
        return new Queue(GEOFENCE_CONFIG_APPLY_QUEUE_APPLE, true);
    }

    @Bean
    public Binding geofenceConfigApplyBinding() {
        return BindingBuilder.bind(geofenceConfigApplyQueue()).to(geofenceExchange()).with(GEOFENCE_CONFIG_APPLY_ROUTE_KEY_APPLE);
    }

    @Bean
    public Queue geofenceActionExecuteQueue() {
        return QueueBuilder.durable(GEOFENCE_ACTION_EXECUTE_QUEUE_APPLE)
                .withArgument("x-dead-letter-exchange", GEOFENCE_ACTION_DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", GEOFENCE_ACTION_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding geofenceActionExecuteBinding() {
        return BindingBuilder.bind(geofenceActionExecuteQueue()).to(geofenceExchange()).with(GEOFENCE_ACTION_EXECUTE_ROUTE_KEY_APPLE);
    }

    // --- Dead Letter Queues (critical flows only) ---

    // Policy DLQ
    @Bean
    public DirectExchange policyDlqExchange() {
        return new DirectExchange(POLICY_DLQ_EXCHANGE);
    }

    @Bean
    public Queue policyDlqQueue() {
        return QueueBuilder.durable(POLICY_DLQ_QUEUE).build();
    }

    @Bean
    public Binding policyDlqBinding() {
        return BindingBuilder.bind(policyDlqQueue()).to(policyDlqExchange()).with(POLICY_DLQ_ROUTING_KEY);
    }

    // Geofence Action DLQ
    @Bean
    public DirectExchange geofenceActionDlqExchange() {
        return new DirectExchange(GEOFENCE_ACTION_DLQ_EXCHANGE);
    }

    @Bean
    public Queue geofenceActionDlqQueue() {
        return QueueBuilder.durable(GEOFENCE_ACTION_DLQ_QUEUE).build();
    }

    @Bean
    public Binding geofenceActionDlqBinding() {
        return BindingBuilder.bind(geofenceActionDlqQueue()).to(geofenceActionDlqExchange()).with(GEOFENCE_ACTION_DLQ_ROUTING_KEY);
    }

    // Device Event DLQ
    @Bean
    public DirectExchange deviceEventDlqExchange() {
        return new DirectExchange(DEVICE_DLQ_EXCHANGE);
    }

    @Bean
    public Queue deviceEventDlqQueue() {
        return QueueBuilder.durable(DEVICE_DLQ_QUEUE).build();
    }

    @Bean
    public Binding deviceEventDlqBinding() {
        return BindingBuilder.bind(deviceEventDlqQueue()).to(deviceEventDlqExchange()).with(DEVICE_DLQ_ROUTING_KEY);
    }

    // Account Event DLQ
    @Bean
    public DirectExchange accountEventDlqExchange() {
        return new DirectExchange(ACCOUNT_DLQ_EXCHANGE);
    }

    @Bean
    public Queue accountEventDlqQueue() {
        return QueueBuilder.durable(ACCOUNT_DLQ_QUEUE).build();
    }

    @Bean
    public Binding accountEventDlqBinding() {
        return BindingBuilder.bind(accountEventDlqQueue()).to(accountEventDlqExchange()).with(ACCOUNT_DLQ_ROUTING_KEY);
    }

    // Mail gateway exchange (publisher only — back_core consumes)
    @Bean
    public TopicExchange mailGatewayExchange() {
        return new TopicExchange(MAIL_GATEWAY_EXCHANGE);
    }

    // Bindings for consumed queues
    @Bean
    public Binding policyApplyBinding() {
        return BindingBuilder.bind(policyApplyQueue()).to(policyExchange()).with(POLICY_APPLY_ROUTE_KEY_APPLE);
    }

    @Bean
    public Binding policyCreateBinding() {
        return BindingBuilder.bind(policyCreateQueue()).to(policyExchange()).with(POLICY_CREATE_ROUTE_KEY);
    }

    @Bean
    public Binding policyDeleteBinding() {
        return BindingBuilder.bind(policyDeleteQueue()).to(policyExchange()).with(POLICY_DELETE_ROUTE_KEY);
    }

    @Bean
    public Binding policyUpdateBinding() {
        return BindingBuilder.bind(policyUpdateQueue()).to(policyExchange()).with(POLICY_UPDATE_ROUTE_KEY);
    }
}
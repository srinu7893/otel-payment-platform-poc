package com.srinu.payments.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EVENT_EXCHANGE = "payments.events";
    public static final String NOTIFICATION_QUEUE = "payments.notification";
    public static final String DLX = "payments.notification.dlx";
    public static final String DLQ = "payments.notification.dlq";

    @Bean
    TopicExchange paymentExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
            .deadLetterExchange(DLX)
            .deadLetterRoutingKey("notification.failed")
            .build();
    }

    @Bean
    Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding paymentCompletedBinding(Queue notificationQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(notificationQueue).to(paymentExchange).with("payment.completed");
    }

    @Bean
    Binding paymentRefundedBinding(Queue notificationQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(notificationQueue).to(paymentExchange).with("payment.refunded");
    }

    @Bean
    Binding transferCompletedBinding(Queue notificationQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(notificationQueue).to(paymentExchange).with("transfer.completed");
    }

    @Bean
    Binding deadLetterBinding(Queue notificationDeadLetterQueue, DirectExchange notificationDeadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
            .to(notificationDeadLetterExchange)
            .with("notification.failed");
    }
}

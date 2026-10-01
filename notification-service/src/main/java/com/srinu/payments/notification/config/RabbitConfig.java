package com.srinu.payments.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean TopicExchange paymentExchange(){ return new TopicExchange("payments.events", true, false); }
    @Bean Queue notificationQueue(){ return QueueBuilder.durable("payments.notification").build(); }
    @Bean Binding notificationBinding(Queue notificationQueue, TopicExchange paymentExchange){
        return BindingBuilder.bind(notificationQueue).to(paymentExchange).with("payment.completed");
    }
}

package com.srinu.payments.payment.config;
import org.springframework.amqp.core.*; import org.springframework.context.annotation.*;
@Configuration public class RabbitConfig { @Bean TopicExchange paymentExchange(){return new TopicExchange("payments.events",true,false);} }
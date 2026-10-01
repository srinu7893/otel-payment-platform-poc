package com.srinu.payments.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void completed(UUID paymentId, BigDecimal amount) {
        var event = new PaymentCompletedEvent(paymentId, amount, "PAYMENT_COMPLETED");
        try {
            rabbitTemplate.convertAndSend(
                    "payments.events",
                    "payment.completed",
                    objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize payment event", ex);
        }
    }

    public record PaymentCompletedEvent(UUID paymentId, BigDecimal amount, String eventType) {}
}

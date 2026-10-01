package com.srinu.payments.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void completed(UUID paymentId, BigDecimal amount) {
        publish("payment.completed", new PaymentCompletedEvent(paymentId, amount, "PAYMENT_COMPLETED"));
    }

    public void transferCompleted(UUID transferId, UUID bankTransactionId, String customerId,
                                  String senderAccount, String receiverAccount,
                                  BigDecimal amount, String currency) {
        publish("transfer.completed", new TransferCompletedEvent(
            transferId, bankTransactionId, customerId, mask(senderAccount), mask(receiverAccount),
            amount, currency, "TRANSFER_COMPLETED"));
    }

    private void publish(String routingKey, Object event) {
        try {
            String body = objectMapper.writeValueAsString(event);
            String correlationId = MDC.get("correlationId");
            if (correlationId == null) correlationId = UUID.randomUUID().toString();
            String finalCorrelationId = correlationId;
            rabbitTemplate.convertAndSend("payments.events", routingKey, body, message -> {
                message.getMessageProperties().setHeader(CORRELATION_HEADER, finalCorrelationId);
                message.getMessageProperties().setHeader("eventType", event.getClass().getSimpleName());
                return message;
            });
            log.info("event=DOMAIN_EVENT_PUBLISHED routingKey={} payloadType={} correlationId={}",
                routingKey, event.getClass().getSimpleName(), correlationId);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize domain event", ex);
        }
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }

    public record PaymentCompletedEvent(UUID paymentId, BigDecimal amount, String eventType) {}
    public record TransferCompletedEvent(UUID transferId, UUID bankTransactionId, String customerId,
                                         String senderAccount, String receiverAccount, BigDecimal amount,
                                         String currency, String eventType) {}
}

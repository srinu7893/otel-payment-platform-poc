package com.srinu.payments.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srinu.payments.payment.outbox.OutboxEvent;
import com.srinu.payments.payment.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void completed(UUID paymentId, String customerId, BigDecimal amount) {
        stage("PAYMENT", paymentId, "PAYMENT_COMPLETED", "payment.completed",
            new PaymentCompletedEvent(paymentId, customerId, amount, "PAYMENT_COMPLETED"));
    }

    public void transferCompleted(UUID transferId, UUID bankTransactionId, String customerId,
                                  String senderAccount, String receiverAccount,
                                  BigDecimal amount, String currency) {
        stage("TRANSFER", transferId, "TRANSFER_COMPLETED", "transfer.completed",
            new TransferCompletedEvent(transferId, bankTransactionId, customerId,
                mask(senderAccount), mask(receiverAccount), amount, currency, "TRANSFER_COMPLETED"));
    }

    private void stage(String aggregateType, UUID aggregateId, String eventType,
                       String routingKey, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String correlationId = MDC.get("correlationId");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            OutboxEvent staged = outbox.save(new OutboxEvent(
                aggregateType, aggregateId, eventType, routingKey, payload, correlationId));

            log.info("event=DOMAIN_EVENT_STAGED outboxEventId={} aggregateType={} aggregateId={} routingKey={} correlationId={}",
                staged.getId(), aggregateType, aggregateId, routingKey, correlationId);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize domain event", ex);
        }
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }

    public record PaymentCompletedEvent(UUID paymentId, String customerId, BigDecimal amount, String eventType) {}
    public record TransferCompletedEvent(UUID transferId, UUID bankTransactionId, String customerId,
                                         String senderAccount, String receiverAccount, BigDecimal amount,
                                         String currency, String eventType) {}
}

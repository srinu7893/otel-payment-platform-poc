package com.srinu.payments.notification.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srinu.payments.notification.domain.NotificationRecord;
import com.srinu.payments.notification.repository.NotificationRepository;
import com.srinu.payments.notification.service.NotificationDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class PaymentEventListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private final NotificationRepository repository;
    private final NotificationDeliveryService delivery;
    private final ObjectMapper objectMapper;

    public PaymentEventListener(NotificationRepository repository,
                                NotificationDeliveryService delivery,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.delivery = delivery;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "payments.notification")
    public void onDomainEvent(Message message) throws Exception {
        String correlationId = message.getMessageProperties().getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode event = objectMapper.readTree(payload);
            String eventType = text(event, "eventType");
            log.info("event=NOTIFICATION_EVENT_RECEIVED eventType={} correlationId={}", eventType, correlationId);

            if ("PAYMENT_COMPLETED".equals(eventType)) {
                UUID paymentId = UUID.fromString(text(event, "paymentId"));
                String customerId = text(event, "customerId");
                String destination = customerId == null ? "customer@example.test" : customerId + "@example.test";
                var record = repository.save(new NotificationRecord(paymentId, null, customerId,
                    eventType, "EMAIL_SIMULATED", destination));
                delivery.deliver(record, "Payment completed",
                    "Payment " + shortId(paymentId) + " completed successfully");
                return;
            }

            if ("TRANSFER_COMPLETED".equals(eventType)) {
                UUID transferId = UUID.fromString(text(event, "transferId"));
                String customerId = text(event, "customerId");
                String destination = customerId == null ? "customer@example.test" : customerId + "@example.test";
                var email = repository.save(new NotificationRecord(null, transferId, customerId,
                    eventType, "EMAIL_SIMULATED", destination));
                delivery.deliver(email, "Transfer completed",
                    "Transfer " + shortId(transferId) + " completed successfully");

                var sms = repository.save(new NotificationRecord(null, transferId, customerId,
                    eventType, "SMS_SIMULATED", "+910000000000"));
                delivery.deliver(sms, "Transfer completed",
                    "Transfer " + shortId(transferId) + " completed");
                return;
            }

            log.warn("event=NOTIFICATION_EVENT_IGNORED eventType={}", eventType);
        } finally {
            MDC.clear();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}

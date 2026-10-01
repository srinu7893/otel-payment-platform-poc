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
import org.springframework.dao.DataIntegrityViolationException;
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

    @org.springframework.amqp.rabbit.annotation.RabbitListener(queues = "payments.notification")
    public void onDomainEvent(Message message) throws Exception {
        String correlationId = message.getMessageProperties().getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            UUID sourceEventId = eventId(message);
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode event = objectMapper.readTree(payload);
            String eventType = text(event, "eventType");
            log.info("event=NOTIFICATION_EVENT_RECEIVED eventType={} sourceEventId={} correlationId={}",
                eventType, sourceEventId, correlationId);

            if ("PAYMENT_COMPLETED".equals(eventType)) {
                UUID paymentId = UUID.fromString(text(event, "paymentId"));
                String customerId = text(event, "customerId");
                String destination = customerId == null ? "customer@example.test" : customerId + "@example.test";
                deliverOnce(sourceEventId, paymentId, null, customerId, eventType,
                    "EMAIL_SIMULATED", destination,
                    "Payment completed", "Payment " + shortId(paymentId) + " completed successfully");
                return;
            }

            if ("TRANSFER_COMPLETED".equals(eventType)) {
                UUID transferId = UUID.fromString(text(event, "transferId"));
                String customerId = text(event, "customerId");
                String destination = customerId == null ? "customer@example.test" : customerId + "@example.test";

                deliverOnce(sourceEventId, null, transferId, customerId, eventType,
                    "EMAIL_SIMULATED", destination,
                    "Transfer completed", "Transfer " + shortId(transferId) + " completed successfully");

                deliverOnce(sourceEventId, null, transferId, customerId, eventType,
                    "SMS_SIMULATED", "+910000000000",
                    "Transfer completed", "Transfer " + shortId(transferId) + " completed");
                return;
            }

            log.warn("event=NOTIFICATION_EVENT_IGNORED eventType={} sourceEventId={}", eventType, sourceEventId);
        } finally {
            MDC.clear();
        }
    }

    private void deliverOnce(UUID sourceEventId, UUID paymentId, UUID transferId, String customerId,
                             String eventType, String channel, String destination,
                             String subject, String body) {
        NotificationRecord record = null;
        if (sourceEventId != null) {
            record = repository.findBySourceEventIdAndChannel(sourceEventId, channel).orElse(null);
            if (record != null && "SENT".equals(record.getStatus())) {
                log.info("event=NOTIFICATION_DUPLICATE_SKIPPED sourceEventId={} notificationId={} channel={}",
                    sourceEventId, record.getId(), channel);
                return;
            }
        }

        if (record == null) {
            try {
                record = repository.save(new NotificationRecord(sourceEventId, paymentId, transferId, customerId,
                    eventType, channel, destination));
            } catch (DataIntegrityViolationException race) {
                if (sourceEventId == null) throw race;
                record = repository.findBySourceEventIdAndChannel(sourceEventId, channel).orElseThrow(() -> race);
                if ("SENT".equals(record.getStatus())) {
                    log.info("event=NOTIFICATION_DUPLICATE_SKIPPED sourceEventId={} notificationId={} channel={}",
                        sourceEventId, record.getId(), channel);
                    return;
                }
            }
        } else {
            log.info("event=NOTIFICATION_RETRY_REUSED sourceEventId={} notificationId={} channel={} status={} attempts={}",
                sourceEventId, record.getId(), channel, record.getStatus(), record.getAttempts());
        }

        delivery.deliver(record, subject, body);
    }

    private UUID eventId(Message message) {
        Object raw = message.getMessageProperties().getHeaders().get("eventId");
        if (raw == null) {
            log.warn("event=NOTIFICATION_EVENT_WITHOUT_ID action=DEDUPLICATION_DISABLED");
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            log.warn("event=NOTIFICATION_EVENT_INVALID_ID eventId={} action=DEDUPLICATION_DISABLED", raw);
            return null;
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

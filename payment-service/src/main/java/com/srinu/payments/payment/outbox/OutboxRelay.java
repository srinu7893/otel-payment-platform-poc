package com.srinu.payments.payment.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String EXCHANGE = "payments.events";

    private final OutboxEventRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final int batchSize;
    private final long confirmTimeoutMs;

    public OutboxRelay(OutboxEventRepository outbox,
                       RabbitTemplate rabbitTemplate,
                       @Value("${outbox.relay.batch-size:20}") int batchSize,
                       @Value("${outbox.relay.confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
        this.confirmTimeoutMs = Math.max(1000, confirmTimeoutMs);
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        var pending = outbox.lockPending(Instant.now(), PageRequest.of(0, batchSize));
        for (OutboxEvent event : pending) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        Span span = GlobalOpenTelemetry.getTracer("payment-poc.outbox")
            .spanBuilder("outbox.publish")
            .setParent(event.traceContext())
            .setAttribute("messaging.system", "rabbitmq")
            .setAttribute("poc.event.type", event.getEventType())
            .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            publishWithContext(event, span);
        } finally {
            span.end();
        }
    }

    private void recordAttempt(String outcome) {
        GlobalOpenTelemetry.getMeter("payment-poc.outbox")
            .counterBuilder("poc.outbox.publish.attempts")
            .setDescription("Broker-confirmed publish attempts; not committed unique business events")
            .build().add(1, Attributes.of(AttributeKey.stringKey("outcome"), outcome));
    }

    private void publishWithContext(OutboxEvent event, Span span) {
        CorrelationData correlationData = new CorrelationData(event.getId().toString());
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, event.getRoutingKey(), event.getPayload(), message -> {
                message.getMessageProperties().setHeader("X-Correlation-Id", event.getCorrelationId());
                message.getMessageProperties().setHeader("eventId", event.getId().toString());
                message.getMessageProperties().setHeader("eventType", event.getEventType());
                message.getMessageProperties().setContentType("application/json");
                return message;
            }, correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ negatively acknowledged event: " + confirm.getReason());
            }
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned unroutable event for " + event.getRoutingKey());
            }

            event.markPublished();
            recordAttempt("success");
            log.info("event=OUTBOX_PUBLISHED outboxEventId={} aggregateType={} aggregateId={} routingKey={} attempts={} correlationId={}",
                event.getId(), event.getAggregateType(), event.getAggregateId(), event.getRoutingKey(),
                event.getAttempts(), event.getCorrelationId());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            event.markFailed(ex);
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR);
            recordAttempt("failure");
            log.warn("event=OUTBOX_PUBLISH_FAILED outboxEventId={} routingKey={} attempts={} reason=INTERRUPTED",
                event.getId(), event.getRoutingKey(), event.getAttempts());
        } catch (Exception ex) {
            event.markFailed(ex);
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR);
            recordAttempt("failure");
            log.warn("event=OUTBOX_PUBLISH_FAILED outboxEventId={} routingKey={} attempts={} errorType={} message={}",
                event.getId(), event.getRoutingKey(), event.getAttempts(), ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}

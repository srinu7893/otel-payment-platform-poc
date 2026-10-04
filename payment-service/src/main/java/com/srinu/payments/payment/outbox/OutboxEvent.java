package com.srinu.payments.payment.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    private UUID id;

    @Column(nullable = false, length = 40)
    private String aggregateType;

    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 100)
    private String correlationId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(length = 55)
    private String traceparent;

    @Column(length = 512)
    private String tracestate;

    @Version
    private Long version;

    protected OutboxEvent() {}

    public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String routingKey,
                       String payload, String correlationId) {
        var trace = OutboxTraceContext.capture();
        this.traceparent = trace.get("traceparent");
        this.tracestate = trace.get("tracestate");
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.payload = payload;
        this.correlationId = correlationId;
        this.status = "PENDING";
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }

    public void markPublished() {
        this.status = "PUBLISHED";
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(Throwable error) {
        this.attempts++;
        long backoffSeconds = Math.min(60L, 1L << Math.min(this.attempts, 6));
        this.nextAttemptAt = Instant.now().plusSeconds(backoffSeconds);
        String message = error == null ? "Unknown publish error" : error.getMessage();
        this.lastError = message == null ? error.getClass().getSimpleName() : message.substring(0, Math.min(message.length(), 2000));
    }

    public io.opentelemetry.context.Context traceContext() { return OutboxTraceContext.restore(traceparent, tracestate); }
    public String getTraceparent() { return traceparent; }
    public String getTracestate() { return tracestate; }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public String getCorrelationId() { return correlationId; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
}

package com.srinu.payments.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_payment", columnList = "payment_id"),
    @Index(name = "idx_notification_transfer", columnList = "transfer_id"),
    @Index(name = "idx_notification_status", columnList = "status")
})
public class NotificationRecord {
    @Id
    private UUID id;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "customer_id", length = 80)
    private String customerId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, length = 40)
    private String channel;

    @Column(length = 160)
    private String destination;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationRecord() {}

    public NotificationRecord(UUID paymentId, UUID transferId, String customerId,
                              String eventType, String channel, String destination) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.transferId = transferId;
        this.customerId = customerId;
        this.eventType = eventType;
        this.channel = channel;
        this.destination = destination;
        this.status = "RECEIVED";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void markProcessing() {
        attempts++;
        status = attempts == 1 ? "PROCESSING" : "RETRYING";
        updatedAt = Instant.now();
    }

    public void markSent() {
        status = "SENT";
        sentAt = Instant.now();
        updatedAt = sentAt;
        lastError = null;
    }

    public void markFailed(String error) {
        status = "FAILED";
        lastError = error;
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getTransferId() { return transferId; }
    public String getCustomerId() { return customerId; }
    public String getEventType() { return eventType; }
    public String getStatus() { return status; }
    public String getChannel() { return channel; }
    public String getDestination() { return destination; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSentAt() { return sentAt; }
}

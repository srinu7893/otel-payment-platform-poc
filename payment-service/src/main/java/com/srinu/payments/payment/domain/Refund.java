package com.srinu.payments.payment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_refund_payment", columnNames = "payment_id"),
        @UniqueConstraint(name = "uk_refund_idempotency", columnNames = "idempotency_key")
    },
    indexes = {
        @Index(name = "idx_refund_customer", columnList = "customer_id"),
        @Index(name = "idx_refund_status", columnList = "status"),
        @Index(name = "idx_refund_created_at", columnList = "created_at")
    })
public class Refund {
    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false, updatable = false, length = 80)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "bank_transaction_id")
    private UUID bankTransactionId;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "reconciliation_attempts", nullable = false)
    private int reconciliationAttempts;

    @Column(name = "last_reconciliation_at")
    private Instant lastReconciliationAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Refund() {}

    public Refund(UUID id, UUID paymentId, String idempotencyKey, String customerId, BigDecimal amount) {
        this.id = id;
        this.paymentId = paymentId;
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.amount = amount;
        this.status = "PROCESSING";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void complete(UUID transactionId) {
        status = "COMPLETED";
        bankTransactionId = transactionId;
        failureCode = null;
        updatedAt = Instant.now();
    }

    public void fail(String code) {
        status = "FAILED";
        failureCode = code;
        updatedAt = Instant.now();
    }

    public void markReconciliationRequired(String code) {
        status = "RECONCILIATION_REQUIRED";
        failureCode = code;
        updatedAt = Instant.now();
    }

    public void noteReconciliationAttempt() {
        reconciliationAttempts++;
        lastReconciliationAt = Instant.now();
        updatedAt = lastReconciliationAt;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public UUID getBankTransactionId() { return bankTransactionId; }
    public String getFailureCode() { return failureCode; }
    public int getReconciliationAttempts() { return reconciliationAttempts; }
    public Instant getLastReconciliationAt() { return lastReconciliationAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

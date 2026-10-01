package com.srinu.payments.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers", uniqueConstraints = @UniqueConstraint(name = "uk_transfer_idempotency", columnNames = "idempotency_key"), indexes = {
    @Index(name = "idx_transfer_sender", columnList = "sender_account"),
    @Index(name = "idx_transfer_receiver", columnList = "receiver_account"),
    @Index(name = "idx_transfer_status", columnList = "status"),
    @Index(name = "idx_transfer_created_at", columnList = "created_at")
})
public class Transfer {
    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false, updatable = false, length = 80)
    private String customerId;

    @Column(name = "sender_account", nullable = false, updatable = false, length = 64)
    private String senderAccount;

    @Column(name = "receiver_account", nullable = false, updatable = false, length = 64)
    private String receiverAccount;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

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

    protected Transfer() {}

    public Transfer(UUID id, String idempotencyKey, String customerId, String senderAccount,
                    String receiverAccount, BigDecimal amount, String currency) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.senderAccount = senderAccount;
        this.receiverAccount = receiverAccount;
        this.amount = amount;
        this.currency = currency;
        this.status = "PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void markProcessing() {
        this.status = "PROCESSING";
        this.failureCode = null;
        this.updatedAt = Instant.now();
    }

    public void complete(UUID bankTransactionId) {
        this.status = "COMPLETED";
        this.bankTransactionId = bankTransactionId;
        this.failureCode = null;
        this.updatedAt = Instant.now();
    }

    public void fail(String code) {
        this.status = "FAILED";
        this.failureCode = code;
        this.updatedAt = Instant.now();
    }

    public void markReconciliationRequired(String code) {
        this.status = "RECONCILIATION_REQUIRED";
        this.failureCode = code;
        this.updatedAt = Instant.now();
    }

    public void noteReconciliationAttempt() {
        reconciliationAttempts++;
        lastReconciliationAt = Instant.now();
        updatedAt = lastReconciliationAt;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCustomerId() { return customerId; }
    public String getSenderAccount() { return senderAccount; }
    public String getReceiverAccount() { return receiverAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public UUID getBankTransactionId() { return bankTransactionId; }
    public String getFailureCode() { return failureCode; }
    public int getReconciliationAttempts() { return reconciliationAttempts; }
    public Instant getLastReconciliationAt() { return lastReconciliationAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

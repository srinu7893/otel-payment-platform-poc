package com.srinu.payments.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "uk_payment_idempotency", columnNames = "idempotency_key"), indexes = {
        @Index(name = "idx_payment_customer", columnList = "customer_id"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_created_at", columnList = "created_at"),
        @Index(name = "idx_payment_bank_transaction", columnList = "bank_transaction_id")
})
public class Payment {
    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false, updatable = false, length = 80)
    private String customerId;

    @Column(name = "account_number", nullable = false, updatable = false, length = 64)
    private String accountNumber;

    @Column(nullable = false, updatable = false, length = 120)
    private String merchant;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "bank_transaction_id")
    private UUID bankTransactionId;

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

    protected Payment() {}

    public Payment(UUID id, String idempotencyKey, String customerId, String accountNumber, String merchant, BigDecimal amount) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.merchant = merchant;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markProcessing() { transitionTo(PaymentStatus.PROCESSING, null); }
    public void markCompleted() { markCompleted(null); }
    public void markCompleted(UUID transactionId) {
        this.bankTransactionId = transactionId;
        transitionTo(PaymentStatus.COMPLETED, null);
    }
    public void markDeclined(String code) { transitionTo(PaymentStatus.DECLINED, code); }
    public void markFailed(String code) { transitionTo(PaymentStatus.FAILED, code); }
    public void markReconciliationRequired(String code) {
        transitionTo(PaymentStatus.RECONCILIATION_REQUIRED, code);
    }
    public void noteReconciliationAttempt() {
        reconciliationAttempts++;
        lastReconciliationAt = Instant.now();
        updatedAt = lastReconciliationAt;
    }

    public void cancel() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING payments can be cancelled");
        }
        transitionTo(PaymentStatus.CANCELLED, null);
    }

    private void transitionTo(PaymentStatus next, String code) {
        this.status = next;
        this.failureCode = code;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCustomerId() { return customerId; }
    public String getAccountNumber() { return accountNumber; }
    public String getMerchant() { return merchant; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public UUID getBankTransactionId() { return bankTransactionId; }
    public int getReconciliationAttempts() { return reconciliationAttempts; }
    public Instant getLastReconciliationAt() { return lastReconciliationAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

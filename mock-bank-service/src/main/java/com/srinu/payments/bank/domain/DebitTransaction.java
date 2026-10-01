package com.srinu.payments.bank.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "debit_transactions",
    uniqueConstraints = @UniqueConstraint(name = "uk_debit_transaction_payment", columnNames = "payment_id"),
    indexes = {
        @Index(name = "idx_debit_transaction_account", columnList = "account_number"),
        @Index(name = "idx_debit_transaction_status", columnList = "status"),
        @Index(name = "idx_debit_transaction_created_at", columnList = "created_at")
    })
public class DebitTransaction {
    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "account_number", nullable = false, updatable = false, length = 64)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DebitTransaction() {}

    public DebitTransaction(UUID transactionId, UUID paymentId, String accountNumber,
                            BigDecimal amount, String status, String message) {
        this.transactionId = transactionId;
        this.paymentId = paymentId;
        this.accountNumber = accountNumber;
        this.amount = amount;
        this.status = status;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public UUID getTransactionId() { return transactionId; }
    public UUID getPaymentId() { return paymentId; }
    public String getAccountNumber() { return accountNumber; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}

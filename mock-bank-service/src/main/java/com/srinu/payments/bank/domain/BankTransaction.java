package com.srinu.payments.bank.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_transaction", indexes = {
    @Index(name = "idx_bank_tx_payment", columnList = "paymentId"),
    @Index(name = "idx_bank_tx_sender", columnList = "senderAccount"),
    @Index(name = "idx_bank_tx_receiver", columnList = "receiverAccount"),
    @Index(name = "idx_bank_tx_created", columnList = "createdAt")
})
public class BankTransaction {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private UUID paymentId;
    @Column(nullable = false, length = 40)
    private String type;
    @Column(nullable = false, length = 64)
    private String senderAccount;
    @Column(length = 64)
    private String receiverAccount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BankTransaction() {}

    public BankTransaction(UUID paymentId, String type, String senderAccount, String receiverAccount,
                           BigDecimal amount, String currency, String status) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.type = type;
        this.senderAccount = senderAccount;
        this.receiverAccount = receiverAccount;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getType() { return type; }
    public String getSenderAccount() { return senderAccount; }
    public String getReceiverAccount() { return receiverAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.srinu.payments.bank.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "bank_account")
public class Account {
    @Id
    private String accountNumber;
    @Column(nullable = false)
    private String customerName;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    @Column(nullable = false)
    private boolean active = true;
    @Version
    private Long version;

    protected Account() {}

    public Account(String accountNumber, String customerName, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.customerName = customerName;
        this.balance = balance;
        this.active = true;
    }

    public String getAccountNumber() { return accountNumber; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getBalance() { return balance; }
    public boolean isActive() { return active; }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return active && balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        if (!active) throw new IllegalStateException("Account is inactive");
        this.balance = this.balance.subtract(amount);
    }

    public void updateProfile(String customerName) { this.customerName = customerName; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }
}

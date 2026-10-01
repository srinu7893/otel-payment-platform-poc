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
    @Version
    private Long version;

    protected Account() {}

    public Account(String accountNumber, String customerName, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.customerName = customerName;
        this.balance = balance;
    }

    public String getAccountNumber() { return accountNumber; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getBalance() { return balance; }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }
}

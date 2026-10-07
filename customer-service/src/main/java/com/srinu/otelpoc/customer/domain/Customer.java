package com.srinu.otelpoc.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private boolean active = true;

    // JPA persists this field through field access; it is not part of the JSON API.
    @SuppressWarnings("unused")
    private Instant createdAt = Instant.now();

    protected Customer() {}

    public Customer(String id, String name, String email, String account) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.accountNumber = account;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getAccountNumber() { return accountNumber; }
    public boolean isActive() { return active; }

    public void update(String name, String email, String account) {
        this.name = name;
        this.email = email;
        this.accountNumber = account;
    }

    public void deactivate() { active = false; }
}

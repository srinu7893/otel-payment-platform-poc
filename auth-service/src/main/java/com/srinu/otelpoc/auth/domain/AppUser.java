package com.srinu.otelpoc.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_account", indexes = @Index(name = "idx_user_customer", columnList = "customerId"))
public class AppUser {
    @Id
    @Column(length = 80)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 80)
    private String customerId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean locked = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "username"))
    @Column(name = "role", nullable = false, length = 30)
    private Set<String> roles = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant lastLoginAt;

    protected AppUser() {}

    public AppUser(String username, String passwordHash, String customerId, Set<String> roles) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.customerId = customerId;
        this.roles = new HashSet<>(roles);
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getCustomerId() { return customerId; }
    public boolean isEnabled() { return enabled; }
    public boolean isLocked() { return locked; }
    public Set<String> getRoles() { return Set.copyOf(roles); }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void recordLogin() { this.lastLoginAt = Instant.now(); }
    public void disable() { this.enabled = false; }
    public void enable() { this.enabled = true; }
    public void lock() { this.locked = true; }
    public void unlock() { this.locked = false; }
}

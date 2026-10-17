package com.mcp_gateway_k8s_store.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "app_users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_users_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_app_users_username_normalized", columnNames = "username_normalized"),
        @UniqueConstraint(name = "uk_app_users_email_normalized", columnNames = "email_normalized")
})
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "username_normalized", length = 80)
    private String usernameNormalized;

    @Column(length = 254)
    private String email;

    @Column(name = "email_normalized", length = 254)
    private String emailNormalized;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() { }

    public AppUser(String username, String passwordHash, UserRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
    }

    public AppUser(String username, String email, String passwordHash, UserRole role) {
        this(username, passwordHash, role);
        this.email = email == null ? null : email.trim();
        this.emailNormalized = this.email == null ? null : this.email.toLowerCase(java.util.Locale.ROOT);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        usernameNormalized = username == null ? null : username.toLowerCase(java.util.Locale.ROOT);
        if (email != null) { email = email.trim(); emailNormalized = email.toLowerCase(java.util.Locale.ROOT); }
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getEmailNormalized() { return emailNormalized; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}

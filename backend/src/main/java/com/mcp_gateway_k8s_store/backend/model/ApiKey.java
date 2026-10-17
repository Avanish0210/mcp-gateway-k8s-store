package com.mcp_gateway_k8s_store.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "ix_api_keys_owner", columnList = "owner_id"),
        @Index(name = "ux_api_keys_prefix", columnList = "key_prefix", unique = true)
})
public class ApiKey {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(name = "key_name", nullable = false, length = 100)
    private String name;

    @Column(name = "key_prefix", nullable = false, unique = true, length = 40)
    private String keyPrefix;

    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() { }

    public ApiKey(AppUser owner, String name, String keyPrefix, String secretHash, Instant expiresAt) {
        this.owner = owner;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.secretHash = secretHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public String getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getName() { return name; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getSecretHash() { return secretHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isRevoked() { return revokedAt != null; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public void revoke(Instant at) { if (revokedAt == null) revokedAt = at; }
}

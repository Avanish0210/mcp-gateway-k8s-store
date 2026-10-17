package com.mcp_gateway_k8s_store.backend.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "team_invitations", indexes = {
        @Index(name = "ux_team_invitation_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "ux_team_invitation_pending_email", columnList = "active_email", unique = true),
        @Index(name = "ix_team_invitation_creator", columnList = "created_by_id")
})
public class TeamInvitation {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 254)
    private String emailNormalized;

    @Column(name = "active_email", length = 254)
    private String activeEmail;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected TeamInvitation() { }

    public TeamInvitation(String email, String tokenHash, AppUser createdBy, Instant expiresAt) {
        this.email = email.trim();
        this.emailNormalized = this.email.toLowerCase(Locale.ROOT);
        this.activeEmail = this.emailNormalized;
        this.tokenHash = tokenHash;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getTokenHash() { return tokenHash; }
    public AppUser getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getStatus(Instant now) {
        if (acceptedAt != null) return "ACCEPTED";
        if (revokedAt != null) return "REVOKED";
        return expiresAt != null && !expiresAt.isAfter(now) ? "EXPIRED" : "PENDING";
    }
    public boolean isPending(Instant now) { return "PENDING".equals(getStatus(now)); }
    public void accept(Instant now) { acceptedAt = now; activeEmail = null; }
    public void revoke(Instant now) { revokedAt = now; activeEmail = null; }
    public void expire() { activeEmail = null; }
}

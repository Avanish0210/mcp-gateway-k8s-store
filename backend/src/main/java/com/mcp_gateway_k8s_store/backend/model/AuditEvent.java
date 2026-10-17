package com.mcp_gateway_k8s_store.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_audit_actor_time", columnList = "actor_id, occurred_at"),
        @Index(name = "idx_audit_event_time", columnList = "event_type, occurred_at"),
        @Index(name = "idx_audit_server", columnList = "server_id"),
        @Index(name = "idx_audit_store", columnList = "store_id"),
        @Index(name = "idx_audit_tool", columnList = "tool_name")
})
public class AuditEvent {
    @Id @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @Column(name = "actor_id", length = 36) private String actorId;
    @Column(name = "actor_username", length = 80) private String actorUsername;
    @Column(name = "event_type", nullable = false, length = 80) private String eventType;
    @Column(name = "server_id", length = 80) private String serverId;
    @Column(name = "tool_name", length = 120) private String toolName;
    @Column(name = "store_id", length = 120) private String storeId;
    @Column(nullable = false, length = 10) @Enumerated(EnumType.STRING) private AuditOutcome outcome;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "duration_ms") private Long durationMs;
    @Column(name = "error_category", length = 80) private String errorCategory;
    @Column(name = "description", length = 500) private String description;
    @Column(name = "correlation_id", length = 100) private String correlationId;

    protected AuditEvent() { }
    public AuditEvent(String actorId, String actorUsername, String eventType, String serverId, String toolName,
                      String storeId, AuditOutcome outcome, Long durationMs, String errorCategory,
                      String description, String correlationId) {
        this.id = UUID.randomUUID().toString(); this.actorId = actorId; this.actorUsername = actorUsername;
        this.eventType = eventType; this.serverId = serverId; this.toolName = toolName; this.storeId = storeId;
        this.outcome = outcome; this.durationMs = durationMs; this.errorCategory = errorCategory;
        this.description = description; this.correlationId = correlationId; this.occurredAt = Instant.now();
    }
    public String getId() { return id; } public String getActorId() { return actorId; }
    public String getActorUsername() { return actorUsername; } public String getEventType() { return eventType; }
    public String getServerId() { return serverId; } public String getToolName() { return toolName; }
    public String getStoreId() { return storeId; } public AuditOutcome getOutcome() { return outcome; }
    public Instant getOccurredAt() { return occurredAt; } public Long getDurationMs() { return durationMs; }
    public String getErrorCategory() { return errorCategory; } public String getDescription() { return description; }
    public String getCorrelationId() { return correlationId; }
}

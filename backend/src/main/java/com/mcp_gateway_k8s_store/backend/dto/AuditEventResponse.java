package com.mcp_gateway_k8s_store.backend.dto;

import com.mcp_gateway_k8s_store.backend.model.AuditEvent;
import com.mcp_gateway_k8s_store.backend.model.AuditOutcome;
import java.time.Instant;

public record AuditEventResponse(String id, String actorId, String actorUsername, String eventType,
        String serverId, String toolName, String storeId, AuditOutcome outcome, Instant occurredAt,
        Long durationMs, String errorCategory, String description, String correlationId) {
    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getActorId(), e.getActorUsername(), e.getEventType(),
                e.getServerId(), e.getToolName(), e.getStoreId(), e.getOutcome(), e.getOccurredAt(),
                e.getDurationMs(), e.getErrorCategory(), e.getDescription(), e.getCorrelationId());
    }
}

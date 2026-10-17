package com.mcp_gateway_k8s_store.backend.dto;

import com.mcp_gateway_k8s_store.backend.model.ApiKey;

import java.time.Instant;

public record ApiKeyResponse(String id, String name, String prefix, Instant createdAt,
                             Instant lastUsedAt, Instant expiresAt, boolean revoked) {
    public static ApiKeyResponse from(ApiKey key) {
        return new ApiKeyResponse(key.getId(), key.getName(), key.getKeyPrefix(), key.getCreatedAt(),
                key.getLastUsedAt(), key.getExpiresAt(), key.isRevoked());
    }
}

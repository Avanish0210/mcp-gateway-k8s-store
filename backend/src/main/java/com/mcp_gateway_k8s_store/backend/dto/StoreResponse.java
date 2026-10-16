package com.mcp_gateway_k8s_store.backend.dto;

import com.mcp_gateway_k8s_store.backend.model.Store;
import com.mcp_gateway_k8s_store.backend.model.StoreStatus;

import java.time.Instant;

public record StoreResponse(
        String id,
        String name,
        String namespace,
        String helmRelease,
        String storeUrl,
        StoreStatus status,
        String errorMessage,
        Instant createdAt
) {
    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getName(),
                store.getNamespaceName(),
                store.getHelmRelease(),
                store.getStoreUrl(),
                store.getStatus(),
                store.getErrorMessage(),
                store.getCreatedAt()
        );
    }
}

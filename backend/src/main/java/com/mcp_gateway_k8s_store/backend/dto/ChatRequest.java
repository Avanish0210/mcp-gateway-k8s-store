package com.mcp_gateway_k8s_store.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "Message must not be empty")
        String message,
        String storeId
) {
}

package com.mcp_gateway_k8s_store.backend.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStoreRequest(
        @NotBlank(message = "Store name is required")
        @Size(min = 2, max = 100, message = "Store name must contain 2-100 characters")
        String name
) {
}

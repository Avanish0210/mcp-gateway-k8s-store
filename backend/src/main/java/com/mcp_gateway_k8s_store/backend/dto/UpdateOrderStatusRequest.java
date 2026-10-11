package com.mcp_gateway_k8s_store.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateOrderStatusRequest(

        @NotBlank(message = "Order status is required")
        String status

) {
}

package com.mcp_gateway_k8s_store.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateOrderStatusRequest(

        @NotBlank(message = "Order status is required")
        @Pattern(regexp = "pending|processing|on-hold|completed|cancelled|refunded|failed",
                message = "Order status is not supported")
        String status

) {
}

package com.mcp_gateway_k8s_store.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(max = 200, message = "Product name cannot exceed 200 characters")
        String name,

        @NotBlank(message = "Product type is required")
        String type,

        @PositiveOrZero(message = "Price cannot be negative")
        BigDecimal regularPrice,

        String description,

        Boolean virtual,

        Boolean downloadable

) {
}

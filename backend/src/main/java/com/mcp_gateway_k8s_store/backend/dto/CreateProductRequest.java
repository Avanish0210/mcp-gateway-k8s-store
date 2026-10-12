package com.mcp_gateway_k8s_store.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record CreateProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(max = 200, message = "Product name cannot exceed 200 characters")
        String name,

        @Pattern(regexp = "simple|virtual|downloadable", message = "Product type must be simple, virtual, or downloadable")
        String type,

        @NotNull(message = "Regular price is required")
        @PositiveOrZero(message = "Price cannot be negative")
        @Digits(integer = 12, fraction = 2, message = "Price must have at most two decimal places")
        BigDecimal regularPrice,

        @Size(max = 10000, message = "Description cannot exceed 10000 characters")
        String description

) {
}

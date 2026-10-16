package com.mcp_gateway_k8s_store.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) { }

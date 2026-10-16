package com.mcp_gateway_k8s_store.backend.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserProfileResponse user) { }

package com.mcp_gateway_k8s_store.backend.dto;

import com.mcp_gateway_k8s_store.backend.model.AppUser;
import com.mcp_gateway_k8s_store.backend.model.UserRole;

import java.time.Instant;

public record TeamMemberResponse(String id, String username, String email, UserRole role, boolean enabled, Instant createdAt) {
    public static TeamMemberResponse from(AppUser user) {
        return new TeamMemberResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}

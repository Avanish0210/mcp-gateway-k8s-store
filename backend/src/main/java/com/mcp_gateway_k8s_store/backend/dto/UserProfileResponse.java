package com.mcp_gateway_k8s_store.backend.dto;

import com.mcp_gateway_k8s_store.backend.model.AppUser;
import com.mcp_gateway_k8s_store.backend.model.UserRole;

public record UserProfileResponse(String id, String username, UserRole role, boolean enabled) {
    public static UserProfileResponse from(AppUser user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getRole(), user.isEnabled());
    }
}

package com.mcp_gateway_k8s_store.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

import java.nio.charset.StandardCharsets;

public record AcceptInvitationRequest(
        @NotBlank @Size(max = 256) String token,
        @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
        @NotBlank @Size(min = 12, max = 72) String password
) {
    @AssertTrue(message = "Password exceeds the configured password encoder limit")
    public boolean isPasswordWithinEncoderLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}

package com.mcp_gateway_k8s_store.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(@NotBlank @Email @Size(max = 254) String email) { }

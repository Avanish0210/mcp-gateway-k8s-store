package com.mcp_gateway_k8s_store.backend.dto;

import java.time.Instant;

public record CreatedInvitationResponse(String id, String email, String status, Instant createdAt,
                                        Instant expiresAt, String inviteUrl, String deliveryMode) { }

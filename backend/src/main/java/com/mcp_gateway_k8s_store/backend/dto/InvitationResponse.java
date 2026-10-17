package com.mcp_gateway_k8s_store.backend.dto;

import com.mcp_gateway_k8s_store.backend.model.TeamInvitation;

import java.time.Instant;

public record InvitationResponse(String id, String email, String status, Instant createdAt, Instant expiresAt,
                                 Instant acceptedAt, Instant revokedAt, String inviteUrl) {
    public static InvitationResponse from(TeamInvitation invitation, Instant now, String inviteUrl) {
        return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getStatus(now),
                invitation.getCreatedAt(), invitation.getExpiresAt(), invitation.getAcceptedAt(),
                invitation.getRevokedAt(), inviteUrl);
    }
}

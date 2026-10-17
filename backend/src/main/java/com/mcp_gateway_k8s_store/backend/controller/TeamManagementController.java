package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.dto.CreateInvitationRequest;
import com.mcp_gateway_k8s_store.backend.dto.CreatedInvitationResponse;
import com.mcp_gateway_k8s_store.backend.dto.InvitationResponse;
import com.mcp_gateway_k8s_store.backend.dto.TeamMemberResponse;
import com.mcp_gateway_k8s_store.backend.service.TeamInvitationService;
import com.mcp_gateway_k8s_store.backend.service.AuditService;
import com.mcp_gateway_k8s_store.backend.model.AuditOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/team")
public class TeamManagementController {
    private final TeamInvitationService invitations;
    private final AuditService audit;
    public TeamManagementController(TeamInvitationService invitations, AuditService audit) { this.invitations = invitations; this.audit = audit; }

    @GetMapping("/members")
    public List<TeamMemberResponse> members(Authentication authentication) { return invitations.members(authentication.getName()); }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedInvitationResponse create(Authentication authentication, @Valid @RequestBody CreateInvitationRequest request) {
        try {
            CreatedInvitationResponse created = invitations.create(authentication.getName(), request.email());
            audit.recordCurrent("TEAM_INVITATION_CREATED", null, null, created.id(), AuditOutcome.SUCCESS, null, null, "Team invitation created");
            return created;
        } catch (RuntimeException failure) {
            audit.recordCurrent("TEAM_INVITATION_CREATED", null, null, null, AuditOutcome.FAILURE, null, failure, null);
            throw failure;
        }
    }

    @GetMapping("/invitations")
    public List<InvitationResponse> invitations(Authentication authentication) { return invitations.list(authentication.getName()); }

    @DeleteMapping("/invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication authentication, @PathVariable String id) {
        try {
            invitations.revoke(authentication.getName(), id);
            audit.recordCurrent("TEAM_INVITATION_REVOKED", null, null, id, AuditOutcome.SUCCESS, null, null, "Team invitation revoked");
        } catch (RuntimeException failure) {
            audit.recordCurrent("TEAM_INVITATION_REVOKED", null, null, id, AuditOutcome.FAILURE, null, failure, null);
            throw failure;
        }
    }
}

package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.dto.LoginRequest;
import com.mcp_gateway_k8s_store.backend.dto.LoginResponse;
import com.mcp_gateway_k8s_store.backend.dto.UserProfileResponse;
import com.mcp_gateway_k8s_store.backend.dto.AcceptInvitationRequest;
import com.mcp_gateway_k8s_store.backend.dto.AcceptInvitationResponse;
import com.mcp_gateway_k8s_store.backend.model.AppUser;
import com.mcp_gateway_k8s_store.backend.repository.AppUserRepository;
import com.mcp_gateway_k8s_store.backend.service.JwtTokenService;
import com.mcp_gateway_k8s_store.backend.service.TeamInvitationService;
import com.mcp_gateway_k8s_store.backend.service.InvitationAcceptanceRateLimiter;
import com.mcp_gateway_k8s_store.backend.service.AuditService;
import com.mcp_gateway_k8s_store.backend.model.AuditOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final JwtTokenService tokens;
    private final TeamInvitationService invitations;
    private final InvitationAcceptanceRateLimiter rateLimiter;
    private final AuditService audit;

    public AuthenticationController(AuthenticationManager authenticationManager, AppUserRepository users, JwtTokenService tokens,
                                     TeamInvitationService invitations, InvitationAcceptanceRateLimiter rateLimiter, AuditService audit) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.tokens = tokens;
        this.invitations = invitations;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @PostMapping("/accept-invitation")
    public AcceptInvitationResponse acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.allow(httpRequest.getRemoteAddr()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many invitation attempts. Try again shortly.");
        AcceptInvitationResponse accepted = invitations.accept(request.token(), request.username(), request.password());
        audit.recordForUser(accepted.id(), accepted.username(), "TEAM_INVITATION_ACCEPTED", accepted.id(), AuditOutcome.SUCCESS, "Invitation accepted");
        return accepted;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        AppUser user = users.findByUsernameIgnoreCase(request.username())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        return new LoginResponse(tokens.issue(user), "Bearer", tokens.expirationSeconds(), UserProfileResponse.from(user));
    }

    @GetMapping("/me")
    public UserProfileResponse me(Authentication authentication) {
        return users.findByUsernameIgnoreCase(authentication.getName())
                .filter(AppUser::isEnabled)
                .map(UserProfileResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }
}

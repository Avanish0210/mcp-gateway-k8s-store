package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.dto.*;
import com.mcp_gateway_k8s_store.backend.model.AppUser;
import com.mcp_gateway_k8s_store.backend.model.TeamInvitation;
import com.mcp_gateway_k8s_store.backend.model.UserRole;
import com.mcp_gateway_k8s_store.backend.repository.AppUserRepository;
import com.mcp_gateway_k8s_store.backend.repository.TeamInvitationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class TeamInvitationService {
    private final TeamInvitationRepository invitations;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();
    private final long expirationHours;
    private final boolean developmentLinksEnabled;
    private final String frontendBaseUrl;

    public TeamInvitationService(TeamInvitationRepository invitations, AppUserRepository users,
                                 PasswordEncoder passwords, Environment environment,
                                 @Value("${team.invitations.expiration-hours:72}") long expirationHours,
                                 @Value("${team.invitations.development-links-enabled:false}") boolean developmentLinksEnabled,
                                 @Value("${team.invitations.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.invitations = invitations;
        this.users = users;
        this.passwords = passwords;
        this.environment = environment;
        this.expirationHours = Math.min(720, Math.max(1, expirationHours));
        this.developmentLinksEnabled = developmentLinksEnabled;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public CreatedInvitationResponse create(String adminUsername, String requestedEmail) {
        requireAdmin(adminUsername);
        boolean developmentLink = allowDevelopmentLink();
        if (!developmentLink) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Email delivery is not configured. Enable development invitation links only in a local dev profile.");
        String email = requestedEmail.trim();
        String emailKey = email.toLowerCase(Locale.ROOT);
        if (users.existsByEmailNormalized(emailKey)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This email already belongs to a registered user");
        TeamInvitation existing = invitations.findByActiveEmail(emailKey).orElse(null);
        Instant now = Instant.now();
        if (existing != null && existing.isPending(now)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An active invitation already exists for this email");
        if (existing != null) {
            existing.expire();
            invitations.saveAndFlush(existing);
        }
        String token = randomToken();
        Instant expires = now.plus(expirationHours, ChronoUnit.HOURS);
        TeamInvitation invitation = new TeamInvitation(email, sha256(token), requireAdmin(adminUsername), expires);
        try {
            invitation = invitations.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException collision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active invitation already exists for this email");
        }
        String url = frontendBaseUrl + "/accept-invitation?token=" + token;
        return new CreatedInvitationResponse(invitation.getId(), invitation.getEmail(), "PENDING",
                invitation.getCreatedAt(), invitation.getExpiresAt(), url, "DEVELOPMENT_LINK");
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> list(String adminUsername) {
        requireAdmin(adminUsername);
        Instant now = Instant.now();
        return invitations.findAllByOrderByCreatedAtDesc().stream()
                .map(invitation -> InvitationResponse.from(invitation, now, null)).toList();
    }

    @Transactional
    public void revoke(String adminUsername, String id) {
        requireAdmin(adminUsername);
        TeamInvitation invitation = invitations.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        Instant now = Instant.now();
        if (!invitation.isPending(now)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending invitations can be revoked");
        invitation.revoke(now);
        invitations.save(invitation);
    }

    @Transactional
    public AcceptInvitationResponse accept(String token, String username, String password) {
        TeamInvitation invitation = invitations.findByTokenHashForUpdate(sha256(token))
                .orElseThrow(TeamInvitationService::invalidInvitation);
        Instant now = Instant.now();
        if (!invitation.isPending(now)) throw invalidInvitation();
        String cleanUsername = username.trim();
        if (users.existsByUsernameIgnoreCase(cleanUsername)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use");
        if (users.existsByEmailNormalized(invitation.getEmail().toLowerCase(Locale.ROOT)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email already belongs to a registered user");
        AppUser user = new AppUser(cleanUsername, invitation.getEmail(), passwords.encode(password), UserRole.MEMBER);
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException collision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already in use");
        }
        invitation.accept(now);
        invitations.save(invitation);
        return new AcceptInvitationResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> members(String adminUsername) {
        requireAdmin(adminUsername);
        return users.findAllByOrderByCreatedAtAsc().stream().map(TeamMemberResponse::from).toList();
    }

    private AppUser requireAdmin(String username) {
        AppUser admin = users.findByUsernameIgnoreCase(username)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        if (admin.getRole() != UserRole.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required");
        return admin;
    }

    private boolean allowDevelopmentLink() {
        return developmentLinksEnabled && environment.acceptsProfiles(Profiles.of("dev", "local"));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String token) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }

    private static ResponseStatusException invalidInvitation() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is invalid, expired, revoked, or already used");
    }
}

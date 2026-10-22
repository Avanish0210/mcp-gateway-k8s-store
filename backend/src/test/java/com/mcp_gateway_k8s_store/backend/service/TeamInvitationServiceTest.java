package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.dto.AcceptInvitationResponse;
import com.mcp_gateway_k8s_store.backend.dto.CreatedInvitationResponse;
import com.mcp_gateway_k8s_store.backend.model.AppUser;
import com.mcp_gateway_k8s_store.backend.model.TeamInvitation;
import com.mcp_gateway_k8s_store.backend.model.UserRole;
import com.mcp_gateway_k8s_store.backend.repository.AppUserRepository;
import com.mcp_gateway_k8s_store.backend.repository.TeamInvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TeamInvitationServiceTest {
    private TeamInvitationRepository invitations;
    private AppUserRepository users;
    private Environment environment;
    private BCryptPasswordEncoder passwordEncoder;
    private InvitationEmailService invitationEmail;
    private TeamInvitationService service;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        invitations = mock(TeamInvitationRepository.class);
        users = mock(AppUserRepository.class);
        environment = mock(Environment.class);
        invitationEmail = new InvitationEmailService(mock(org.springframework.mail.javamail.JavaMailSender.class),
                false, "", "", "", "", true, "http://localhost:5173");
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new TeamInvitationService(invitations, users, passwordEncoder, environment, invitationEmail,
                72, true, "http://localhost:5173");
        admin = new AppUser("admin", "admin-password-hash", UserRole.ADMIN);
        when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        when(invitations.findByActiveEmail(anyString())).thenReturn(Optional.empty());
        when(invitations.saveAndFlush(any(TeamInvitation.class))).thenAnswer(call -> call.getArgument(0));
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void adminCanCreateSingleUseHashOnlyDevelopmentInvitation() throws Exception {
        CreatedInvitationResponse response = service.create("admin", "  member@example.com  ");
        assertEquals("PENDING", response.status());
        assertEquals("DEVELOPMENT_LINK", response.deliveryMode());
        assertTrue(response.inviteUrl().startsWith("http://localhost:5173/accept-invitation?token="));
        String token = response.inviteUrl().substring(response.inviteUrl().indexOf("token=") + 6);
        verify(invitations).saveAndFlush(argThat(invitation -> invitation.getEmail().equals("member@example.com")
                && invitation.getTokenHash().equals(hash(token)) && !invitation.getTokenHash().equals(token)));
    }

    @Test
    void onlyAdminsCanCreateInvitationsAndDuplicateActiveInvitesAreBlocked() {
        when(users.findByUsernameIgnoreCase("member")).thenReturn(Optional.of(new AppUser("member", "hash", UserRole.MEMBER)));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.create("member", "member@example.com")).getStatusCode().value());
        when(invitations.findByActiveEmail("member@example.com")).thenReturn(Optional.of(
                new TeamInvitation("member@example.com", "somehash", admin, Instant.now().plusSeconds(1000))));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.create("admin", "member@example.com")).getStatusCode().value());
    }

    @Test
    void developmentLinksAreNeverReturnedOutsideAnExplicitLocalProfile() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        TeamInvitationService productionSafeService = new TeamInvitationService(invitations, users, passwordEncoder,
                environment, invitationEmail, 72, true, "https://dashboard.example.com");
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> productionSafeService.create("admin", "member@example.com")).getStatusCode().value());
        verify(invitations, never()).saveAndFlush(any(TeamInvitation.class));
    }

    @Test
    void configuredMailDeliverySendsInvitationAndDoesNotReturnTokenLink() {
        org.springframework.mail.javamail.JavaMailSender sender = mock(org.springframework.mail.javamail.JavaMailSender.class);
        InvitationEmailService configuredMailer = new InvitationEmailService(sender, true, "smtp.example.com",
                "invites@example.com", "invites@example.com", "provider-app-password", true,
                "https://dashboard.example.com");
        TeamInvitationService mailService = new TeamInvitationService(invitations, users, passwordEncoder,
                environment, configuredMailer, 72, false, "https://dashboard.example.com");

        CreatedInvitationResponse response = mailService.create("admin", "member@example.com");

        assertEquals("EMAIL", response.deliveryMode());
        assertNull(response.inviteUrl());
        verify(sender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void acceptingInvitationCreatesMemberWithEncodedPasswordAndPreventsReuse() {
        String token = "secure-invitation-token";
        TeamInvitation invitation = new TeamInvitation("member@example.com", hash(token), admin, Instant.now().plusSeconds(1000));
        when(invitations.findByTokenHashForUpdate(hash(token))).thenReturn(Optional.of(invitation));
        AcceptInvitationResponse response = service.accept(token, "member-name", "some strong password 123");
        assertEquals("MEMBER", response.role());
        assertEquals("member@example.com", response.email());
        verify(users).saveAndFlush(argThat(user -> user.getRole() == UserRole.MEMBER
                && passwordEncoder.matches("some strong password 123", user.getPasswordHash())));
        assertNotNull(invitation.getAcceptedAt());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.accept(token, "another-name", "some strong password 123")).getStatusCode().value());
    }

    @Test
    void invalidExpiredRevokedAndPreviouslyRegisteredEmailAreRejected() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.accept("unknown", "member", "some strong password 123")).getStatusCode().value());
        TeamInvitation expired = new TeamInvitation("expired@example.com", "hash", admin, Instant.now().minusSeconds(1));
        when(invitations.findByTokenHashForUpdate(hash("expired"))).thenReturn(Optional.of(expired));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.accept("expired", "member", "some strong password 123")).getStatusCode().value());
        TeamInvitation revoked = new TeamInvitation("revoked@example.com", "hash", admin, Instant.now().plusSeconds(1000));
        revoked.revoke(Instant.now());
        when(invitations.findByTokenHashForUpdate(hash("revoked"))).thenReturn(Optional.of(revoked));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.accept("revoked", "member", "some strong password 123")).getStatusCode().value());
        TeamInvitation duplicateEmail = new TeamInvitation("existing@example.com", hash("duplicate"), admin, Instant.now().plusSeconds(1000));
        when(invitations.findByTokenHashForUpdate(hash("duplicate"))).thenReturn(Optional.of(duplicateEmail));
        when(users.existsByEmailNormalized("existing@example.com")).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.accept("duplicate", "member", "some strong password 123")).getStatusCode().value());
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}

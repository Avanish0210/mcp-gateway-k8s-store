package com.mcp_gateway_k8s_store.backend;

import com.mcp_gateway_k8s_store.backend.model.AppUser;
import com.mcp_gateway_k8s_store.backend.model.ApiKey;
import com.mcp_gateway_k8s_store.backend.model.UserRole;
import com.mcp_gateway_k8s_store.backend.model.StoreStatus;
import com.mcp_gateway_k8s_store.backend.dto.CreatedApiKeyResponse;
import com.mcp_gateway_k8s_store.backend.repository.ApiKeyRepository;
import com.mcp_gateway_k8s_store.backend.repository.AppUserRepository;
import com.mcp_gateway_k8s_store.backend.repository.StoreRepository;
import com.mcp_gateway_k8s_store.backend.repository.TeamInvitationRepository;
import com.mcp_gateway_k8s_store.backend.repository.AuditEventRepository;
import com.mcp_gateway_k8s_store.backend.model.AuditEvent;
import com.mcp_gateway_k8s_store.backend.model.TeamInvitation;
import com.mcp_gateway_k8s_store.backend.dto.CreatedInvitationResponse;
import com.mcp_gateway_k8s_store.backend.service.JwtTokenService;
import com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpService;
import com.mcp_gateway_k8s_store.backend.service.McpGatewayTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "security.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "spring.ai.mcp.client.enabled=false",
        "stores.provisioning.enabled=true",
        "spring.profiles.active=dev",
        "team.invitations.development-links-enabled=true",
        "team.invitations.email-enabled=false"
})
@AutoConfigureMockMvc
class BackendApplicationTests {
    @Autowired private WooCommerceMcpService wooCommerceMcpService;
    @Autowired private McpGatewayTools mcpGatewayTools;
    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenService jwtTokens;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private tools.jackson.databind.ObjectMapper objectMapper;

    @MockitoBean private AppUserRepository users;
    @MockitoBean private ApiKeyRepository apiKeys;
    @MockitoBean private StoreRepository stores;
    @MockitoBean private TeamInvitationRepository invitations;
    @MockitoBean private AuditEventRepository auditEvents;

    private AppUser admin;
    private AppUser member;

    @BeforeEach
    void setUpAccounts() {
        admin = new AppUser("admin-test", passwordEncoder.encode("correct horse battery"), UserRole.ADMIN);
        member = new AppUser("member-test", passwordEncoder.encode("member password safe"), UserRole.MEMBER);
        ReflectionTestUtils.setField(admin, "id", "admin-id");
        ReflectionTestUtils.setField(member, "id", "member-id");
        when(users.findByUsernameIgnoreCase("admin-test")).thenReturn(Optional.of(admin));
        when(users.findByUsernameIgnoreCase("member-test")).thenReturn(Optional.of(member));
        when(stores.existsByNamespaceName(anyString())).thenReturn(false);
        when(stores.findAll()).thenReturn(List.of());
        when(stores.findByStatusIn(org.mockito.ArgumentMatchers.<java.util.Collection<StoreStatus>>any())).thenReturn(List.of());
        when(apiKeys.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitations.saveAndFlush(any(TeamInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditEvents.saveAndFlush(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditEvents.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<AuditEvent>>any(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
    }

    @Test
    void contextLoadsWithoutRequiringExternalMcpServers() { assertNotNull(wooCommerceMcpService); }

    @Test
    void unauthenticatedProtectedRequestsReturn401AndHealthRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/weather/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/stores")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void loginReturnsJwtForValidPasswordAndRejectsInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin-test\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin-test\",\"password\":\"incorrect password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedProfileReturnsOnlyPublicUserFields() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwtTokens.issue(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin-test"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void memberIsForbiddenFromStoreAdministrationButAdminCanReadStoreManagement() throws Exception {
        mockMvc.perform(get("/api/stores").header("Authorization", "Bearer " + jwtTokens.issue(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/stores").header("Authorization", "Bearer " + jwtTokens.issue(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAdminsCanReadGlobalAuditWhileMembersCanReadTheirOwnEndpoint() throws Exception {
        mockMvc.perform(get("/api/audit").header("Authorization", "Bearer " + jwtTokens.issue(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit").header("Authorization", "Bearer " + jwtTokens.issue(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/audit/me").header("Authorization", "Bearer " + jwtTokens.issue(member)))
                .andExpect(status().isOk());
    }

    @Test
    void expiredInvalidAndDisabledJwtTokensReturn401() throws Exception {
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("mcp-gateway").subject("admin-test")
                        .issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(60)).build())).getTokenValue();
        mockMvc.perform(get("/api/weather/status").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/weather/status").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        AppUser disabled = new AppUser("disabled-test", passwordEncoder.encode("disabled password safe"), UserRole.MEMBER);
        disabled.setEnabled(false);
        when(users.findByUsernameIgnoreCase("disabled-test")).thenReturn(Optional.of(disabled));
        mockMvc.perform(get("/api/weather/status").header("Authorization", "Bearer " + jwtTokens.issue(disabled)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiKeyIsOwnedHashedAndReturnedOnlyOnCreation() throws Exception {
        var result = mockMvc.perform(post("/api/keys").header("Authorization", "Bearer " + jwtTokens.issue(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cursor laptop\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andExpect(jsonPath("$.key.name").value("Cursor laptop"))
                .andReturn();
        CreatedApiKeyResponse created = objectMapper.readValue(result.getResponse().getContentAsString(), CreatedApiKeyResponse.class);
        org.mockito.ArgumentCaptor<ApiKey> saved = org.mockito.ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeys).save(saved.capture());
        ApiKey stored = saved.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(member, stored.getOwner());
        org.junit.jupiter.api.Assertions.assertNotEquals(created.apiKey(), stored.getSecretHash());
        org.junit.jupiter.api.Assertions.assertEquals(64, stored.getSecretHash().length());

        when(apiKeys.findAllByOwner_IdOrderByCreatedAtDesc("member-id")).thenReturn(List.of(stored));
        mockMvc.perform(get("/api/keys").header("Authorization", "Bearer " + jwtTokens.issue(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Cursor laptop"))
                .andExpect(jsonPath("$[0].prefix").value(stored.getKeyPrefix()))
                .andExpect(jsonPath("$[0].secretHash").doesNotExist())
                .andExpect(jsonPath("$[0].apiKey").doesNotExist());
        verify(apiKeys).findAllByOwner_IdOrderByCreatedAtDesc("member-id");
    }

    @Test
    void memberCannotListOrRevokeAnotherUsersKeys() throws Exception {
        mockMvc.perform(get("/api/keys").header("Authorization", "Bearer " + jwtTokens.issue(member)))
                .andExpect(status().isOk());
        verify(apiKeys).findAllByOwner_IdOrderByCreatedAtDesc("member-id");
        mockMvc.perform(delete("/api/keys/{id}", "admin-key").header("Authorization", "Bearer " + jwtTokens.issue(member)))
                .andExpect(status().isNotFound());
        verify(apiKeys).findByIdAndOwner_Id("admin-key", "member-id");
    }

    @Test
    void ownedKeyCanBeRevokedAndImmediatelyStopsAuthenticating() throws Exception {
        var result = mockMvc.perform(post("/api/keys").header("Authorization", "Bearer " + jwtTokens.issue(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test client\"}"))
                .andExpect(status().isCreated()).andReturn();
        CreatedApiKeyResponse created = objectMapper.readValue(result.getResponse().getContentAsString(), CreatedApiKeyResponse.class);
        ApiKey stored = new ApiKey(admin, "Test client", created.key().prefix(), "", null);
        ReflectionTestUtils.setField(stored, "id", created.key().id());
        ReflectionTestUtils.setField(stored, "secretHash", com.mcp_gateway_k8s_store.backend.service.ApiKeyService.sha256(created.apiKey()));
        ReflectionTestUtils.setField(stored, "createdAt", Instant.now());
        when(apiKeys.findByKeyPrefix(created.key().prefix())).thenReturn(Optional.of(stored));
        mockMvc.perform(get("/api/weather/status").header("Authorization", "Bearer " + created.apiKey()))
                .andExpect(status().isOk());

        when(apiKeys.findByIdAndOwner_Id(created.key().id(), "admin-id")).thenReturn(Optional.of(stored));
        mockMvc.perform(delete("/api/keys/{id}", created.key().id()).header("Authorization", "Bearer " + jwtTokens.issue(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/weather/status").header("Authorization", "Bearer " + created.apiKey()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownInvalidExpiredAndDisabledApiKeysAreRejected() throws Exception {
        mockMvc.perform(get("/api/weather/status").header("Authorization", "Bearer mcpk_unknown.secret"))
                .andExpect(status().isUnauthorized());
        ApiKey saved = new ApiKey(admin, "temporary", "mcpk_known", "not-a-matching-hash", null);
        when(apiKeys.findByKeyPrefix("mcpk_known")).thenReturn(Optional.of(saved));
        mockMvc.perform(get("/api/weather/status").header("Authorization", "Bearer mcpk_known.invalid"))
                .andExpect(status().isUnauthorized());

        String expiredToken = "mcpk_expired.secret";
        ApiKey expired = new ApiKey(admin, "expired", "mcpk_expired", com.mcp_gateway_k8s_store.backend.service.ApiKeyService.sha256(expiredToken), Instant.now().minusSeconds(5));
        when(apiKeys.findByKeyPrefix("mcpk_expired")).thenReturn(Optional.of(expired));
        mockMvc.perform(get("/api/weather/status").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());

        AppUser disabledUser = new AppUser("api-disabled", passwordEncoder.encode("password for disabled"), UserRole.MEMBER);
        disabledUser.setEnabled(false);
        ApiKey disabled = new ApiKey(disabledUser, "disabled", "mcpk_disabled", "", null);
        String disabledToken = "mcpk_disabled.secret";
        ReflectionTestUtils.setField(disabled, "secretHash", com.mcp_gateway_k8s_store.backend.service.ApiKeyService.sha256(disabledToken));
        when(apiKeys.findByKeyPrefix("mcpk_disabled")).thenReturn(Optional.of(disabled));
        mockMvc.perform(get("/api/weather/status").header("Authorization", "Bearer " + disabledToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mcpEndpointRequiresAuthenticationAndAcceptsApiKeyOnEveryRequest() throws Exception {
        mockMvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"))
                .andExpect(status().isUnauthorized());
        String token = "mcpk_test-client.secret-value";
        ApiKey key = new ApiKey(admin, "MCP test client", "mcpk_test-client",
                com.mcp_gateway_k8s_store.backend.service.ApiKeyService.sha256(token), null);
        when(apiKeys.findByKeyPrefix("mcpk_test-client")).thenReturn(Optional.of(key));
        mockMvc.perform(post("/mcp").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test-client\",\"version\":\"1.0\"}}}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/mcp").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\",\"params\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.tools[?(@.name == 'gateway_list_tools')]").exists());
    }

    @Test
    void mcpToolCallsAreAuditedWithApiKeyOwnerAndFailuresUseSanitizedDetails() throws Exception {
        String token = "mcpk_audit-client.secret-value";
        ApiKey key = new ApiKey(admin, "Audit test client", "mcpk_audit-client",
                com.mcp_gateway_k8s_store.backend.service.ApiKeyService.sha256(token), null);
        when(apiKeys.findByKeyPrefix("mcpk_audit-client")).thenReturn(Optional.of(key));

        mockMvc.perform(post("/mcp").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":21,\"method\":\"tools/call\",\"params\":{\"name\":\"gateway_status\",\"arguments\":{}}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.isError").value(false));

        mockMvc.perform(post("/mcp").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":22,\"method\":\"tools/call\",\"params\":{\"name\":\"weather_get_current\",\"arguments\":{\"location\":\"secret-input-that-must-not-be-logged\"}}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.isError").value(true));

        org.mockito.ArgumentCaptor<AuditEvent> events = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEvents, org.mockito.Mockito.atLeast(2)).saveAndFlush(events.capture());
        List<AuditEvent> auditRows = events.getAllValues();
        org.junit.jupiter.api.Assertions.assertTrue(auditRows.stream().anyMatch(event -> "admin-id".equals(event.getActorId())
                && "gateway_status".equals(event.getToolName()) && event.getOutcome() == com.mcp_gateway_k8s_store.backend.model.AuditOutcome.SUCCESS
                && event.getDurationMs() != null));
        org.junit.jupiter.api.Assertions.assertTrue(auditRows.stream().anyMatch(event -> "weather_get_current".equals(event.getToolName())
                && event.getOutcome() == com.mcp_gateway_k8s_store.backend.model.AuditOutcome.FAILURE));
        org.junit.jupiter.api.Assertions.assertTrue(auditRows.stream().noneMatch(event -> String.valueOf(event.getDescription()).contains("secret-input-that-must-not-be-logged")));
    }

    @Test
    void mcpMutationToolsEnforceAdminRole() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "member-test", "", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MEMBER"))));
        try {
            assertThrows(AccessDeniedException.class, () -> mcpGatewayTools.createProduct("Denied", "10.00", null, null));
        } finally {
            SecurityContextHolder.clearContext();
        }
        mockMvc.perform(post("/mcp").header("Authorization", "Bearer " + jwtTokens.issue(member))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"woocommerce_create_product\",\"arguments\":{\"name\":\"Denied\",\"regularPrice\":\"10.00\"}}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.isError").value(true));
    }

    @Test
    void adminCanCreateInvitationAndMemberCannotManageInvitations() throws Exception {
        mockMvc.perform(post("/api/team/invitations").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/team/invitations").header("Authorization", "Bearer " + jwtTokens.issue(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/team/invitations").header("Authorization", "Bearer " + jwtTokens.issue(member))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/team/invitations/some-id").header("Authorization", "Bearer " + jwtTokens.issue(member))).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/team/invitations").header("Authorization", "Bearer " + jwtTokens.issue(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.inviteUrl").value(org.hamcrest.Matchers.containsString("accept-invitation?token=")))
                .andExpect(jsonPath("$.tokenHash").doesNotExist());
        verify(invitations).saveAndFlush(any(TeamInvitation.class));
    }

    @Test
    void invitationAcceptanceCreatesMemberWithHashedPasswordAndTokenIsSingleUse() throws Exception {
        TeamInvitation invitation = new TeamInvitation("invitee@example.com", "sha256-placeholder", admin, Instant.now().plusSeconds(3600));
        when(invitations.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(invitation));
        AppUser created = new AppUser("invitee", "invitee@example.com", passwordEncoder.encode("a sufficiently strong password"), UserRole.MEMBER);
        ReflectionTestUtils.setField(created, "id", "invitee-id");
        when(users.saveAndFlush(any(AppUser.class))).thenReturn(created);

        mockMvc.perform(post("/api/auth/accept-invitation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"single-use-token-value\",\"username\":\"invitee\",\"password\":\"a sufficiently strong password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.email").value("invitee@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(users).saveAndFlush(org.mockito.ArgumentMatchers.argThat(user -> user.getRole() == UserRole.MEMBER
                && user.getEmail().equals("invitee@example.com") && passwordEncoder.matches("a sufficiently strong password", user.getPasswordHash())));
        verify(invitations).save(invitation);

        when(users.findByUsernameIgnoreCase("invitee")).thenReturn(Optional.of(created));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"invitee\",\"password\":\"a sufficiently strong password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("MEMBER"));
        mockMvc.perform(post("/api/auth/accept-invitation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"single-use-token-value\",\"username\":\"invitee2\",\"password\":\"a sufficiently strong password\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidExpiredAndRevokedInvitationsAreRejected() throws Exception {
        mockMvc.perform(post("/api/auth/accept-invitation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"unknown-token\",\"username\":\"invitee\",\"password\":\"a sufficiently strong password\"}"))
                .andExpect(status().isBadRequest());
        TeamInvitation expired = new TeamInvitation("expired@example.com", "hash", admin, Instant.now().minusSeconds(10));
        when(invitations.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(expired));
        mockMvc.perform(post("/api/auth/accept-invitation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"expired-token\",\"username\":\"invitee\",\"password\":\"a sufficiently strong password\"}"))
                .andExpect(status().isBadRequest());
        TeamInvitation revoked = new TeamInvitation("revoked@example.com", "hash", admin, Instant.now().plusSeconds(3600));
        revoked.revoke(Instant.now());
        when(invitations.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(revoked));
        mockMvc.perform(post("/api/auth/accept-invitation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"revoked-token\",\"username\":\"invitee\",\"password\":\"a sufficiently strong password\"}"))
                .andExpect(status().isBadRequest());
    }
}

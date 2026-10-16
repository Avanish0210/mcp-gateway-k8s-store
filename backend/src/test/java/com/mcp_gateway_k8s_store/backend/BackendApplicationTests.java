package com.mcp_gateway_k8s_store.backend;

import com.mcp_gateway_k8s_store.backend.model.AppUser;
import com.mcp_gateway_k8s_store.backend.model.UserRole;
import com.mcp_gateway_k8s_store.backend.model.StoreStatus;
import com.mcp_gateway_k8s_store.backend.repository.AppUserRepository;
import com.mcp_gateway_k8s_store.backend.repository.StoreRepository;
import com.mcp_gateway_k8s_store.backend.service.JwtTokenService;
import com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "security.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "spring.ai.mcp.client.enabled=false",
        "stores.provisioning.enabled=true"
})
@AutoConfigureMockMvc
class BackendApplicationTests {
    @Autowired private WooCommerceMcpService wooCommerceMcpService;
    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenService jwtTokens;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private AppUserRepository users;
    @MockitoBean private StoreRepository stores;

    private AppUser admin;
    private AppUser member;

    @BeforeEach
    void setUpAccounts() {
        admin = new AppUser("admin-test", passwordEncoder.encode("correct horse battery"), UserRole.ADMIN);
        member = new AppUser("member-test", passwordEncoder.encode("member password safe"), UserRole.MEMBER);
        when(users.findByUsernameIgnoreCase("admin-test")).thenReturn(Optional.of(admin));
        when(users.findByUsernameIgnoreCase("member-test")).thenReturn(Optional.of(member));
        when(stores.existsByNamespaceName(anyString())).thenReturn(false);
        when(stores.findAll()).thenReturn(List.of());
        when(stores.findByStatusIn(org.mockito.ArgumentMatchers.<java.util.Collection<StoreStatus>>any())).thenReturn(List.of());
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
}

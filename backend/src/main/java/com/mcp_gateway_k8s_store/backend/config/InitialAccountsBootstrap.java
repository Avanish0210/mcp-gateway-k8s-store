package com.mcp_gateway_k8s_store.backend.config;

import com.mcp_gateway_k8s_store.backend.model.AppUser;
import com.mcp_gateway_k8s_store.backend.model.UserRole;
import com.mcp_gateway_k8s_store.backend.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class InitialAccountsBootstrap implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String memberUsername;
    private final String memberPassword;

    public InitialAccountsBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder,
                                    @Value("${security.bootstrap.admin-username:}") String adminUsername,
                                    @Value("${security.bootstrap.admin-password:}") String adminPassword,
                                    @Value("${security.bootstrap.member-username:}") String memberUsername,
                                    @Value("${security.bootstrap.member-password:}") String memberPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.memberUsername = memberUsername;
        this.memberPassword = memberPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfConfigured(adminUsername, adminPassword, UserRole.ADMIN);
        createIfConfigured(memberUsername, memberPassword, UserRole.MEMBER);
    }

    private void createIfConfigured(String username, String password, UserRole role) {
        boolean hasUsername = username != null && !username.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        if (!hasUsername && !hasPassword) return;
        if (hasUsername != hasPassword) throw new IllegalStateException("Both initial account username and password must be configured");
        String normalized = username.trim();
        if (normalized.length() < 3 || normalized.length() > 80 || normalized.chars().anyMatch(Character::isISOControl))
            throw new IllegalStateException("Initial account username must be 3 to 80 printable characters");
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        // Keep the existing local Compose credentials usable. BCrypt truncates
        // inputs beyond 72 bytes, so reject those while requiring at least 8.
        if (passwordBytes < 8 || passwordBytes > 72)
            throw new IllegalStateException("Initial account password must be 8 to 72 UTF-8 bytes");
        if (!users.existsByUsernameIgnoreCase(normalized))
            users.save(new AppUser(normalized, passwordEncoder.encode(password), role));
    }
}

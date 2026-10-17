package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.dto.ApiKeyResponse;
import com.mcp_gateway_k8s_store.backend.dto.CreateApiKeyRequest;
import com.mcp_gateway_k8s_store.backend.dto.CreatedApiKeyResponse;
import com.mcp_gateway_k8s_store.backend.model.ApiKey;
import com.mcp_gateway_k8s_store.backend.model.AppUser;
import com.mcp_gateway_k8s_store.backend.repository.ApiKeyRepository;
import com.mcp_gateway_k8s_store.backend.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class ApiKeyService {
    private static final String TOKEN_PREFIX = "mcpk_";
    private final ApiKeyRepository keys;
    private final AppUserRepository users;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository keys, AppUserRepository users) {
        this.keys = keys;
        this.users = users;
    }

    @Transactional
    public CreatedApiKeyResponse create(String username, CreateApiKeyRequest request) {
        AppUser owner = requireEnabledUser(username);
        String name = request.name().trim();
        if (name.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Key name is required");
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiration must be in the future");

        String identifier = randomUrlSafe(9);
        String prefix = TOKEN_PREFIX + identifier;
        String completeKey = prefix + "." + randomUrlSafe(32);
        ApiKey saved = keys.save(new ApiKey(owner, name, prefix, sha256(completeKey), request.expiresAt()));
        return new CreatedApiKeyResponse(ApiKeyResponse.from(saved), completeKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(String username) {
        AppUser owner = requireEnabledUser(username);
        return keys.findAllByOwner_IdOrderByCreatedAtDesc(owner.getId()).stream().map(ApiKeyResponse::from).toList();
    }

    @Transactional
    public void revoke(String username, String id) {
        AppUser owner = requireEnabledUser(username);
        ApiKey key = keys.findByIdAndOwner_Id(id, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));
        key.revoke(Instant.now());
        keys.save(key);
    }

    @Transactional
    public AppUser authenticate(String completeKey) {
        if (completeKey == null) throw invalidKey();
        int separator = completeKey.indexOf('.');
        if (separator <= TOKEN_PREFIX.length() || !completeKey.startsWith(TOKEN_PREFIX)
                || separator != completeKey.lastIndexOf('.') || separator == completeKey.length() - 1) throw invalidKey();
        String prefix = completeKey.substring(0, separator);
        ApiKey key = keys.findByKeyPrefix(prefix).orElseThrow(ApiKeyService::invalidKey);
        Instant now = Instant.now();
        if (key.isRevoked() || (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(now))) throw invalidKey();
        byte[] expected = key.getSecretHash().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = sha256(completeKey).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) throw invalidKey();
        AppUser owner = key.getOwner();
        if (owner == null || !owner.isEnabled()) throw invalidKey();
        key.setLastUsedAt(now);
        keys.save(key);
        return owner;
    }

    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }

    private AppUser requireEnabledUser(String username) {
        return users.findByUsernameIgnoreCase(username).filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    private String randomUrlSafe(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static BadCredentialsException invalidKey() { return new BadCredentialsException("Invalid API key"); }
}

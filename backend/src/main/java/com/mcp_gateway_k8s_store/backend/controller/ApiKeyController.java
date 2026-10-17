package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.dto.ApiKeyResponse;
import com.mcp_gateway_k8s_store.backend.dto.CreateApiKeyRequest;
import com.mcp_gateway_k8s_store.backend.dto.CreatedApiKeyResponse;
import com.mcp_gateway_k8s_store.backend.service.ApiKeyService;
import com.mcp_gateway_k8s_store.backend.service.AuditService;
import com.mcp_gateway_k8s_store.backend.model.AuditOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/keys")
public class ApiKeyController {
    private final ApiKeyService keys;
    private final AuditService audit;

    public ApiKeyController(ApiKeyService keys, AuditService audit) { this.keys = keys; this.audit = audit; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedApiKeyResponse create(Authentication authentication, @Valid @RequestBody CreateApiKeyRequest request) {
        try {
            CreatedApiKeyResponse created = keys.create(authentication.getName(), request);
            audit.recordCurrent("API_KEY_CREATED", null, null, created.key().id(), AuditOutcome.SUCCESS, null, null, "API key created");
            return created;
        } catch (RuntimeException failure) {
            audit.recordCurrent("API_KEY_CREATED", null, null, null, AuditOutcome.FAILURE, null, failure, null);
            throw failure;
        }
    }

    @GetMapping
    public List<ApiKeyResponse> list(Authentication authentication) { return keys.list(authentication.getName()); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication authentication, @PathVariable String id) {
        try {
            keys.revoke(authentication.getName(), id);
            audit.recordCurrent("API_KEY_REVOKED", null, null, id, AuditOutcome.SUCCESS, null, null, "API key revoked");
        } catch (RuntimeException failure) {
            audit.recordCurrent("API_KEY_REVOKED", null, null, id, AuditOutcome.FAILURE, null, failure, null);
            throw failure;
        }
    }
}

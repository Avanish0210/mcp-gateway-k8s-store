package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.dto.AuditEventResponse;
import com.mcp_gateway_k8s_store.backend.model.*;
import com.mcp_gateway_k8s_store.backend.repository.AuditEventRepository;
import com.mcp_gateway_k8s_store.backend.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditEventRepository events;
    private final AppUserRepository users;

    public AuditService(AuditEventRepository events, AppUserRepository users) { this.events = events; this.users = users; }

    @Transactional
    public void recordCurrent(String eventType, String serverId, String toolName, String storeId,
                              AuditOutcome outcome, Long durationMs, Throwable failure, String description) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()) ? null : auth.getName();
        String userId = username == null ? null : users.findByUsernameIgnoreCase(username).map(AppUser::getId).orElse(null);
        persist(userId, username, eventType, serverId, toolName, storeId, outcome, durationMs, failure, description);
    }

    @Transactional
    public void recordForUser(String userId, String username, String eventType, String target,
                              AuditOutcome outcome, String description) {
        persist(userId, username, eventType, null, null, target, outcome, null, null, description);
    }

    private void persist(String userId, String username, String eventType, String serverId, String toolName,
                         String storeId, AuditOutcome outcome, Long durationMs, Throwable failure, String description) {
        String category = failure == null ? null : failure.getClass().getSimpleName();
        String safeDescription = failure == null ? clean(description, 500) : "Operation failed (" + category + ")";
        events.saveAndFlush(new AuditEvent(userId, clean(username, 80), clean(eventType, 80), clean(serverId, 80),
                clean(toolName, 120), clean(storeId, 120), outcome, durationMs, clean(category, 80),
                safeDescription, correlationId()));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> global(String actorId, String eventType, AuditOutcome outcome, String serverId,
            String toolName, String storeId, Instant from, Instant to, int page, int size) {
        return query(null, actorId, eventType, outcome, serverId, toolName, storeId, from, to, page, size);
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> mine(String username, String eventType, AuditOutcome outcome, String serverId,
            String toolName, String storeId, Instant from, Instant to, int page, int size) {
        String actorId = users.findByUsernameIgnoreCase(username).map(AppUser::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        return query(actorId, null, eventType, outcome, serverId, toolName, storeId, from, to, page, size);
    }

    private Page<AuditEventResponse> query(String enforcedActor, String actorId, String eventType, AuditOutcome outcome,
            String serverId, String toolName, String storeId, Instant from, Instant to, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid audit pagination or time range");
        Specification<AuditEvent> spec = (root, q, cb) -> cb.conjunction();
        String effectiveActor = enforcedActor != null ? enforcedActor : actorId;
        if (effectiveActor != null) spec = spec.and((r,q,cb) -> cb.equal(r.get("actorId"), effectiveActor));
        if (eventType != null && !eventType.isBlank()) spec = spec.and((r,q,cb) -> cb.equal(r.get("eventType"), eventType));
        if (outcome != null) spec = spec.and((r,q,cb) -> cb.equal(r.get("outcome"), outcome));
        if (serverId != null && !serverId.isBlank()) spec = spec.and((r,q,cb) -> cb.equal(r.get("serverId"), serverId));
        if (toolName != null && !toolName.isBlank()) spec = spec.and((r,q,cb) -> cb.equal(r.get("toolName"), toolName));
        if (storeId != null && !storeId.isBlank()) spec = spec.and((r,q,cb) -> cb.equal(r.get("storeId"), storeId));
        if (from != null) spec = spec.and((r,q,cb) -> cb.greaterThanOrEqualTo(r.get("occurredAt"), from));
        if (to != null) spec = spec.and((r,q,cb) -> cb.lessThanOrEqualTo(r.get("occurredAt"), to));
        return events.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(AuditEventResponse::from);
    }

    private String correlationId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String supplied = attrs.getRequest().getHeader("X-Request-ID");
            if (supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,100}")) return supplied;
        }
        return UUID.randomUUID().toString();
    }
    private String clean(String value, int max) {
        if (value == null) return null;
        String safe = value.replaceAll("[\\r\\n\\p{Cntrl}]", "_").trim();
        return safe.length() > max ? safe.substring(0, max) : safe;
    }
}

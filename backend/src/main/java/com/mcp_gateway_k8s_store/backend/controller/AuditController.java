package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.dto.AuditEventResponse;
import com.mcp_gateway_k8s_store.backend.dto.AuditPageResponse;
import com.mcp_gateway_k8s_store.backend.model.AuditOutcome;
import com.mcp_gateway_k8s_store.backend.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit) { this.audit = audit; }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AuditPageResponse global(@RequestParam(required=false) String actorId,
            @RequestParam(required=false) String eventType, @RequestParam(required=false) AuditOutcome outcome,
            @RequestParam(required=false) String serverId, @RequestParam(required=false) String toolName,
            @RequestParam(required=false) String storeId, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size) {
        return AuditPageResponse.from(audit.global(actorId,eventType,outcome,serverId,toolName,storeId,from,to,page,size));
    }

    @GetMapping("/me")
    public AuditPageResponse mine(java.security.Principal principal,
            @RequestParam(required=false) String eventType, @RequestParam(required=false) AuditOutcome outcome,
            @RequestParam(required=false) String serverId, @RequestParam(required=false) String toolName,
            @RequestParam(required=false) String storeId, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size) {
        return AuditPageResponse.from(audit.mine(principal.getName(),eventType,outcome,serverId,toolName,storeId,from,to,page,size));
    }
}

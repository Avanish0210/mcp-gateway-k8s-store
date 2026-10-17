package com.mcp_gateway_k8s_store.backend.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record AuditPageResponse(List<AuditEventResponse> content, int number, int size,
                                long totalElements, int totalPages, boolean first, boolean last) {
    public static AuditPageResponse from(Page<AuditEventResponse> page) {
        return new AuditPageResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }
}

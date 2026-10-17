package com.mcp_gateway_k8s_store.backend.repository;

import com.mcp_gateway_k8s_store.backend.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String>, JpaSpecificationExecutor<AuditEvent> { }

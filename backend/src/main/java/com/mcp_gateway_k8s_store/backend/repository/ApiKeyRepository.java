package com.mcp_gateway_k8s_store.backend.repository;

import com.mcp_gateway_k8s_store.backend.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {
    Optional<ApiKey> findByKeyPrefix(String keyPrefix);
    List<ApiKey> findAllByOwner_IdOrderByCreatedAtDesc(String ownerId);
    Optional<ApiKey> findByIdAndOwner_Id(String id, String ownerId);
}

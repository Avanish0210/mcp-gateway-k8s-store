package com.mcp_gateway_k8s_store.backend.repository;

import com.mcp_gateway_k8s_store.backend.model.Store;
import com.mcp_gateway_k8s_store.backend.model.StoreStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store, String> {

    boolean existsByNamespaceName(String namespaceName);

    boolean existsByHelmRelease(String helmRelease);

    List<Store> findByStatusIn(Collection<StoreStatus> statuses);

    long countByStatusNot(StoreStatus status);
}

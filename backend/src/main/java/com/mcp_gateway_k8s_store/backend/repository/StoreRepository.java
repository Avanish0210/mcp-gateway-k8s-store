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

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Store s set s.status = :status, s.errorMessage = :error, s.storeUrl = :url where s.id = :id and s.status in :expected")
    int updateHealth(@org.springframework.data.repository.query.Param("id") String id,
                     @org.springframework.data.repository.query.Param("status") StoreStatus status,
                     @org.springframework.data.repository.query.Param("error") String error,
                     @org.springframework.data.repository.query.Param("url") String url,
                     @org.springframework.data.repository.query.Param("expected") Collection<StoreStatus> expected);
}

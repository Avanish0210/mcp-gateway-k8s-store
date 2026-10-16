package com.mcp_gateway_k8s_store.backend.model;


import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "managed_stores",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_managed_stores_namespace", columnNames = "namespace_name"),
                @UniqueConstraint(name = "uk_managed_stores_release", columnNames = "helm_release")
        }
)
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "namespace_name", nullable = false, unique = true, length = 63)
    private String namespaceName;

    @Column(name = "helm_release", nullable = false, unique = true, length = 53)
    private String helmRelease;

    @Column(name = "store_url", length = 255)
    private String storeUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Store() {
    }

    public Store(String name, String namespaceName, String helmRelease, StoreStatus status) {
        this.name = name;
        this.namespaceName = namespaceName;
        this.helmRelease = helmRelease;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public String getHelmRelease() {
        return helmRelease;
    }

    public String getStoreUrl() {
        return storeUrl;
    }

    public StoreStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStoreUrl(String storeUrl) {
        this.storeUrl = storeUrl;
    }

    public void setStatus(StoreStatus status) {
        this.status = status;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}


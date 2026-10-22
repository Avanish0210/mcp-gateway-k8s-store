package com.mcp_gateway_k8s_store.backend.model;

public enum StoreStatus {
    PROVISIONING,
    READY,
    RECOVERING,
    FAILED,
    DELETING
}

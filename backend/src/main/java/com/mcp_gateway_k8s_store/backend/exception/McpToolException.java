package com.mcp_gateway_k8s_store.backend.exception;

public class McpToolException extends RuntimeException {
    public McpToolException(String message) {
        super(message);
    }

    public McpToolException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.mcp_gateway_k8s_store.backend.service;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WooCommerceMcpService {

    private final List<McpSyncClient> mcpClients;

    public WooCommerceMcpService(List<McpSyncClient> mcpClients) {
        this.mcpClients = mcpClients;
    }

    public List<String> getAvailableTools() {
        return mcpClients.stream()
                .flatMap(client ->
                        client.listTools()
                                .tools()
                                .stream()
                )
                .map(tool -> tool.name())
                .distinct()
                .toList();
    }
}

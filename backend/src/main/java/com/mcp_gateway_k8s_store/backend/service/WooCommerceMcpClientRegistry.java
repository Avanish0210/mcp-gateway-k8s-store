package com.mcp_gateway_k8s_store.backend.service;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import com.mcp_gateway_k8s_store.backend.model.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Owns isolated per-store MCP processes and never mixes their routing. */
@Service
public class WooCommerceMcpClientRegistry implements AutoCloseable {
    private static final Set<String> REQUIRED_TOOLS = Set.of("list_products", "get_product", "list_orders", "get_order", "list_customers", "get_customer", "create_product", "update_order_status");
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
    private final String nodeCommand;
    private final String serverPath;

    public WooCommerceMcpClientRegistry(
            @Value("${MCP_NODE_COMMAND:node}") String nodeCommand,
            @Value("${MCP_SERVER_PATH:../mcp-servers/woocommerce/dist/server.js}") String serverPath) {
        this.nodeCommand = nodeCommand; this.serverPath = Path.of(serverPath).toAbsolutePath().normalize().toString();
    }

    public synchronized void register(Store store, String url, String key, String secret) {
        McpSyncClient existing = clients.get(store.getId());
        if (existing != null && existing.isInitialized()) return;
        if (existing != null) { existing.close(); clients.remove(store.getId()); }
        StdioClientTransport transport = new StdioClientTransport(
                ServerParameters.builder(nodeCommand).args(serverPath).env(Map.of(
                        "WOOCOMMERCE_URL", url, "WOOCOMMERCE_CONSUMER_KEY", key, "WOOCOMMERCE_CONSUMER_SECRET", secret))
                        .build(), new JacksonMcpJsonMapper(JsonMapper.builder().build()));
        transport.setStdErrorHandler(line -> { });
        McpSyncClient client = McpClient.sync(transport).initializationTimeout(Duration.ofSeconds(20))
                .requestTimeout(Duration.ofSeconds(15)).build();
        try {
            client.initialize();
            Set<String> available = client.listTools().tools().stream().map(McpSchema.Tool::name).collect(java.util.stream.Collectors.toSet());
            if (!client.isInitialized() || !available.containsAll(REQUIRED_TOOLS)) throw new IllegalStateException("Store WooCommerce MCP did not expose the required tools");
            McpSchema.CallToolResult health = client.callTool(new McpSchema.CallToolRequest("list_products", Map.of("page", 1, "perPage", 1, "search", "")));
            if (Boolean.TRUE.equals(health.isError())) throw new IllegalStateException("WooCommerce API connectivity check failed");
            clients.put(store.getId(), client);
        } catch (RuntimeException failure) { client.close(); throw new IllegalStateException("Store-specific WooCommerce MCP initialization failed", failure); }
    }

    public McpSyncClient require(String storeId) {
        McpSyncClient client = clients.get(storeId);
        if (client == null || !client.isInitialized()) throw new IllegalStateException("Store WooCommerce MCP is not initialized");
        return client;
    }
    public java.util.List<String> tools(String storeId) { return require(storeId).listTools().tools().stream().map(McpSchema.Tool::name).sorted().toList(); }
    public void unregister(String storeId) { McpSyncClient client = clients.remove(storeId); if (client != null) client.close(); }
    @Override public void close() { clients.keySet().forEach(this::unregister); }

}

package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.exception.McpToolException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class WooCommerceMcpService {
    private static final java.util.Set<String> WOO_TOOLS = java.util.Set.of("list_products", "get_product", "list_orders", "get_order", "list_customers", "get_customer", "create_product", "update_order_status");

    private final List<McpSyncClient> mcpClients;
    private final ObjectMapper objectMapper;
    private final WooCommerceMcpClientRegistry storeClients;

    public WooCommerceMcpService(List<McpSyncClient> mcpClients, ObjectMapper objectMapper) {
        this(mcpClients, objectMapper, null);
    }

    @Autowired
    public WooCommerceMcpService(List<McpSyncClient> mcpClients, ObjectMapper objectMapper, WooCommerceMcpClientRegistry storeClients) {
        this.mcpClients = List.copyOf(mcpClients);
        this.objectMapper = objectMapper;
        this.storeClients = storeClients;
    }

    public List<String> getAvailableTools() {
        if (storeClients != null && hasDefaultSession()) return storeClients.tools("store-1");
        return mcpClients.stream()
                .filter(McpSyncClient::isInitialized)
                .flatMap(client -> client.listTools().tools().stream())
                .map(McpSchema.Tool::name)
                .filter(WOO_TOOLS::contains)
                .distinct()
                .sorted()
                .toList();
    }
    public List<String> getAvailableTools(String storeId) {
        if (storeClients == null) throw new McpToolException("Store-specific WooCommerce MCP is unavailable.");
        return storeClients.tools(storeId);
    }

    public Map<String, Object> getConnectionStatus() {
        boolean available;
        try { listProducts(1, 1, ""); available = true; }
        catch (RuntimeException unavailable) { available = false; }
        return Map.of(
                "configuredClients", 1,
                "initializedClients", available ? 1 : 0,
                "available", available
        );
    }
    public Map<String, Object> getConnectionStatus(String storeId) {
        if (storeId == null || storeId.isBlank() || "store-1".equals(storeId)) return getConnectionStatus();
        boolean available = storeClients != null && storeClients.healthy(storeId);
        return Map.of("configuredClients", 1, "initializedClients", available ? 1 : 0, "available", available);
    }

    public Object listProducts(int page, int perPage, String search) {
        return invoke("list_products", Map.of("page", page, "perPage", perPage,
                "search", search == null ? "" : search));
    }
    public Object listProducts(String storeId, int page, int perPage, String search) { return invoke(storeId, "list_products", Map.of("page", page, "perPage", perPage, "search", search == null ? "" : search)); }

    public Object getProduct(long productId) {
        return invoke("get_product", Map.of("productId", productId));
    }
    public Object getProduct(String storeId, long productId) { return invoke(storeId, "get_product", Map.of("productId", productId)); }

    public Object createProduct(String name, String regularPrice, String description, String type) {
        return invoke("create_product", Map.of(
                "name", name,
                "regularPrice", regularPrice,
                "description", description == null ? "" : description,
                "type", type == null ? "simple" : type
        ));
    }
    public Object createProduct(String storeId, String name, String regularPrice, String description, String type) { return invoke(storeId, "create_product", Map.of("name", name, "regularPrice", regularPrice, "description", description == null ? "" : description, "type", type == null ? "simple" : type)); }

    public Object listOrders(int page, int perPage, String status) {
        return invoke("list_orders", Map.of("page", page, "perPage", perPage,
                "status", status == null ? "" : status));
    }
    public Object listOrders(String storeId, int page, int perPage, String status) { return invoke(storeId, "list_orders", Map.of("page", page, "perPage", perPage, "status", status == null ? "" : status)); }

    public Object getOrder(long orderId) {
        return invoke("get_order", Map.of("orderId", orderId));
    }
    public Object getOrder(String storeId, long orderId) { return invoke(storeId, "get_order", Map.of("orderId", orderId)); }

    public Object updateOrderStatus(long orderId, String status) {
        return invoke("update_order_status", Map.of("orderId", orderId, "status", status));
    }
    public Object updateOrderStatus(String storeId, long orderId, String status) { return invoke(storeId, "update_order_status", Map.of("orderId", orderId, "status", status)); }

    public Object listCustomers(int page, int perPage) {
        return invoke("list_customers", Map.of("page", page, "perPage", perPage));
    }
    public Object listCustomers(String storeId, int page, int perPage) { return invoke(storeId, "list_customers", Map.of("page", page, "perPage", perPage)); }

    public Object getCustomer(long customerId) {
        return invoke("get_customer", Map.of("customerId", customerId));
    }
    public Object getCustomer(String storeId, long customerId) { return invoke(storeId, "get_customer", Map.of("customerId", customerId)); }

    private Object invoke(String toolName, Map<String, Object> arguments) {
        return invoke(null, toolName, arguments);
    }

    private Object invoke(String storeId, String toolName, Map<String, Object> arguments) {
        McpSyncClient client;
        if (storeId != null) {
            if (storeClients == null) throw new McpToolException("Store-specific WooCommerce MCP is unavailable.");
            try { client = storeClients.require(storeId); }
            catch (IllegalStateException e) { throw new McpToolException(e.getMessage(), e); }
        } else if (storeClients != null && hasDefaultSession()) client = storeClients.require("store-1");
        else client = mcpClients.stream()
                .filter(McpSyncClient::isInitialized)
                .filter(candidate -> candidate.listTools().tools().stream()
                        .anyMatch(tool -> toolName.equals(tool.name())))
                .findFirst()
                .orElseThrow(() -> new McpToolException("WooCommerce MCP tool is unavailable: " + toolName));
        if (client == null) throw new McpToolException("Store-specific WooCommerce MCP is unavailable.");

        try {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
            if (Boolean.TRUE.equals(result.isError())) {
                String message = result.content().stream()
                        .filter(McpSchema.TextContent.class::isInstance)
                        .map(McpSchema.TextContent.class::cast)
                        .map(McpSchema.TextContent::text)
                        .findFirst().orElse("WooCommerce MCP tool call failed.");
                throw new McpToolException(message);
            }
            if (result.structuredContent() != null) {
                return result.structuredContent();
            }
            String text = result.content().stream()
                    .filter(McpSchema.TextContent.class::isInstance)
                    .map(McpSchema.TextContent.class::cast)
                    .map(McpSchema.TextContent::text)
                    .findFirst()
                    .orElse("null");
            return objectMapper.readValue(text, Object.class);
        } catch (McpToolException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new McpToolException("WooCommerce MCP returned an unreadable response.", exception);
        } catch (RuntimeException exception) {
            throw new McpToolException("WooCommerce MCP request failed.", exception);
        }
    }

    private boolean hasDefaultSession() {
        try { storeClients.require("store-1"); return true; }
        catch (IllegalStateException absent) { return false; }
    }
}

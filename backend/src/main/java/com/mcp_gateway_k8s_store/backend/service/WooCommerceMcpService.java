package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.exception.McpToolException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class WooCommerceMcpService {

    private final List<McpSyncClient> mcpClients;
    private final ObjectMapper objectMapper;

    public WooCommerceMcpService(List<McpSyncClient> mcpClients, ObjectMapper objectMapper) {
        this.mcpClients = List.copyOf(mcpClients);
        this.objectMapper = objectMapper;
    }

    public List<String> getAvailableTools() {
        return mcpClients.stream()
                .filter(McpSyncClient::isInitialized)
                .flatMap(client -> client.listTools().tools().stream())
                .map(McpSchema.Tool::name)
                .distinct()
                .sorted()
                .toList();
    }

    public Map<String, Object> getConnectionStatus() {
        return Map.of(
                "configuredClients", mcpClients.size(),
                "initializedClients", mcpClients.stream().filter(McpSyncClient::isInitialized).count(),
                "available", mcpClients.stream().anyMatch(McpSyncClient::isInitialized)
        );
    }

    public Object listProducts(int page, int perPage, String search) {
        return invoke("list_products", Map.of("page", page, "perPage", perPage,
                "search", search == null ? "" : search));
    }

    public Object getProduct(long productId) {
        return invoke("get_product", Map.of("productId", productId));
    }

    public Object createProduct(String name, String regularPrice, String description, String type) {
        return invoke("create_product", Map.of(
                "name", name,
                "regularPrice", regularPrice,
                "description", description == null ? "" : description,
                "type", type == null ? "simple" : type
        ));
    }

    public Object listOrders(int page, int perPage, String status) {
        return invoke("list_orders", Map.of("page", page, "perPage", perPage,
                "status", status == null ? "" : status));
    }

    public Object getOrder(long orderId) {
        return invoke("get_order", Map.of("orderId", orderId));
    }

    public Object updateOrderStatus(long orderId, String status) {
        return invoke("update_order_status", Map.of("orderId", orderId, "status", status));
    }

    public Object listCustomers(int page, int perPage) {
        return invoke("list_customers", Map.of("page", page, "perPage", perPage));
    }

    public Object getCustomer(long customerId) {
        return invoke("get_customer", Map.of("customerId", customerId));
    }

    private Object invoke(String toolName, Map<String, Object> arguments) {
        McpSyncClient client = mcpClients.stream()
                .filter(McpSyncClient::isInitialized)
                .filter(candidate -> candidate.listTools().tools().stream()
                        .anyMatch(tool -> toolName.equals(tool.name())))
                .findFirst()
                .orElseThrow(() -> new McpToolException("WooCommerce MCP tool is unavailable: " + toolName));

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
}

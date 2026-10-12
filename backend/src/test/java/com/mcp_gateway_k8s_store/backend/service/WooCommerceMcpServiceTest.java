package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.exception.McpToolException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WooCommerceMcpServiceTest {
    private McpSyncClient client;
    private WooCommerceMcpService service;

    @BeforeEach
    void setUp() {
        client = mock(McpSyncClient.class);
        when(client.isInitialized()).thenReturn(true);
        service = new WooCommerceMcpService(List.of(client), new ObjectMapper());
    }

    @Test
    void discoversToolsFromConnectedServer() {
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(
                List.of(tool("get_product"), tool("list_products")), null));

        assertEquals(List.of("get_product", "list_products"), service.getAvailableTools());
    }

    @Test
    void mapsProductListArgumentsAndDecodesJsonResult() {
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool("list_products")), null));
        when(client.callTool(any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("[{\"id\":7}]")), false, null, Map.of()));

        Object result = service.listProducts(2, 25, "mug");

        ArgumentCaptor<McpSchema.CallToolRequest> request = ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(client).callTool(request.capture());
        assertEquals("list_products", request.getValue().name());
        assertEquals(Map.of("page", 2, "perPage", 25, "search", "mug"), request.getValue().arguments());
        assertEquals(List.of(Map.of("id", 7)), result);
    }

    @Test
    void refusesToolsNotRegisteredByTheMcpServer() {
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool("get_product")), null));

        McpToolException exception = assertThrows(McpToolException.class,
                () -> service.listProducts(1, 10, null));

        assertTrue(exception.getMessage().contains("list_products"));
    }

    private McpSchema.Tool tool(String name) {
        return McpSchema.Tool.builder(name, Map.of()).build();
    }
}

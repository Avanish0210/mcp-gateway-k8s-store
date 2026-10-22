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

    @Test
    void storeScopedCallsUseOnlyTheSelectedStoreClient() {
        McpSyncClient storeTwo = mock(McpSyncClient.class);
        McpSyncClient storeThree = mock(McpSyncClient.class);
        com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpClientRegistry registry = mock(com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpClientRegistry.class);
        when(registry.require("store-two")).thenReturn(storeTwo);
        when(registry.require("store-three")).thenReturn(storeThree);
        when(storeTwo.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool("list_products")), null));
        when(storeThree.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool("list_products")), null));
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("[]")), false, null, Map.of());
        when(storeTwo.callTool(any())).thenReturn(result);
        when(storeThree.callTool(any())).thenReturn(result);
        WooCommerceMcpService scoped = new WooCommerceMcpService(List.of(client), new ObjectMapper(), registry);

        scoped.listProducts("store-two", 1, 10, "");
        scoped.listProducts("store-three", 1, 10, "");

        verify(storeTwo).callTool(any());
        verify(storeThree).callTool(any());
        verify(client, org.mockito.Mockito.never()).callTool(any());
    }

    private McpSchema.Tool tool(String name) {
        return McpSchema.Tool.builder(name, Map.of()).build();
    }

    @Test void weatherSessionDoesNotMakeWooCommerceAvailable() {
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool("get_current_weather")), null));
        assertEquals(false, service.getConnectionStatus().get("available"));
    }

    @Test void initializedTransportWithBrokenStoreApiIsUnavailable() {
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool("list_products")), null));
        when(client.callTool(any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("fetch failed")), true, null, Map.of()));
        assertEquals(false, service.getConnectionStatus().get("available"));
    }
}

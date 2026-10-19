package com.mcp_gateway_k8s_store.backend.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Request-scoped AI tools; store selection is captured from the validated HTTP request. */
public class AiGatewayTools {
    private static final Logger log = LoggerFactory.getLogger(AiGatewayTools.class);
    private final McpGatewayTools gateway;
    private final String storeId;

    public AiGatewayTools(McpGatewayTools gateway, String storeId) {
        this.gateway = gateway;
        this.storeId = storeId;
    }

    @Tool(description = "Check actual connection status of WooCommerce, GitHub, and Weather. Use only when asked about connection or integration status.")
    public Object checkGatewayStatus() { return safe("gateway_status", gateway::gatewayStatus); }

    @Tool(description = "List or search actual products in the user's already selected WooCommerce store. Never infer product data.")
    public Object listWooCommerceProducts(
            @ToolParam(description = "Page number, starting at 1") Integer page,
            @ToolParam(description = "Maximum results, 1 to 100") Integer perPage,
            @ToolParam(description = "Optional product name search") String search) {
        return safe("woocommerce_list_products", () -> summarizeProducts(
                gateway.chatListProducts(storeId, boundedPage(page), boundedSize(perPage), search)));
    }

    @Tool(description = "List actual orders in the selected WooCommerce store. Use status only when requested; this tool does not support date filtering.")
    public Object listWooCommerceOrders(
            @ToolParam(description = "Page number, starting at 1") Integer page,
            @ToolParam(description = "Maximum results, 1 to 100") Integer perPage,
            @ToolParam(description = "Optional WooCommerce order status") String status) {
        return safe("woocommerce_list_orders", () -> gateway.chatListOrders(storeId, boundedPage(page), boundedSize(perPage), status));
    }

    @Tool(description = "Retrieve the actual details of one WooCommerce order by ID from the selected store.")
    public Object getWooCommerceOrder(@ToolParam(description = "WooCommerce order ID") Long orderId) {
        if (orderId == null || orderId < 1) return failure("A valid order ID is required.");
        return safe("woocommerce_get_order", () -> gateway.chatGetOrder(storeId, orderId));
    }

    @Tool(description = "Get current observed weather for a named city or place using the Weather MCP integration.")
    public Object getCurrentWeather(@ToolParam(description = "City or place name") String location) {
        return safe("weather_get_current", () -> gateway.getCurrentWeather(location));
    }

    @Tool(description = "Get a real daily weather forecast for a named city or place, for 1 to 7 days.")
    public Object getWeatherForecast(@ToolParam(description = "City or place name") String location,
                                     @ToolParam(description = "Number of days, 1 to 7") Integer days) {
        return safe("weather_get_forecast", () -> gateway.getForecast(location, days == null ? 5 : Math.max(1, Math.min(days, 7))));
    }

    @Tool(description = "Search actual GitHub repositories using the user's query.")
    public Object searchGitHubRepositories(@ToolParam(description = "Repository search query") String query) {
        return safe("github_search_repositories", () -> gateway.searchRepositories(query));
    }

    @Tool(description = "Search actual GitHub code using the user's query.")
    public Object searchGitHubCode(@ToolParam(description = "Code search query") String query) {
        return safe("github_search_code", () -> gateway.searchCode(query));
    }

    private int boundedPage(Integer value) { return value == null ? 1 : Math.max(1, Math.min(value, 10000)); }
    private int boundedSize(Integer value) { return value == null ? 10 : Math.max(1, Math.min(value, 100)); }
    private Object safe(String toolName, java.util.function.Supplier<Object> operation) {
        try { return operation.get(); }
        catch (RuntimeException failure) {
            log.warn("AI MCP tool {} failed ({}).", toolName, safeFailureCategory(failure));
            // Return structured JSON tool output and let Gemini explain that the
            // integration is unavailable. Never pass upstream exception text to the model.
            return failure("The requested integration operation failed. The data is unavailable; do not infer or claim success.");
        }
    }

    private String safeFailureCategory(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("enqueue message"))
            return "MCP stdio transport disconnected";
        if (root instanceof java.util.concurrent.TimeoutException
                || root instanceof java.net.http.HttpTimeoutException
                || root instanceof java.util.concurrent.CancellationException
                || (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("timeout")))
            return "upstream request timed out";
        return root.getClass().getSimpleName();
    }
    private Map<String, Object> failure(String message) { return Map.of("success", false, "error", message); }

    private Object summarizeProducts(Object result) {
        if (result instanceof java.util.List<?> products)
            return products.stream().map(this::summarizeProduct).toList();
        if (result instanceof Map<?, ?> product) return summarizeProduct(product);
        return result;
    }

    private Object summarizeProduct(Object value) {
        if (!(value instanceof Map<?, ?> product)) return value;
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        for (String field : java.util.List.of("id", "name", "price", "regular_price", "sale_price",
                "stock_status", "stock_quantity", "status", "sku", "type", "permalink")) {
            if (product.containsKey(field)) summary.put(field, product.get(field));
        }
        return summary.isEmpty() ? value : summary;
    }
}

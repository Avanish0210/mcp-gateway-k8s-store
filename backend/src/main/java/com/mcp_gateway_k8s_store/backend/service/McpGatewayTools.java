package com.mcp_gateway_k8s_store.backend.service;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Network-facing MCP tools routed through the existing integration services. */
@Component
public class McpGatewayTools {
    private final WooCommerceMcpService woo;
    private final GitHubMcpService github;
    private final WeatherMcpService weather;
    private final AuditService audit;

    public McpGatewayTools(WooCommerceMcpService woo, GitHubMcpService github, WeatherMcpService weather, AuditService audit) {
        this.woo = woo;
        this.github = github;
        this.weather = weather;
        this.audit = audit;
    }

    @McpTool(name = "gateway_status", description = "Show availability of the WooCommerce, GitHub, and Weather MCP integrations")
    public Map<String, Object> gatewayStatus() {
        return audited("gateway", "gateway_status", () -> {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("woocommerce", woo.getConnectionStatus());
        result.put("github", github.getConnectionStatus());
        result.put("weather", weather.getConnectionStatus());
        return result;
        });
    }

    @McpTool(name = "gateway_list_tools", description = "List tools currently available from each connected MCP integration")
    public Map<String, List<String>> listTools() {
        return audited("gateway", "gateway_list_tools", () -> Map.of("woocommerce", woo.getAvailableTools(), "github", github.getAvailableTools(), "weather", weather.getAvailableTools()));
    }

    @McpTool(name = "woocommerce_list_products", description = "List WooCommerce products")
    public Object listProducts(@McpToolParam(description = "Page number", required = false) Integer page,
                               @McpToolParam(description = "Maximum items, up to 100", required = false) Integer perPage,
                               @McpToolParam(description = "Optional product search", required = false) String search) {
        return audited("woocommerce", "woocommerce_list_products", () -> woo.listProducts(page == null ? 1 : page, perPage == null ? 10 : perPage, search));
    }

    @McpTool(name = "woocommerce_get_product", description = "Get one WooCommerce product by ID")
    public Object getProduct(@McpToolParam(description = "WooCommerce product ID") long productId) { return audited("woocommerce", "woocommerce_get_product", () -> woo.getProduct(productId)); }

    @McpTool(name = "woocommerce_list_orders", description = "List WooCommerce orders")
    public Object listOrders(@McpToolParam(description = "Page number", required = false) Integer page,
                             @McpToolParam(description = "Maximum items, up to 100", required = false) Integer perPage,
                             @McpToolParam(description = "Optional order status", required = false) String status) {
        return audited("woocommerce", "woocommerce_list_orders", () -> woo.listOrders(page == null ? 1 : page, perPage == null ? 10 : perPage, status));
    }

    @McpTool(name = "woocommerce_get_order", description = "Get one WooCommerce order by ID")
    public Object getOrder(@McpToolParam(description = "WooCommerce order ID") long orderId) { return audited("woocommerce", "woocommerce_get_order", () -> woo.getOrder(orderId)); }

    @McpTool(name = "woocommerce_list_customers", description = "List WooCommerce customers")
    public Object listCustomers(@McpToolParam(description = "Page number", required = false) Integer page,
                                @McpToolParam(description = "Maximum items, up to 100", required = false) Integer perPage) {
        return audited("woocommerce", "woocommerce_list_customers", () -> woo.listCustomers(page == null ? 1 : page, perPage == null ? 10 : perPage));
    }

    @McpTool(name = "woocommerce_get_customer", description = "Get one WooCommerce customer by ID")
    public Object getCustomer(@McpToolParam(description = "WooCommerce customer ID") long customerId) { return audited("woocommerce", "woocommerce_get_customer", () -> woo.getCustomer(customerId)); }

    @McpTool(name = "woocommerce_create_product", description = "Create a WooCommerce product (ADMIN only)")
    public Object createProduct(@McpToolParam(description = "Product name") String name,
                                @McpToolParam(description = "Regular price as a decimal string") String regularPrice,
                                @McpToolParam(description = "Product description", required = false) String description,
                                @McpToolParam(description = "Product type, usually simple", required = false) String type) {
        return audited("woocommerce", "woocommerce_create_product", () -> { requireAdmin(); return woo.createProduct(name, regularPrice, description, type); });
    }

    @McpTool(name = "woocommerce_update_order_status", description = "Change an order status (ADMIN only)")
    public Object updateOrderStatus(@McpToolParam(description = "WooCommerce order ID") long orderId,
                                    @McpToolParam(description = "New order status") String status) {
        return audited("woocommerce", "woocommerce_update_order_status", () -> { requireAdmin(); return woo.updateOrderStatus(orderId, status); });
    }

    @McpTool(name = "github_search_repositories", description = "Search GitHub repositories")
    public Object searchRepositories(@McpToolParam(description = "GitHub repository search query") String query) { return audited("github", "github_search_repositories", () -> github.searchRepositories(query)); }

    @McpTool(name = "github_search_code", description = "Search GitHub code")
    public Object searchCode(@McpToolParam(description = "GitHub code search query") String query) { return audited("github", "github_search_code", () -> github.searchCode(query)); }

    @McpTool(name = "github_list_issues", description = "List issues in a GitHub repository")
    public Object listIssues(@McpToolParam(description = "Repository owner") String owner,
                             @McpToolParam(description = "Repository name") String repo,
                             @McpToolParam(description = "Issue state", required = false) String state) {
        return audited("github", "github_list_issues", () -> github.listIssues(owner, repo, state));
    }

    @McpTool(name = "weather_search_location", description = "Find a place and return its coordinates and timezone")
    public Object searchLocation(@McpToolParam(description = "City or place name") String location) { return audited("weather", "weather_search_location", () -> weather.searchLocation(location)); }

    @McpTool(name = "weather_get_current", description = "Get current weather for a location")
    public Object getCurrentWeather(@McpToolParam(description = "City or place name") String location) { return audited("weather", "weather_get_current", () -> weather.getCurrentWeather(location)); }

    @McpTool(name = "weather_get_forecast", description = "Get a daily weather forecast for 1 to 7 days")
    public Object getForecast(@McpToolParam(description = "City or place name") String location,
                              @McpToolParam(description = "Number of forecast days, 1 to 7", required = false) Integer days) {
        return audited("weather", "weather_get_forecast", () -> weather.getWeatherForecast(location, days == null ? 5 : days));
    }

    private <T> T audited(String server, String tool, Supplier<T> operation) {
        long started = System.nanoTime();
        T result;
        try {
            result = operation.get();
        } catch (RuntimeException failure) {
            audit.recordCurrent("MCP_TOOL_INVOCATION", server, tool, null,
                    com.mcp_gateway_k8s_store.backend.model.AuditOutcome.FAILURE,
                    (System.nanoTime() - started) / 1_000_000, failure, null);
            throw failure;
        }
        audit.recordCurrent("MCP_TOOL_INVOCATION", server, tool, null,
                com.mcp_gateway_k8s_store.backend.model.AuditOutcome.SUCCESS,
                (System.nanoTime() - started) / 1_000_000, null, null);
        return result;
    }

    private void requireAdmin() {
        boolean allowed = SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!allowed) throw new AccessDeniedException("Administrator access required");
    }
}

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
public class WeatherMcpService {

    private final List<McpSyncClient> mcpClients;
    private final ObjectMapper objectMapper;

    public WeatherMcpService(
            List<McpSyncClient> mcpClients,
            ObjectMapper objectMapper
    ) {
        this.mcpClients = List.copyOf(mcpClients);
        this.objectMapper = objectMapper;
    }

    public List<String> getAvailableTools() {
        return mcpClients.stream()
                .filter(McpSyncClient::isInitialized)
                .flatMap(client -> client.listTools().tools().stream())
                .map(McpSchema.Tool::name)
                .filter(this::isWeatherTool)
                .distinct()
                .sorted()
                .toList();
    }


    public Map<String, Object> getConnectionStatus() {
        List<McpSyncClient> initializedClients = mcpClients.stream()
                .filter(McpSyncClient::isInitialized)
                .filter(client -> client.listTools().tools().stream()
                        .anyMatch(tool -> isWeatherTool(tool.name())))
                .toList();

        return Map.of(
                "configuredClients", mcpClients.size(),
                "initializedClients", initializedClients.size(),
                "available", !initializedClients.isEmpty(),
                "tools", getAvailableTools()
        );
    }

    public Object searchLocation(String location) {
        return invoke(
                "search_location",
                Map.of("location", location)
        );
    }

    public Object getCurrentWeather(String location) {
        return invoke(
                "get_current_weather",
                Map.of("location", location)
        );
    }

    public Object getWeatherForecast(String location, int days) {
        if (days < 1 || days > 7) {
            throw new IllegalArgumentException(
                    "Forecast days must be between 1 and 7."
            );
        }

        return invoke(
                "get_weather_forecast",
                Map.of(
                        "location", location,
                        "days", days
                )
        );
    }

    private boolean isWeatherTool(String toolName) {
        return toolName.equals("search_location")
                || toolName.equals("get_current_weather")
                || toolName.equals("get_weather_forecast");
    }

    private Object invoke(
            String toolName,
            Map<String, Object> arguments
    ) {
        McpSyncClient client = mcpClients.stream()
                .filter(McpSyncClient::isInitialized)
                .filter(candidate -> candidate.listTools().tools().stream()
                        .anyMatch(tool -> toolName.equals(tool.name())))
                .findFirst()
                .orElseThrow(() -> new McpToolException(
                        "Weather MCP tool is unavailable: " + toolName
                ));

        try {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(toolName, arguments)
            );

            if (Boolean.TRUE.equals(result.isError())) {
                String message = result.content().stream()
                        .filter(McpSchema.TextContent.class::isInstance)
                        .map(McpSchema.TextContent.class::cast)
                        .map(McpSchema.TextContent::text)
                        .findFirst()
                        .orElse("Weather MCP tool call failed.");

                throw new McpToolException(message);
            }

            if (result.structuredContent() != null) {
                return result.structuredContent();
            }

            String responseText = result.content().stream()
                    .filter(McpSchema.TextContent.class::isInstance)
                    .map(McpSchema.TextContent.class::cast)
                    .map(McpSchema.TextContent::text)
                    .findFirst()
                    .orElse("null");

            return objectMapper.readValue(responseText, Object.class);

        } catch (McpToolException exception) {
            throw exception;

        } catch (JacksonException exception) {
            throw new McpToolException(
                    "Weather MCP returned an unreadable response.",
                    exception
            );

        } catch (RuntimeException exception) {
            throw new McpToolException(
                    "Weather MCP request failed.",
                    exception
            );
        }
    }
}

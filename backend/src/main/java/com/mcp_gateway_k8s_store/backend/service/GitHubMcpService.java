package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.exception.McpToolException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GitHubMcpService {

    private final List<McpSyncClient> mcpClients;
    private final ObjectMapper objectMapper;

    private static final Set<String> GITHUB_TOOL_NAMES = Set.of(
            "search_repositories",
            "search_code",
            "search_issues",
            "list_issues",
            "get_issue",
            "list_commits",
            "get_commit",
            "get_file_contents",
            "list_pull_requests",
            "get_pull_request",
            "search_users",
            "get_me"
    );

    public GitHubMcpService(
            List<McpSyncClient> mcpClients,
            ObjectMapper objectMapper
    ) {
        this.mcpClients = List.copyOf(mcpClients);
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the available tools exposed by the GitHub MCP server.
     */
    public List<String> getAvailableTools() {
        return getGitHubClient()
                .map(client -> client.listTools()
                        .tools()
                        .stream()
                        .map(McpSchema.Tool::name)
                        .sorted()
                        .toList())
                .orElse(List.of());
    }

    /**
     * Returns the GitHub MCP connection status.
     */
    public Map<String, Object> getConnectionStatus() {

        var githubClient = getGitHubClient();

        return Map.of(
                "service", "GitHub MCP",
                "configuredClients", mcpClients.size(),
                "githubConnected", githubClient.isPresent(),
                "availableTools", githubClient
                        .map(client -> client.listTools().tools().size())
                        .orElse(0)
        );
    }

    /**
     * Searches GitHub repositories.
     *
     * The query is passed to the official MCP tool.
     */
    public Object searchRepositories(String query) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "Repository search query must not be empty."
            );
        }

        return invoke(
                "search_repositories",
                Map.of("query", query)
        );
    }

    /**
     * Searches GitHub code.
     */
    public Object searchCode(String query) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "Code search query must not be empty."
            );
        }

        return invoke(
                "search_code",
                Map.of("query", query)
        );
    }

    /**
     * Lists issues for a repository.
     */
    public Object listIssues(
            String owner,
            String repo,
            String state
    ) {

        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException(
                    "Repository owner must not be empty."
            );
        }

        if (repo == null || repo.isBlank()) {
            throw new IllegalArgumentException(
                    "Repository name must not be empty."
            );
        }

        Map<String, Object> arguments = Map.of(
                "owner", owner,
                "repo", repo,
                "state", state == null || state.isBlank()
                        ? "open"
                        : state
        );

        return invoke("list_issues", arguments);
    }

    /**
     * Invokes a GitHub MCP tool.
     *
     * Only known read-only tools are exposed through this service.
     * The official server should also be configured with its
     * GITHUB_READ_ONLY environment variable.
     */
    public Object invoke(
            String toolName,
            Map<String, Object> arguments
    ) {

        if (!GITHUB_TOOL_NAMES.contains(toolName)) {
            throw new IllegalArgumentException(
                    "Unsupported GitHub read-only tool: " + toolName
            );
        }

        McpSyncClient client = getGitHubClient()
                .orElseThrow(() -> new McpToolException(
                        "GitHub MCP client is unavailable. " +
                                "Check the Docker container and Spring AI configuration."
                ));

        boolean toolExists = client.listTools()
                .tools()
                .stream()
                .anyMatch(tool -> toolName.equals(tool.name()));

        if (!toolExists) {
            throw new McpToolException(
                    "GitHub MCP tool is not available: " + toolName
            );
        }

        try {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(
                            toolName,
                            arguments == null ? Map.of() : arguments
                    )
            );

            if (Boolean.TRUE.equals(result.isError())) {

                String message = result.content()
                        .stream()
                        .filter(McpSchema.TextContent.class::isInstance)
                        .map(McpSchema.TextContent.class::cast)
                        .map(McpSchema.TextContent::text)
                        .findFirst()
                        .orElse("GitHub MCP tool call failed.");

                throw new McpToolException(message);
            }

            if (result.structuredContent() != null) {
                return result.structuredContent();
            }

            String text = result.content()
                    .stream()
                    .filter(McpSchema.TextContent.class::isInstance)
                    .map(McpSchema.TextContent.class::cast)
                    .map(McpSchema.TextContent::text)
                    .findFirst()
                    .orElse("null");

            try {
                return objectMapper.readValue(text, Object.class);
            } catch (JacksonException exception) {
                // Some MCP tools return plain text rather than JSON.
                return text;
            }

        } catch (McpToolException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            throw new McpToolException(
                    "GitHub MCP request failed: " + toolName,
                    exception
            );
        }
    }

    /**
     * Identifies the GitHub MCP client using its advertised tools.
     *
     * This avoids selecting the WooCommerce MCP client.
     */
    private java.util.Optional<McpSyncClient> getGitHubClient() {

        return mcpClients.stream()
                .filter(McpSyncClient::isInitialized)
                .filter(client -> {
                    try {
                        return client.listTools()
                                .tools()
                                .stream()
                                .anyMatch(tool ->
                                        GITHUB_TOOL_NAMES.contains(tool.name())
                                );
                    } catch (RuntimeException exception) {
                        return false;
                    }
                })
                .findFirst();
    }
}

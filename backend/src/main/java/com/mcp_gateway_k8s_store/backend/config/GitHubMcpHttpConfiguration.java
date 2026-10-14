package com.mcp_gateway_k8s_store.backend.config;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;

@Configuration
public class GitHubMcpHttpConfiguration {

    @Bean
    McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> githubMcpRequestHeaders(
            @Value("${GITHUB_PERSONAL_ACCESS_TOKEN:}") String token) {
        return (connectionName, builder) -> {
            if ("github".equals(connectionName) && !token.isBlank()) {
                builder.httpRequestCustomizer((request, method, uri, body, context) -> {
                    request.header("Authorization", "Bearer " + token);
                    request.header("X-MCP-Toolsets", "repos,issues,pull_requests,users");
                    request.header("X-MCP-Readonly", "true");
                });
            }
        };
    }
}

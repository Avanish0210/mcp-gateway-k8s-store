package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.service.GitHubMcpService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubMcpService githubMcpService;

    public GitHubController(GitHubMcpService githubMcpService) {
        this.githubMcpService = githubMcpService;
    }

    @GetMapping("/status")
    public Map<String, Object> getConnectionStatus() {
        return githubMcpService.getConnectionStatus();
    }

    @GetMapping("/tools")
    public List<String> getAvailableTools() {
        return githubMcpService.getAvailableTools();
    }

    @GetMapping("/repositories")
    public Object searchRepositories(
            @RequestParam String query
    ) {
        return githubMcpService.searchRepositories(query);
    }

    @GetMapping("/code")
    public Object searchCode(
            @RequestParam String query
    ) {
        return githubMcpService.searchCode(query);
    }

    @GetMapping("/repositories/{owner}/{repo}/issues")
    public Object listIssues(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(defaultValue = "open") String state
    ) {
        return githubMcpService.listIssues(owner, repo, state);
    }

    @PostMapping("/tools/{toolName}")
    public Object invokeTool(
            @PathVariable String toolName,
            @RequestBody(required = false)
            Map<String, Object> arguments
    ) {
        return githubMcpService.invoke(
                toolName,
                arguments == null ? Map.of() : arguments
        );
    }
}

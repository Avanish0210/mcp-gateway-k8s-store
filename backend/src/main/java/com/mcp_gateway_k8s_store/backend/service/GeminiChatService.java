package com.mcp_gateway_k8s_store.backend.service;

import com.google.genai.errors.ApiException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.mcp_gateway_k8s_store.backend.model.StoreStatus;
import com.mcp_gateway_k8s_store.backend.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GeminiChatService {
    private final ChatClient chatClient;
    private final McpGatewayTools gateway;
    private final StoreRepository stores;
    private static final Logger log = LoggerFactory.getLogger(GeminiChatService.class);
    @org.springframework.beans.factory.annotation.Value("${GEMINI_FALLBACK_MODEL:gemini-3.5-flash-lite}")
    private String fallbackModel = "gemini-3.5-flash-lite";

    public GeminiChatService(ChatClient chatClient, McpGatewayTools gateway, StoreRepository stores) {
        this.chatClient = chatClient;
        this.gateway = gateway;
        this.stores = stores;
    }

    public String chat(String message, String requestedStoreId) {
        String storeId = resolveStore(requestedStoreId);
        long started = System.nanoTime();
        String requestId = java.util.UUID.randomUUID().toString();
        for (int attempt = 1; attempt <= 2; attempt++) {
        try {
            var prompt = chatClient.prompt()
                    .system("You are the Urmi.AI MCP Gateway assistant. Answer ordinary questions conversationally. Use tools only for relevant integration data requests. Base every integration claim only on tool results. A tool result with success=false means the operation failed and the data is unavailable; say so clearly and never infer results. Never claim a mutation succeeded unless an authorized tool confirms it. You cannot create or change products or orders. Orders tool does not filter by date; do not claim a partial page is all orders for a date.")
                    .user(message)
                    .tools(new AiGatewayTools(gateway, storeId));
            if (attempt > 1 && !fallbackModel.isBlank()) prompt.options(
                    org.springframework.ai.google.genai.GoogleGenAiChatOptions.builder().model(fallbackModel));
            String reply = prompt.call().content();
            log.debug("Chat completion succeeded in {} ms.", (System.nanoTime() - started) / 1_000_000);
            if (reply == null || reply.isBlank()) throw new IllegalStateException("Empty AI response");
            return reply;
        } catch (RuntimeException failure) {
            ApiException apiFailure = findApiException(failure);
            log.warn("Chat request {} attempt {} failed after {} ms ({}).", requestId, attempt,
                    (System.nanoTime() - started) / 1_000_000, safeFailureCategory(failure));
            if (attempt < 2 && ((apiFailure != null && apiFailure.code() >= 500) || isNetworkFailure(failure))) {
                try { Thread.sleep(1000); continue; }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
            if (apiFailure != null && apiFailure.code() == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Gemini is temporarily rate-limited or its usage quota has been reached. Check Gemini API usage and try again later.");
            }
            if (apiFailure != null && apiFailure.code() >= 500) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Gemini is temporarily unavailable. Please try again shortly. Reference: " + requestId);
            }
            if (apiFailure != null && (apiFailure.code() == 400 || apiFailure.code() == 401 || apiFailure.code() == 403 || apiFailure.code() == 404))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "The AI provider rejected the configured model or credentials. Reference: " + requestId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI assistant could not complete this request. Reference: " + requestId);
        }
        }
        throw new IllegalStateException("Chat retry exhausted");
    }

    private boolean isNetworkFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof java.io.IOException || current instanceof java.util.concurrent.TimeoutException) return true;
            if (current.getCause() == current) break;
        }
        return false;
    }

    private ApiException findApiException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ApiException apiFailure) return apiFailure;
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return null;
    }

    private String safeFailureCategory(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        if (root instanceof ApiException apiFailure) {
            String status = apiFailure.status();
            String safeStatus = status != null && status.matches("[A-Z_]{1,50}") ? status : "unknown";
            return "Gemini API HTTP " + apiFailure.code() + " (" + safeStatus + ")";
        }
        String message = root.getMessage();
        if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("enqueue message"))
            return "MCP stdio transport disconnected";
        if (root instanceof java.util.concurrent.TimeoutException
                || root instanceof java.net.http.HttpTimeoutException
                || (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("timeout")))
            return "upstream request timed out";
        return root.getClass().getSimpleName();
    }

    private String resolveStore(String requestedStoreId) {
        if (requestedStoreId == null || requestedStoreId.isBlank() || "store-1".equals(requestedStoreId)) return null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin) throw new AccessDeniedException("You are not authorized to use this store.");
        var store = stores.findById(requestedStoreId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found."));
        // Keep ordinary conversation available while this store reconnects.
        // Integration tools still target this exact store and report unavailable data.
        return store.getId();
    }
}

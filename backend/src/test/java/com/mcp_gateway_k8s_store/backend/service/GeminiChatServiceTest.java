package com.mcp_gateway_k8s_store.backend.service;

import com.google.genai.errors.ApiException;
import com.mcp_gateway_k8s_store.backend.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GeminiChatServiceTest {
    @Test void providerUnavailableUsesOneFallbackAttempt() {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec prompt = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt()).thenReturn(prompt);
        when(prompt.call()).thenReturn(response);
        ApiException unavailable = mock(ApiException.class);
        when(unavailable.code()).thenReturn(503);
        when(response.content()).thenThrow(unavailable).thenReturn("Recovered reply");
        GeminiChatService service = new GeminiChatService(client, mock(McpGatewayTools.class), mock(StoreRepository.class));
        assertEquals("Recovered reply", service.chat("Hello", null));
        verify(client, times(2)).prompt();
        verify(prompt).options(any(org.springframework.ai.google.genai.GoogleGenAiChatOptions.Builder.class));
    }

    @Test void quotaFailureDoesNotTriggerRepeatedProviderRequests() {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec prompt = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt()).thenReturn(prompt);
        when(prompt.call()).thenReturn(response);
        ApiException quota = mock(ApiException.class);
        when(quota.code()).thenReturn(429);
        when(response.content()).thenThrow(quota);
        GeminiChatService service = new GeminiChatService(client, mock(McpGatewayTools.class), mock(StoreRepository.class));
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> service.chat("Hello", null)).getStatusCode().value());
        verify(client, times(1)).prompt();
    }
}

package com.mcp_gateway_k8s_store.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public com.google.genai.Client googleGenAiClient(
            @org.springframework.beans.factory.annotation.Value("${spring.ai.google.genai.api-key}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${chat.provider.timeout-ms:20000}") int timeoutMs) {
        // Disable the SDK's own retry loop: GeminiChatService owns the bounded
        // primary/fallback attempts and the browser has a finite request budget.
        return com.google.genai.Client.builder().apiKey(apiKey)
                .httpOptions(com.google.genai.types.HttpOptions.builder()
                        .timeout(timeoutMs)
                        .retryOptions(com.google.genai.types.HttpRetryOptions.builder().attempts(1).build()).build())
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder){
        return chatClientBuilder.build();
    }
}

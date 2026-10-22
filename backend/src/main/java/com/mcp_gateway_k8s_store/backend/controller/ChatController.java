package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.dto.ChatRequest;
import com.mcp_gateway_k8s_store.backend.dto.ChatResponse;
import com.mcp_gateway_k8s_store.backend.service.GeminiChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final GeminiChatService geminiChatService;

    public ChatController(GeminiChatService geminiChatService) {
        this.geminiChatService = geminiChatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest chatRequest){
        String reply = geminiChatService.chat(chatRequest.message(), chatRequest.storeId());
        return ResponseEntity.ok(new ChatResponse(reply));
    }
}

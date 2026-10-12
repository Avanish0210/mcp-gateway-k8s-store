package com.mcp_gateway_k8s_store.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpService;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final WooCommerceMcpService wooCommerceMcpService;
    private final String applicationName;

    public HealthController(WooCommerceMcpService wooCommerceMcpService,
                            @Value("${spring.application.name:mcp-gateway-k8s-store}") String applicationName) {
        this.wooCommerceMcpService = wooCommerceMcpService;
        this.applicationName = applicationName;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "application", applicationName,
                "woocommerceMcp", wooCommerceMcpService.getConnectionStatus());
    }
}

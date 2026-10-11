package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/woocommerce")
@RequiredArgsConstructor
public class WooCommerceController {

    private final WooCommerceMcpService wooCommerceMcpService;


    @GetMapping("/tools")
    public List<String> getAvailableTools() {
        return wooCommerceMcpService.getAvailableTools();
    }
}

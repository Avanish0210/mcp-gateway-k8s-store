package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/woocommerce")
public class WooCommerceController {

    private final WooCommerceMcpService wooCommerceMcpService;

    public WooCommerceController(WooCommerceMcpService wooCommerceMcpService) { this.wooCommerceMcpService = wooCommerceMcpService; }


    @GetMapping("/tools")
    public List<String> getAvailableTools() {
        return wooCommerceMcpService.getAvailableTools();
    }

    @GetMapping("/store-tools")
    public List<String> getStoreTools(@RequestParam String storeId) {
        return wooCommerceMcpService.getAvailableTools(storeId);
    }

    @GetMapping("/status")
    public Map<String, Object> getConnectionStatus(@RequestParam(required = false) String storeId) {
        if (storeId != null && !storeId.isBlank() && !"store-1".equals(storeId)) {
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
                throw new org.springframework.security.access.AccessDeniedException("Only administrators can access managed stores");
        }
        return wooCommerceMcpService.getConnectionStatus(storeId);
    }
}

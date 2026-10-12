package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.dto.UpdateOrderStatusRequest;
import com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/woocommerce/orders")
@RequiredArgsConstructor
public class OrderController {
    private final WooCommerceMcpService service;

    @GetMapping
    public Object list(@RequestParam(defaultValue = "1") @Min(1) int page,
                       @RequestParam(defaultValue = "10") @Min(1) @Max(100) int perPage,
                       @RequestParam(required = false) String status) {
        return service.listOrders(page, perPage, status);
    }

    @GetMapping("/{orderId}")
    public Object get(@PathVariable @Min(1) long orderId) {
        return service.getOrder(orderId);
    }

    @PutMapping("/{orderId}/status")
    public Object updateStatus(@PathVariable @Min(1) long orderId,
                               @Valid @RequestBody UpdateOrderStatusRequest request) {
        return service.updateOrderStatus(orderId, request.status());
    }
}

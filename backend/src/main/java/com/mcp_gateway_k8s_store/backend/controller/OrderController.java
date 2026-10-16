package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.dto.UpdateOrderStatusRequest;
import com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class OrderController {
    private final WooCommerceMcpService service;

    public OrderController(WooCommerceMcpService service) { this.service = service; }

    @GetMapping
    public Object list(@RequestParam(defaultValue = "1") @Min(1) int page,
                       @RequestParam(defaultValue = "10") @Min(1) @Max(100) int perPage,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String storeId) {
        return storeId == null ? service.listOrders(page, perPage, status) : service.listOrders(storeId, page, perPage, status);
    }

    @GetMapping("/{orderId}")
    public Object get(@PathVariable @Min(1) long orderId, @RequestParam(required = false) String storeId) {
        return storeId == null ? service.getOrder(orderId) : service.getOrder(storeId, orderId);
    }

    @PutMapping("/{orderId}/status")
    public Object updateStatus(@PathVariable @Min(1) long orderId,
                               @Valid @RequestBody UpdateOrderStatusRequest request,
                               @RequestParam(required = false) String storeId) {
        return storeId == null ? service.updateOrderStatus(orderId, request.status()) : service.updateOrderStatus(storeId, orderId, request.status());
    }
}

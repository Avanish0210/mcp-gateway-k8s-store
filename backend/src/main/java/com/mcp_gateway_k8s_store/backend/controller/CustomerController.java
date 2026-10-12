package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/woocommerce/customers")
public class CustomerController {
    private final WooCommerceMcpService service;

    public CustomerController(WooCommerceMcpService service) {
        this.service = service;
    }

    @GetMapping
    public Object list(@RequestParam(defaultValue = "1") @Min(1) int page,
                       @RequestParam(defaultValue = "10") @Min(1) @Max(100) int perPage) {
        return service.listCustomers(page, perPage);
    }

    @GetMapping("/{customerId}")
    public Object get(@PathVariable @Min(1) long customerId) {
        return service.getCustomer(customerId);
    }
}

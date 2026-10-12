package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.dto.CreateProductRequest;
import com.mcp_gateway_k8s_store.backend.service.WooCommerceMcpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/woocommerce/products")
public class ProductController {
    private final WooCommerceMcpService service;

    public ProductController(WooCommerceMcpService service) {
        this.service = service;
    }

    @GetMapping
    public Object list(@RequestParam(defaultValue = "1") @Min(1) int page,
                       @RequestParam(defaultValue = "10") @Min(1) @Max(100) int perPage,
                       @RequestParam(required = false) String search) {
        return service.listProducts(page, perPage, search);
    }

    @GetMapping("/{productId}")
    public Object get(@PathVariable @Min(1) long productId) {
        return service.getProduct(productId);
    }

    @PostMapping
    public Object create(@Valid @RequestBody CreateProductRequest request) {
        return service.createProduct(request.name(), request.regularPrice().toPlainString(),
                request.description(), request.type());
    }
}

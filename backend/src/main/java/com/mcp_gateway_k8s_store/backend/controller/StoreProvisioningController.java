package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.dto.CreateStoreRequest;
import com.mcp_gateway_k8s_store.backend.dto.StoreResponse;
import com.mcp_gateway_k8s_store.backend.service.StoreProvisioningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stores")
@ConditionalOnProperty(prefix = "stores.provisioning", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StoreProvisioningController {

    private final StoreProvisioningService storeProvisioningService;

    public StoreProvisioningController(StoreProvisioningService storeProvisioningService) { this.storeProvisioningService = storeProvisioningService; }

    @GetMapping
    public List<StoreResponse> listStores() {
        return storeProvisioningService.listStores();
    }

    @GetMapping("/{id}")
    public StoreResponse getStore(@PathVariable String id) {
        return storeProvisioningService.getStore(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse createStore(
            @Valid @RequestBody CreateStoreRequest request
    ) {
        return storeProvisioningService.createStore(request);
    }

    @PostMapping("/{id}/retry")
    public StoreResponse retry(@PathVariable String id) {
        return storeProvisioningService.retryStore(id);
    }

    @DeleteMapping("/{id}")
    public StoreResponse delete(@PathVariable String id) {
        return storeProvisioningService.deleteStore(id);
    }

    @GetMapping("/{id}/status")
    public StoreResponse status(@PathVariable String id) {
        return storeProvisioningService.getStore(id);
    }
}

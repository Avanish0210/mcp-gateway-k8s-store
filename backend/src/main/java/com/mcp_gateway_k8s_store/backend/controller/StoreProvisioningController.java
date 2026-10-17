package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.dto.CreateStoreRequest;
import com.mcp_gateway_k8s_store.backend.dto.StoreResponse;
import com.mcp_gateway_k8s_store.backend.service.StoreProvisioningService;
import com.mcp_gateway_k8s_store.backend.service.AuditService;
import com.mcp_gateway_k8s_store.backend.model.AuditOutcome;
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
    private final AuditService audit;

    public StoreProvisioningController(StoreProvisioningService storeProvisioningService, AuditService audit) { this.storeProvisioningService = storeProvisioningService; this.audit = audit; }

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
        long started = System.nanoTime();
        try {
            StoreResponse response = storeProvisioningService.createStore(request);
            audit.recordCurrent("STORE_CREATED", null, null, response.namespace(), AuditOutcome.SUCCESS,
                    (System.nanoTime() - started) / 1_000_000, null, "Store provisioning accepted");
            return response;
        } catch (RuntimeException failure) {
            audit.recordCurrent("STORE_CREATED", null, null, null, AuditOutcome.FAILURE,
                    (System.nanoTime() - started) / 1_000_000, failure, null);
            throw failure;
        }
    }

    @PostMapping("/{id}/retry")
    public StoreResponse retry(@PathVariable String id) {
        StoreResponse response = storeProvisioningService.retryStore(id);
        audit.recordCurrent("STORE_PROVISIONING_RETRIED", null, null, response.namespace(), AuditOutcome.SUCCESS, null, null, "Store provisioning retry accepted");
        return response;
    }

    @DeleteMapping("/{id}")
    public StoreResponse delete(@PathVariable String id) {
        long started = System.nanoTime();
        try {
            StoreResponse response = storeProvisioningService.deleteStore(id);
            audit.recordCurrent("STORE_DELETION_REQUESTED", null, null, response.namespace(), AuditOutcome.SUCCESS,
                    (System.nanoTime() - started) / 1_000_000, null, "Store deletion accepted");
            return response;
        } catch (RuntimeException failure) {
            audit.recordCurrent("STORE_DELETION_REQUESTED", null, null, id, AuditOutcome.FAILURE,
                    (System.nanoTime() - started) / 1_000_000, failure, null);
            throw failure;
        }
    }

    @GetMapping("/{id}/status")
    public StoreResponse status(@PathVariable String id) {
        return storeProvisioningService.getStore(id);
    }
}

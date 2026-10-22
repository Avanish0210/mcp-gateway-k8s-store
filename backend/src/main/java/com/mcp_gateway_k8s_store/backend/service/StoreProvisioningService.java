package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.dto.CreateStoreRequest;
import com.mcp_gateway_k8s_store.backend.dto.StoreResponse;
import com.mcp_gateway_k8s_store.backend.model.Store;
import com.mcp_gateway_k8s_store.backend.model.StoreStatus;
import com.mcp_gateway_k8s_store.backend.repository.StoreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "stores.provisioning", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StoreProvisioningService {

    private static final int MAX_STORES = 3;
    private static final String EXISTING_NAMESPACE = "ecommerce";

    private final StoreRepository storeRepository;
    private final StoreProvisioningWorker worker;
    @org.springframework.beans.factory.annotation.Autowired
    private WooCommerceMcpClientRegistry clients;

    public StoreProvisioningService(StoreRepository storeRepository, StoreProvisioningWorker worker) {
        this.storeRepository = storeRepository;
        this.worker = worker;
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> listStores() {
        List<StoreResponse> result = storeRepository.findAll()
                .stream()
                .map(StoreResponse::from)
                .toList();
        if (storeRepository.existsByNamespaceName(EXISTING_NAMESPACE)) return result;
        java.util.ArrayList<StoreResponse> withExisting = new java.util.ArrayList<>();
        withExisting.add(existingStore());
        withExisting.addAll(result);
        return List.copyOf(withExisting);
    }

    @Transactional(readOnly = true)
    public StoreResponse getStore(String id) {
        if ("store-1".equals(id) && !storeRepository.existsByNamespaceName(EXISTING_NAMESPACE))
            return existingStore();
        return StoreResponse.from(findStore(id));
    }

    private StoreResponse existingStore() {
        boolean ready = false;
        if (clients != null) {
            try { clients.require("store-1"); ready = true; } catch (IllegalStateException unavailable) { }
        }
        return new StoreResponse("store-1", "Store 1", EXISTING_NAMESPACE, "ecommerce-store", null,
                ready ? StoreStatus.READY : StoreStatus.RECOVERING, ready ? null : "Store reconnecting automatically", null);
    }

    @Transactional
    public synchronized StoreResponse createStore(CreateStoreRequest request) {
        try {
            worker.verifyClusterReady();
        } catch (IllegalStateException unavailable) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The Kubernetes cluster is not reachable, so the store was not created. "
                            + unavailable.getMessage()
            );
        }
        long registeredStores = storeRepository.count();
        long totalIncludingExistingStore = registeredStores
                + (storeRepository.existsByNamespaceName(EXISTING_NAMESPACE) ? 0 : 1);

        if (totalIncludingExistingStore >= MAX_STORES) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The maximum of three stores includes the existing Store 1. "
                            + "Only two additional stores are permitted."
            );
        }

        String namespace = nextNamespace();
        String release = namespace;

        Store store = new Store(
                request.name().trim(),
                namespace,
                release,
                StoreStatus.PROVISIONING
        );

        Store saved = storeRepository.saveAndFlush(store);
        afterCommit(() -> worker.provision(saved.getId()));
        return StoreResponse.from(saved);
    }

    @Transactional
    public StoreResponse retryStore(String id) {
        Store store = findStore(id);
        if (store.getStatus() == StoreStatus.DELETING && store.getErrorMessage() != null && store.getErrorMessage().startsWith("Deletion failed")) {
            afterCommit(() -> worker.delete(store.getId()));
            return StoreResponse.from(store);
        }
        if (store.getStatus() != StoreStatus.FAILED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed provisioning or deletion can be retried");
        store.setStatus(StoreStatus.PROVISIONING);
        store.setErrorMessage(null);
        Store saved = storeRepository.save(store);
        afterCommit(() -> worker.provision(saved.getId()));
        return StoreResponse.from(saved);
    }

    @Transactional
    public StoreResponse deleteStore(String id) {
        if ("store-1".equals(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Store 1 cannot be deleted");
        Store store = findStore(id);
        if (EXISTING_NAMESPACE.equals(store.getNamespaceName()) || "ecommerce-store".equals(store.getHelmRelease()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Store 1 cannot be deleted");
        if (!store.getNamespaceName().matches("ecommerce-store-[23]") || !store.getHelmRelease().equals(store.getNamespaceName()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Store is not a validated managed namespace");
        if (store.getStatus() == StoreStatus.DELETING) {
            if (store.getErrorMessage() != null && store.getErrorMessage().startsWith("Deletion failed")) afterCommit(() -> worker.delete(store.getId()));
            return StoreResponse.from(store);
        }
        if (store.getStatus() == StoreStatus.PROVISIONING) throw new ResponseStatusException(HttpStatus.CONFLICT, "Wait for provisioning to finish before deleting this store");
        store.setStatus(StoreStatus.DELETING);
        store.setErrorMessage(null);
        Store saved = storeRepository.save(store);
        afterCommit(() -> worker.delete(saved.getId()));
        return StoreResponse.from(saved);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private Store findStore(String id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Store not found"
                ));
    }

    private String nextNamespace() {
        for (int number = 2; number <= 3; number++) {
            String candidate = "ecommerce-store-" + number;

            boolean alreadyRecorded =
                    storeRepository.existsByNamespaceName(candidate);

            if (!alreadyRecorded && !EXISTING_NAMESPACE.equals(candidate)) {
                return candidate;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No additional store namespace is available"
        );
    }
}

package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.model.StoreStatus;
import com.mcp_gateway_k8s_store.backend.repository.StoreRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "stores.provisioning", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StoreReconciliationRunner implements ApplicationRunner {
    private final StoreRepository stores;
    private final StoreProvisioningWorker worker;

    public StoreReconciliationRunner(StoreRepository stores, StoreProvisioningWorker worker) { this.stores = stores; this.worker = worker; }

    @Override public void run(ApplicationArguments args) {
        stores.findByStatusIn(List.of(StoreStatus.PROVISIONING, StoreStatus.DELETING, StoreStatus.READY)).forEach(store -> {
            if (store.getNamespaceName().equals("ecommerce") || store.getHelmRelease().equals("ecommerce-store")) return;
            switch (store.getStatus()) {
                case PROVISIONING -> worker.provision(store.getId());
                case DELETING -> worker.delete(store.getId());
                case READY -> worker.validateReady(store.getId());
                default -> { }
            }
        });
    }
}

package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.model.Store;
import com.mcp_gateway_k8s_store.backend.model.StoreStatus;
import com.mcp_gateway_k8s_store.backend.repository.StoreRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@ConditionalOnProperty(prefix = "stores.provisioning", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StoreProvisioningWorker {
    private final StoreRepository stores;
    private final KubernetesStoreAdapter kubernetes;
    private final WooCommerceMcpClientRegistry mcpClients;
    private static final SecureRandom RANDOM = new SecureRandom();

    public StoreProvisioningWorker(StoreRepository stores, KubernetesStoreAdapter kubernetes, WooCommerceMcpClientRegistry mcpClients) { this.stores = stores; this.kubernetes = kubernetes; this.mcpClients = mcpClients; }

    public void verifyClusterReady() { kubernetes.verifyClusterReady(); }

    @Async("storeProvisioningExecutor")
    public void provision(String id) {
        Store store = stores.findById(id).orElse(null);
        if (store == null) return;
        try {
            int port = store.getNamespaceName().endsWith("2") ? 38082 : 38083;
            String wordpress = secret(), database = secret(), root = secret();
            kubernetes.provision(store.getNamespaceName(), store.getHelmRelease(), store.getId(), port, wordpress, database, root);
            kubernetes.verifyEndpoint(store.getNamespaceName(), store.getHelmRelease(), port);
            String url = kubernetes.hostUrl(port);
            KubernetesStoreAdapter.StoreApiCredentials api = kubernetes.ensureWooCommerceCredentials(store.getNamespaceName(), store.getHelmRelease(), store.getId());
            mcpClients.register(store, url, api.consumerKey(), api.consumerSecret());
            store.setStoreUrl(url);
            store.setStatus(StoreStatus.READY);
            store.setErrorMessage(null);
        } catch (Exception e) {
            // Keep any correctly-owned namespace, Helm release and PVCs available for
            // diagnosis and retry. Cleanup is an explicit user-requested delete action.
            store.setStatus(StoreStatus.FAILED);
            store.setStoreUrl(null);
            store.setErrorMessage(sanitize(e.getMessage()));
        }
        stores.save(store);
    }

    @Async("storeProvisioningExecutor")
    public void delete(String id) {
        Store store = stores.findById(id).orElse(null);
        if (store == null || store.getNamespaceName().equals("ecommerce")) return;
        try {
            mcpClients.unregister(store.getId());
            kubernetes.deleteManaged(store.getNamespaceName(), store.getHelmRelease(), store.getId());
            stores.delete(store);
        } catch (Exception e) { store.setStatus(StoreStatus.DELETING); store.setErrorMessage("Deletion failed and can be retried: " + sanitize(e.getMessage())); stores.save(store); }
    }

    @Async("storeProvisioningExecutor")
    public void validateReady(String id) {
        Store store = stores.findById(id).orElse(null);
        if (store == null || store.getStatus() != StoreStatus.READY) return;
        try {
            int port = store.getNamespaceName().endsWith("2") ? 38082 : 38083;
            kubernetes.verifyEndpoint(store.getNamespaceName(), store.getHelmRelease(), port);
            KubernetesStoreAdapter.StoreApiCredentials api = kubernetes.ensureWooCommerceCredentials(store.getNamespaceName(), store.getHelmRelease(), store.getId());
            mcpClients.register(store, kubernetes.hostUrl(port), api.consumerKey(), api.consumerSecret());
        } catch (Exception e) {
            mcpClients.unregister(store.getId());
            store.setStatus(StoreStatus.FAILED);
            store.setErrorMessage("Store health check failed after backend restart: " + sanitize(e.getMessage()));
            stores.save(store);
        }
    }

    @Transactional
    public void retry(String id) {
        Store store = stores.findById(id).orElseThrow();
        if (store.getStatus() != StoreStatus.FAILED) throw new IllegalStateException("Only failed stores can be retried");
        store.setStatus(StoreStatus.PROVISIONING); store.setErrorMessage(null); stores.save(store); provision(id);
    }

    private static String secret() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String sanitize(String value) { if (value == null || value.isBlank()) return "Provisioning failed; inspect backend logs for a redacted diagnostic."; return value.replaceAll("(?i)(password|secret|token)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2[redacted]").substring(0, Math.min(value.length(), 900)); }
}

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
    private final java.util.Map<String, java.util.concurrent.locks.ReentrantLock> locks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Integer> failures = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Long> nextCheck = new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Value("${WOOCOMMERCE_CONSUMER_KEY:}")
    private String defaultKey = "";
    @org.springframework.beans.factory.annotation.Value("${WOOCOMMERCE_CONSUMER_SECRET:}")
    private String defaultSecret = "";

    public StoreProvisioningWorker(StoreRepository stores, KubernetesStoreAdapter kubernetes, WooCommerceMcpClientRegistry mcpClients) { this.stores = stores; this.kubernetes = kubernetes; this.mcpClients = mcpClients; }

    public void verifyClusterReady() { kubernetes.verifyClusterReady(); }

    @Async("storeProvisioningExecutor")
    public void provision(String id) {
        var lifecycle = locks.computeIfAbsent(id, ignored -> new java.util.concurrent.locks.ReentrantLock());
        lifecycle.lock();
        try {
        Store store = stores.findById(id).orElse(null);
        if (store == null || store.getStatus() != StoreStatus.PROVISIONING) return;
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
        } finally { lifecycle.unlock(); }
    }

    @Async("storeProvisioningExecutor")
    public void delete(String id) {
        var lifecycle = locks.computeIfAbsent(id, ignored -> new java.util.concurrent.locks.ReentrantLock());
        lifecycle.lock();
        try {
        Store store = stores.findById(id).orElse(null);
        if (store == null || store.getStatus() != StoreStatus.DELETING || store.getNamespaceName().equals("ecommerce")) return;
        try {
            mcpClients.unregister(store.getId());
            kubernetes.deleteManaged(store.getNamespaceName(), store.getHelmRelease(), store.getId());
            stores.delete(store);
        } catch (Exception e) { store.setStatus(StoreStatus.DELETING); store.setErrorMessage("Deletion failed and can be retried: " + sanitize(e.getMessage())); stores.save(store); }
        } finally { lifecycle.unlock(); }
    }

    public void validateReady(String id) {
        var lifecycle = locks.computeIfAbsent(id, ignored -> new java.util.concurrent.locks.ReentrantLock());
        if (!lifecycle.tryLock()) return;
        try {
        if (System.currentTimeMillis() < nextCheck.getOrDefault(id, 0L)) return;
        Store store = stores.findById(id).orElse(null);
        if (store == null || (store.getStatus() != StoreStatus.READY && store.getStatus() != StoreStatus.RECOVERING)) return;
        try {
            int port = store.getNamespaceName().endsWith("2") ? 38082 : 38083;
            kubernetes.verifyEndpoint(store.getNamespaceName(), store.getHelmRelease(), port);
            KubernetesStoreAdapter.StoreApiCredentials api = kubernetes.ensureWooCommerceCredentials(store.getNamespaceName(), store.getHelmRelease(), store.getId());
            mcpClients.register(store, kubernetes.hostUrl(port), api.consumerKey(), api.consumerSecret());
            updateHealth(store, StoreStatus.READY, null);
            failures.remove(id); nextCheck.remove(id);
        } catch (Exception e) {
            mcpClients.unregister(store.getId());
            updateHealth(store, StoreStatus.RECOVERING, "Store reconnecting automatically: " + sanitize(e.getMessage()));
            backoff(id);
        }
        } finally { lifecycle.unlock(); }
    }

    private void updateHealth(Store checked, StoreStatus status, String error) {
        // A delete request may have committed while the health probe was running.
        stores.updateHealth(checked.getId(), status, error,
                kubernetes.hostUrl(checked.getNamespaceName().endsWith("2") ? 38082 : 38083),
                java.util.List.of(StoreStatus.READY, StoreStatus.RECOVERING));
    }

    public void validateDefault() {
        var lifecycle = locks.computeIfAbsent("store-1", ignored -> new java.util.concurrent.locks.ReentrantLock());
        if (defaultKey.isBlank() || defaultSecret.isBlank() || !lifecycle.tryLock()) return;
        try {
            if (System.currentTimeMillis() < nextCheck.getOrDefault("store-1", 0L)) return;
            kubernetes.verifyEndpoint("ecommerce", "ecommerce-store", 38081);
            kubernetes.ensureDefaultWooCommercePlugin();
            mcpClients.register("store-1", kubernetes.hostUrl(38081), defaultKey, defaultSecret);
            failures.remove("store-1"); nextCheck.remove("store-1");
        } catch (RuntimeException failure) {
            mcpClients.unregister("store-1"); backoff("store-1");
        } finally { lifecycle.unlock(); }
    }

    private void backoff(String id) {
        int count = failures.merge(id, 1, Integer::sum);
        nextCheck.put(id, System.currentTimeMillis() + Math.min(120_000L, 5_000L << Math.min(count - 1, 5)));
    }

    @Transactional
    public void retry(String id) {
        Store store = stores.findById(id).orElseThrow();
        if (store.getStatus() != StoreStatus.FAILED) throw new IllegalStateException("Only failed stores can be retried");
        store.setStatus(StoreStatus.PROVISIONING); store.setErrorMessage(null); stores.save(store); provision(id);
    }

    private static String secret() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String sanitize(String value) { if (value == null || value.isBlank()) return "Provisioning failed; inspect backend logs for a redacted diagnostic."; String safe = value.replaceAll("(?i)(password|secret|token)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2[redacted]"); return safe.substring(0, Math.min(safe.length(), 900)); }
}

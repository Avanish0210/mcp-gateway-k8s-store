package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.model.Store;
import com.mcp_gateway_k8s_store.backend.model.StoreStatus;
import com.mcp_gateway_k8s_store.backend.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StoreRecoveryTest {
    @Test void defaultStoreRepairsAccessAndPluginWithoutTouchingManagedStoreRecords() {
        StoreRepository stores = mock(StoreRepository.class);
        KubernetesStoreAdapter kubernetes = mock(KubernetesStoreAdapter.class);
        WooCommerceMcpClientRegistry clients = mock(WooCommerceMcpClientRegistry.class);
        when(kubernetes.hostUrl(38081)).thenReturn("http://127.0.0.1:38081/");
        StoreProvisioningWorker worker = new StoreProvisioningWorker(stores, kubernetes, clients);
        ReflectionTestUtils.setField(worker, "defaultKey", "key");
        ReflectionTestUtils.setField(worker, "defaultSecret", "secret");
        worker.validateDefault();
        verify(kubernetes).verifyEndpoint("ecommerce", "ecommerce-store", 38081);
        verify(kubernetes).ensureDefaultWooCommercePlugin();
        verify(clients).register("store-1", "http://127.0.0.1:38081/", "key", "secret");
        verifyNoInteractions(stores);
    }

    @Test void temporaryOutageRecoversWithoutProvisioningOrChangingCredentials() {
        StoreRepository stores = mock(StoreRepository.class);
        KubernetesStoreAdapter kubernetes = mock(KubernetesStoreAdapter.class);
        WooCommerceMcpClientRegistry clients = mock(WooCommerceMcpClientRegistry.class);
        Store store = new Store("Shop", "ecommerce-store-2", "ecommerce-store-2", StoreStatus.READY);
        ReflectionTestUtils.setField(store, "id", "test-id");
        when(stores.findById("test-id")).thenReturn(Optional.of(store));
        when(kubernetes.hostUrl(38082)).thenReturn("http://127.0.0.1:38082/");
        doThrow(new IllegalStateException("connection refused")).doNothing()
                .when(kubernetes).verifyEndpoint("ecommerce-store-2", "ecommerce-store-2", 38082);
        when(kubernetes.ensureWooCommerceCredentials(anyString(), anyString(), anyString()))
                .thenReturn(new KubernetesStoreAdapter.StoreApiCredentials("key", "secret"));
        StoreProvisioningWorker worker = new StoreProvisioningWorker(stores, kubernetes, clients);
        worker.validateReady("test-id");
        verify(stores).updateHealth(eq("test-id"), eq(StoreStatus.RECOVERING), contains("reconnecting"), anyString(), any());
        // The scheduler respects backoff rather than repeatedly probing a broken cluster.
        worker.validateReady("test-id");
        verify(kubernetes, times(1)).verifyEndpoint(anyString(), anyString(), anyInt());
        ReflectionTestUtils.setField(worker, "nextCheck", new java.util.concurrent.ConcurrentHashMap<String, Long>());
        store.setStatus(StoreStatus.RECOVERING);
        worker.validateReady("test-id");
        verify(clients).register(store, "http://127.0.0.1:38082/", "key", "secret");
        verify(stores).updateHealth("test-id", StoreStatus.READY, null, "http://127.0.0.1:38082/", List.of(StoreStatus.READY, StoreStatus.RECOVERING));
        verify(kubernetes, never()).provision(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test void recoveryDoesNotTouchDeletingStores() {
        StoreRepository stores = mock(StoreRepository.class);
        KubernetesStoreAdapter kubernetes = mock(KubernetesStoreAdapter.class);
        WooCommerceMcpClientRegistry clients = mock(WooCommerceMcpClientRegistry.class);
        Store store = new Store("Shop", "ecommerce-store-2", "ecommerce-store-2", StoreStatus.DELETING);
        when(stores.findById("test-id")).thenReturn(Optional.of(store));
        new StoreProvisioningWorker(stores, kubernetes, clients).validateReady("test-id");
        verifyNoInteractions(kubernetes, clients);
    }
}

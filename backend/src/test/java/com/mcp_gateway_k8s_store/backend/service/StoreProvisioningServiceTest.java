package com.mcp_gateway_k8s_store.backend.service;

import com.mcp_gateway_k8s_store.backend.dto.CreateStoreRequest;
import com.mcp_gateway_k8s_store.backend.model.Store;
import com.mcp_gateway_k8s_store.backend.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StoreProvisioningServiceTest {
    private StoreRepository repository;
    private StoreProvisioningWorker worker;
    private List<Store> records;
    private StoreProvisioningService service;

    @BeforeEach
    void setUp() {
        repository = mock(StoreRepository.class);
        worker = mock(StoreProvisioningWorker.class);
        records = new ArrayList<>();
        when(repository.count()).thenAnswer(inv -> (long) records.size());
        when(repository.existsByNamespaceName(any())).thenAnswer(inv -> records.stream().anyMatch(s -> s.getNamespaceName().equals(inv.getArgument(0))));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> { Store store = inv.getArgument(0); records.add(store); return store; });
        service = new StoreProvisioningService(repository, worker);
    }

    @Test
    void allocatesStoreTwoAndThreeWithoutRegisteringExistingStoreOne() {
        var first = service.createStore(new CreateStoreRequest("Store Two"));
        var second = service.createStore(new CreateStoreRequest("Store Three"));
        assertEquals("ecommerce-store-2", first.namespace());
        assertEquals("ecommerce-store-3", second.namespace());
        assertEquals(2, records.size());
    }

    @Test
    void storeOneCountsTowardMaximumAndStoreFourIsRejected() {
        service.createStore(new CreateStoreRequest("Store Two"));
        service.createStore(new CreateStoreRequest("Store Three"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createStore(new CreateStoreRequest("Store Four")));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void concurrentCreatesDoNotExceedTwoAdditionalSlots() throws Exception {
        try (var pool = Executors.newFixedThreadPool(3)) {
            var a = pool.submit(() -> createOrReject("Alpha"));
            var b = pool.submit(() -> createOrReject("Bravo"));
            var c = pool.submit(() -> createOrReject("Charlie"));
            List<String> namespaces = java.util.stream.Stream.of(a.get(), b.get(), c.get()).filter(java.util.Objects::nonNull).toList();
            assertEquals(2, records.size());
            assertEquals(2, namespaces.size());
            assertEquals(2, namespaces.stream().distinct().count());
        }
    }

    private String createOrReject(String name) {
        try { return service.createStore(new CreateStoreRequest(name)).namespace(); }
        catch (ResponseStatusException conflict) { return null; }
    }
}

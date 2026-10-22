package com.mcp_gateway_k8s_store.backend.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StoreStorageIsolationTest {
    @Test void savedApiCredentialsStillRepairMissingPluginAfterRestart() {
        KubernetesCommandRunner runner = mock(KubernetesCommandRunner.class);
        KubernetesStoreAdapter adapter = new KubernetesStoreAdapter("kubectl", "", "helm", "", "minikube", "", "127.0.0.1", "", "../helm/store-template");
        ReflectionTestUtils.setField(adapter, "commandRunner", runner);
        when(runner.run(anyList(), nullable(String.class), any(Duration.class), anyString())).thenAnswer(invocation -> {
            List<String> args = invocation.getArgument(0);
            if (args.contains("current-context")) return "minikube";
            if (args.contains("plugin") && args.contains("list")) return "[]";
            if (args.contains("--ignore-not-found")) return "woocommerce-mcp-credentials";
            if (args.stream().anyMatch(a -> a.contains("metadata.labels"))) return "store-id";
            if (args.stream().anyMatch(a -> a.contains("consumer-key"))) return "a2V5";
            if (args.stream().anyMatch(a -> a.contains("consumer-secret"))) return "c2VjcmV0";
            return "";
        });
        assertEquals(new KubernetesStoreAdapter.StoreApiCredentials("key", "secret"),
                adapter.ensureWooCommerceCredentials("ecommerce-store-3", "ecommerce-store-3", "store-id"));
        verify(runner).run(argThat(args -> args.contains("plugin") && args.contains("install") && args.contains("woocommerce")
                && args.contains("-c") && args.contains("wordpress") && args.contains("--force")
                && args.contains("WP_CLI_CACHE_DIR=/tmp/wp-cli-cache")), isNull(), any(Duration.class), anyString());
    }

    @Test void reusingNamespaceUsesDifferentStorageForEachNewStoreIdentity() throws Exception {
        String first = renderProvision("11111111-1111-1111-1111-111111111111", true);
        String second = renderProvision("22222222-2222-2222-2222-222222222222", true);
        assertTrue(first.contains("existingClaim: wordpress-11111111-1111-1111-1111-111111111111"));
        assertTrue(first.contains("existingClaim: mariadb-11111111-1111-1111-1111-111111111111"));
        assertFalse(second.contains("11111111-1111-1111-1111-111111111111"));
        assertTrue(second.contains("existingClaim: mariadb-22222222-2222-2222-2222-222222222222"));
    }

    @Test void retryingLegacyStoreDoesNotSwitchItsStorage() throws Exception {
        String values = renderProvision("legacy-id", false);
        assertFalse(values.contains("existingClaim:"));
    }

    private String renderProvision(String id, boolean fresh) throws Exception {
        KubernetesCommandRunner runner = mock(KubernetesCommandRunner.class);
        KubernetesStoreAdapter adapter = new KubernetesStoreAdapter("kubectl", "", "helm", "", "minikube", "", "127.0.0.1", "", "../helm/store-template");
        ReflectionTestUtils.setField(adapter, "commandRunner", runner);
        List<String> values = new ArrayList<>();
        when(runner.run(anyList(), nullable(String.class), any(Duration.class), anyString())).thenAnswer(invocation -> {
            List<String> args = invocation.getArgument(0);
            if (args.contains("current-context")) return "minikube";
            if (args.stream().anyMatch(a -> a.contains("storage-generation"))) return fresh ? id : "";
            if (args.stream().anyMatch(a -> a.contains("metadata.labels"))) return id;
            if (!fresh && args.contains("namespace") && args.contains("--ignore-not-found")) return "ecommerce-store-2";
            if (args.contains("upgrade")) values.add(Files.readString(Path.of(args.get(args.indexOf("--values") + 1))));
            return "";
        });
        adapter.provision("ecommerce-store-2", "ecommerce-store-2", id, 38082, "wordpress", "db", "root");
        assertEquals(1, values.size());
        return values.getFirst();
    }
}

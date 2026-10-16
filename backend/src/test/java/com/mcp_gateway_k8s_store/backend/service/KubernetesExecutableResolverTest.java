package com.mcp_gateway_k8s_store.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KubernetesExecutableResolverTest {
    @TempDir Path temp;

    @Test
    void explicitPathWinsOverPathSearch() throws Exception {
        Path explicit = Files.createFile(temp.resolve("kubectl-custom"));
        Path pathDirectory = Files.createDirectory(temp.resolve("path"));
        Files.createFile(pathDirectory.resolve(commandFileName("kubectl")));

        String resolved = KubernetesExecutableResolver.resolve("kubectl", explicit.toString(), "kubectl", pathDirectory.toString());

        assertEquals(explicit.toAbsolutePath().normalize().toString(), resolved);
    }

    @Test
    void searchesPathAndSupportsWindowsExeNames() throws Exception {
        Path pathDirectory = Files.createDirectory(temp.resolve("path"));
        String executableName = commandFileName("kubectl");
        Path executable = Files.createFile(pathDirectory.resolve(executableName));

        String resolved = KubernetesExecutableResolver.resolve("kubectl", "", "kubectl", pathDirectory.toString());

        assertEquals(executable.toAbsolutePath().normalize().toString(), resolved);
        assertTrue(KubernetesExecutableResolver.candidateNames("kubectl", true).contains("kubectl.exe"));
    }

    @Test
    void missingExecutableProvidesConfigurationGuidance() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> KubernetesExecutableResolver.resolve("kubectl", "", "kubectl", temp.toString()));
        assertTrue(error.getMessage().contains("KUBECTL_PATH"));
        assertTrue(error.getMessage().contains("stores.kubernetes.kubectl-path"));
    }

    private String commandFileName(String command) {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? command + ".exe" : command;
    }
}

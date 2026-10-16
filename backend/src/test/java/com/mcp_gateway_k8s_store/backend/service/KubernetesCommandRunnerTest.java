package com.mcp_gateway_k8s_store.backend.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class KubernetesCommandRunnerTest {
    @Test
    void capturesOutputAndWritesInputToChildProcess() {
        FakeProcess process = new FakeProcess(0, "ok\n", false);
        KubernetesCommandRunner runner = new KubernetesCommandRunner(builder -> process);

        String output = runner.run(List.of("kubectl", "create", "-f", "-"), "manifest", Duration.ofSeconds(1), "config");

        assertEquals("ok", output);
        assertEquals("manifest", process.stdin.toString());
    }

    @Test
    void reportsNonzeroExitAndCapturedDiagnostic() {
        KubernetesCommandRunner runner = new KubernetesCommandRunner(builder -> new FakeProcess(7, "forbidden", false));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runner.run(List.of("kubectl", "get", "pods"), null, Duration.ofSeconds(1), ""));

        assertTrue(error.getMessage().contains("exited with 7"));
        assertTrue(error.getMessage().contains("forbidden"));
    }

    @Test
    void killsTimedOutCommand() {
        FakeProcess process = new FakeProcess(0, "", true);
        KubernetesCommandRunner runner = new KubernetesCommandRunner(builder -> process);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> runner.run(List.of("helm", "upgrade"), null, Duration.ofMillis(1), ""));

        assertTrue(error.getMessage().contains("Command timed out"));
        assertTrue(process.destroyed);
    }

    private static final class FakeProcess extends Process {
        private final int exitCode;
        private final byte[] output;
        private final boolean timeout;
        private boolean destroyed;
        private final ByteArrayOutputStream stdin = new ByteArrayOutputStream();

        private FakeProcess(int exitCode, String output, boolean timeout) {
            this.exitCode = exitCode;
            this.output = output.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            this.timeout = timeout;
        }
        @Override public OutputStream getOutputStream() { return stdin; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(output); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return exitCode; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return !this.timeout; }
        @Override public int exitValue() { return exitCode; }
        @Override public void destroy() { destroyed = true; }
        @Override public Process destroyForcibly() { destroyed = true; return this; }
        @Override public boolean isAlive() { return !destroyed && timeout; }
    }
}

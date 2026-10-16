package com.mcp_gateway_k8s_store.backend.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs fixed executable argument vectors with bounded execution and captured diagnostics. */
public final class KubernetesCommandRunner {
    @FunctionalInterface
    interface ProcessLauncher { Process start(ProcessBuilder builder) throws IOException; }

    private final ProcessLauncher launcher;

    public KubernetesCommandRunner() { this(ProcessBuilder::start); }

    KubernetesCommandRunner(ProcessLauncher launcher) { this.launcher = launcher; }

    public String run(List<String> args, String stdin, Duration timeout, String kubeconfig) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
            if (kubeconfig != null && !kubeconfig.isBlank()) builder.environment().put("KUBECONFIG", kubeconfig);
            process = launcher.start(builder);
            if (stdin != null) {
                process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().close();
            }
            Process running = process;
            var reader = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try (var input = new BufferedReader(new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder captured = new StringBuilder();
                    String line;
                    int lines = 0;
                    while ((line = input.readLine()) != null) {
                        if (lines++ < 100) {
                            if (!captured.isEmpty()) captured.append('\n');
                            captured.append(line);
                        }
                    }
                    return captured.toString();
                } catch (IOException e) { return ""; }
            });
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                throw new IllegalStateException("Command timed out: " + args.get(0));
            }
            String output = reader.get(3, TimeUnit.SECONDS);
            if (process.exitValue() != 0)
                throw new IllegalStateException(args.get(0) + " exited with " + process.exitValue() + (output.isBlank() ? "" : ": " + output));
            return output;
        } catch (InterruptedException e) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running Kubernetes command: " + args.get(0), e);
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            String message = e.getMessage() == null ? "command failed" : e.getMessage();
            if (e instanceof IOException)
                message = "Could not start executable '" + args.get(0) + "'. Check its path and that the container or user can execute it. " + message;
            message = message.replaceAll("(?i)(password|secret|token)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2[redacted]");
            throw new IllegalStateException(message, e);
        }
    }
}

package com.mcp_gateway_k8s_store.backend.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Resolves Kubernetes CLIs lazily so a missing CLI does not prevent Spring Boot from starting. */
public final class KubernetesExecutableResolver {
    private KubernetesExecutableResolver() { }

    public static String resolve(String tool, String explicitPath, String configuredCommand, String pathValue) {
        String explicit = trimToNull(explicitPath);
        String command = trimToNull(configuredCommand);
        String requested = explicit != null ? explicit : command;
        if (requested == null) requested = tool;

        Path requestedPath = Path.of(requested);
        if (requestedPath.isAbsolute() || requested.contains("/") || requested.contains("\\")) {
            if (Files.isRegularFile(requestedPath)) return requestedPath.toAbsolutePath().normalize().toString();
            throw unavailable(tool, requested);
        }

        List<String> names = candidateNames(requested, isWindows());
        if (pathValue != null) {
            for (String directory : pathValue.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (directory.isBlank()) continue;
                for (String name : names) {
                    Path candidate = Path.of(directory, name);
                    if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize().toString();
                }
            }
        }
        throw unavailable(tool, requested);
    }

    static List<String> candidateNames(String command, boolean windows) {
        List<String> names = new ArrayList<>();
        names.add(command);
        if (windows && !command.toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) names.add(command + ".exe");
        return names;
    }

    private static IllegalStateException unavailable(String tool, String configured) {
        String variable = tool.equalsIgnoreCase("helm") ? "HELM_PATH" : "KUBECTL_PATH";
        return new IllegalStateException("Could not locate " + tool + " executable ('" + configured + "'). Install it or set " + variable + " / stores.kubernetes." + tool + "-path to its full path.");
    }

    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"); }
    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

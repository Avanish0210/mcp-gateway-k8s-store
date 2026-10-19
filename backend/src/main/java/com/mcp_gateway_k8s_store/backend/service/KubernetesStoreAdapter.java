package com.mcp_gateway_k8s_store.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import jakarta.annotation.PreDestroy;

/** Bounded, argument-safe boundary for kubectl and Helm operations. */
@Component
public class KubernetesStoreAdapter {
    private final String kubectl;
    private final String kubectlPath;
    private final String helm;
    private final String helmPath;
    private final String context;
    private final String kubeconfig;
    private final String portForwardAddress;
    private final Path chart;
    private final String woocommercePluginVersion;
    private final Map<String, Process> forwards = new ConcurrentHashMap<>();
    private final KubernetesCommandRunner commandRunner = new KubernetesCommandRunner();

    public KubernetesStoreAdapter(
            @Value("${stores.kubernetes.kubectl: kubectl}") String kubectl,
            @Value("${stores.kubernetes.kubectl-path:}") String kubectlPath,
            @Value("${stores.kubernetes.helm:helm}") String helm,
            @Value("${stores.kubernetes.helm-path:}") String helmPath,
            @Value("${stores.kubernetes.context:minikube}") String context,
            @Value("${stores.kubernetes.kubeconfig:}") String kubeconfig,
            @Value("${stores.kubernetes.port-forward-address:127.0.0.1}") String portForwardAddress,
            @Value("${stores.woocommerce.plugin-version:}") String woocommercePluginVersion,
            @Value("${stores.helm.chart:../helm/store-template}") String chart) {
        this.kubectl = kubectl.trim(); this.kubectlPath = kubectlPath; this.helm = helm; this.helmPath = helmPath;
        this.context = context; this.kubeconfig = kubeconfig;
        this.portForwardAddress = portForwardAddress; this.woocommercePluginVersion = woocommercePluginVersion;
        Path configuredChart = Path.of(chart);
        this.chart = configuredChart.isAbsolute() ? configuredChart : Path.of(System.getProperty("user.dir")).resolve(configuredChart).normalize();
    }

    public void provision(String namespace, String release, String storeId, int localPort,
                          String wordpressPassword, String databasePassword, String databaseRootPassword) {
        verifyContext();
        safe(namespace, release);
        String existing = run(List.of(kubectl, "--context", context, "get", "namespace", namespace, "--ignore-not-found", "-o", "jsonpath={.metadata.name}"), null, Duration.ofSeconds(20)).trim();
        if (existing.isBlank()) {
            run(List.of(kubectl, "--context", context, "create", "-f", "-"), namespaceYaml(namespace, storeId), Duration.ofSeconds(30));
        } else {
            String owner = run(List.of(kubectl, "--context", context, "get", "namespace", namespace, "-o", "jsonpath={.metadata.labels.mcp-gateway\\/store-id}"), null, Duration.ofSeconds(20)).trim();
            if (!storeId.equals(owner)) throw new IllegalStateException("Target namespace already exists and is not owned by this store");
        }
        ensureSecret(namespace, "wordpress-credentials", storeId,
                "apiVersion: v1\nkind: Secret\nmetadata:\n  name: wordpress-credentials\n  labels:\n    app.kubernetes.io/managed-by: mcp-gateway\n    mcp-gateway/store-id: \"" + storeId + "\"\ntype: Opaque\ndata:\n  wordpress-password: " + b64(wordpressPassword) + "\n");
        ensureSecret(namespace, "mariadb-credentials", storeId,
                "apiVersion: v1\nkind: Secret\nmetadata:\n  name: mariadb-credentials\n  labels:\n    app.kubernetes.io/managed-by: mcp-gateway\n    mcp-gateway/store-id: \"" + storeId + "\"\ntype: Opaque\ndata:\n  mariadb-password: " + b64(databasePassword) + "\n  mariadb-root-password: " + b64(databaseRootPassword) + "\n  mariadb-replication-password: " + b64(databaseRootPassword) + "\n");
        // Values are stored in a short-lived file instead of process arguments, avoiding credential exposure in process listings.
        Path values = null;
        try {
            values = Files.createTempFile("managed-store-", ".yaml");
            String labels = "    app.kubernetes.io/managed-by: mcp-gateway\n    mcp-gateway/store-id: \"" + storeId + "\"\n";
            String yaml = "store:\n  id: \"" + storeId + "\"\n  localPort: " + localPort + "\nwordpress:\n  wordpressHost: 127.0.0.1:" + localPort + "\n  wordpressScheme: http\n  commonLabels:\n" + labels + "  podLabels:\n" + labels
                    + "\n  mariadb:\n    commonLabels:\n" + labels + "    podLabels:\n" + labels + "    auth:\n      existingSecret: mariadb-credentials\n";
            Files.writeString(values, yaml, StandardCharsets.UTF_8);
            run(List.of(helm, "upgrade", "--install", release, chart.toString(), "--kube-context", context,
                    "--namespace", namespace, "--create-namespace", "--wait", "--timeout", "12m",
                    "--values", values.toString()), null, Duration.ofMinutes(13));
        } catch (Exception e) { throw new IllegalStateException("Helm provisioning failed: " + safeMessage(e), e); }
        finally { if (values != null) try { Files.deleteIfExists(values); } catch (Exception ignored) { } }
        run(List.of(kubectl, "--context", context, "-n", namespace, "wait", "--for=condition=Ready", "pod", "-l", "app.kubernetes.io/instance=" + release, "--timeout=180s"), null, Duration.ofSeconds(190));
        run(List.of(kubectl, "--context", context, "-n", namespace, "get", "service", release + "-wordpress", "-o", "name"), null, Duration.ofSeconds(20));
    }

    public void deleteManaged(String namespace, String release, String storeId) {
        safe(namespace, release);
        verifyContext();
        if (namespace.equals("ecommerce") || release.equals("ecommerce-store") || !namespace.matches("ecommerce-store-[23]") || !namespace.equals(release))
            throw new IllegalArgumentException("Refusing deletion outside an explicitly managed store namespace");
        String exists = run(List.of(kubectl, "--context", context, "get", "namespace", namespace, "--ignore-not-found", "-o", "jsonpath={.metadata.name}"), null, Duration.ofSeconds(20)).trim();
        if (exists.isBlank()) { stopPortForward(namespace); return; }
        String owner = run(List.of(kubectl, "--context", context, "get", "namespace", namespace, "-o", "jsonpath={.metadata.labels.mcp-gateway\\/store-id}"), null, Duration.ofSeconds(20)).trim();
        if (!storeId.equals(owner)) throw new IllegalStateException("Namespace ownership could not be verified; refusing deletion");
        stopPortForward(namespace);
        String installed = run(List.of(helm, "list", "--kube-context", context, "--namespace", namespace, "--filter", "^" + release + "$", "-q"), null, Duration.ofSeconds(20)).trim();
        if (!installed.isBlank()) run(List.of(helm, "uninstall", release, "--kube-context", context, "--namespace", namespace, "--wait", "--timeout", "5m"), null, Duration.ofMinutes(6));
        run(List.of(kubectl, "--context", context, "delete", "namespace", namespace, "--wait=true", "--timeout=120s"), null, Duration.ofSeconds(130));
    }

    public void cleanupFailed(String namespace, String release, String storeId) {
        verifyContext();
        safe(namespace, release);
        String owner;
        try { owner = run(List.of(kubectl, "--context", context, "get", "namespace", namespace, "-o", "jsonpath={.metadata.labels.mcp-gateway\\/store-id}"), null, Duration.ofSeconds(20)).trim(); }
        catch (Exception absent) { return; }
        if (!storeId.equals(owner)) return;
        stopPortForward(namespace);
        try { run(List.of(helm, "uninstall", release, "--kube-context", context, "--namespace", namespace, "--wait", "--timeout", "5m"), null, Duration.ofMinutes(6)); } catch (Exception ignored) { }
        run(List.of(kubectl, "--context", context, "delete", "namespace", namespace, "--wait=true", "--timeout=120s"), null, Duration.ofSeconds(130));
    }

    public void verifyEndpoint(String namespace, String release, int port) {
        verifyContext();
        run(List.of(kubectl, "--context", context, "-n", namespace, "get", "service", release + "-wordpress"), null, Duration.ofSeconds(20));
        ensurePortForward(namespace, release, port);
        try {
            HttpResponse<Void> response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(5)).build()
                    .send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                                    .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 400)
                throw new IllegalStateException("Store endpoint returned HTTP " + response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while checking store endpoint", e);
        } catch (java.io.IOException e) { throw new IllegalStateException("Could not reach the store endpoint", e); }
    }
    public String hostUrl(int port) { return "http://127.0.0.1:" + port + "/"; }

    public StoreApiCredentials ensureWooCommerceCredentials(String namespace, String release, String storeId) {
        verifyContext();
        String name = "woocommerce-mcp-credentials";
        String present = run(List.of(kubectl, "--context", context, "-n", namespace, "get", "secret", name, "--ignore-not-found", "-o", "jsonpath={.metadata.name}"), null, Duration.ofSeconds(20)).trim();
        if (!present.isBlank()) {
            String owner = run(List.of(kubectl, "--context", context, "-n", namespace, "get", "secret", name, "-o", "jsonpath={.metadata.labels.mcp-gateway\\/store-id}"), null, Duration.ofSeconds(20)).trim();
            if (!storeId.equals(owner)) throw new IllegalStateException("WooCommerce credential Secret ownership could not be verified");
            return new StoreApiCredentials(secret(namespace, name, "consumer-key"), secret(namespace, name, "consumer-secret"));
        }
        ensureWooCommercePlugin(namespace, release);
        String php = "$u=get_user_by('login','admin');if(!$u){fwrite(STDERR,'admin user unavailable');exit(2);}$ck='ck_'.wc_rand_hash();$cs='cs_'.wc_rand_hash();global $wpdb;$inserted=$wpdb->insert($wpdb->prefix.'woocommerce_api_keys',['user_id'=>$u->ID,'description'=>'MCP Gateway " + storeId + "','permissions'=>'read_write','consumer_key'=>wc_api_hash($ck),'consumer_secret'=>$cs,'truncated_key'=>substr($ck,-7)],['%d','%s','%s','%s','%s','%s']);if($inserted!==1){fwrite(STDERR,'WooCommerce API key insert failed: '.$wpdb->last_error);exit(3);}echo wp_json_encode(['consumerKey'=>$ck,'consumerSecret'=>$cs]);";
        String output = run(List.of(kubectl, "--context", context, "-n", namespace, "exec", "deployment/" + release + "-wordpress", "--", "wp", "eval", php, "--allow-root"), null, Duration.ofSeconds(30)).trim();
        String key = jsonValue(output, "consumerKey"), consumerSecret = jsonValue(output, "consumerSecret");
        String labels = "  labels:\n    app.kubernetes.io/managed-by: mcp-gateway\n    mcp-gateway/store-id: \"" + storeId + "\"\n";
        String manifest = "apiVersion: v1\nkind: Secret\nmetadata:\n  name: " + name + "\n" + labels + "type: Opaque\ndata:\n  consumer-key: " + b64(key) + "\n  consumer-secret: " + b64(consumerSecret) + "\n";
        ensureSecret(namespace, name, storeId, manifest);
        return new StoreApiCredentials(key, consumerSecret);
    }

    private void ensureWooCommercePlugin(String namespace, String release) {
        String pluginList = run(List.of(kubectl, "--context", context, "-n", namespace, "exec", "deployment/" + release + "-wordpress", "--", "wp", "plugin", "list", "--format=json", "--allow-root"), null, Duration.ofSeconds(30));
        boolean installed = java.util.regex.Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"woocommerce\\\"").matcher(pluginList).find();
        if (!installed) {
            List<String> install = new java.util.ArrayList<>(List.of(kubectl, "--context", context, "-n", namespace, "exec", "deployment/" + release + "-wordpress", "--", "wp", "plugin", "install", "woocommerce"));
            if (woocommercePluginVersion != null && !woocommercePluginVersion.isBlank()) install.add("--version=" + woocommercePluginVersion.trim());
            install.addAll(List.of("--activate", "--allow-root"));
            run(install, null, Duration.ofMinutes(3));
        } else {
            run(List.of(kubectl, "--context", context, "-n", namespace, "exec", "deployment/" + release + "-wordpress", "--", "wp", "plugin", "activate", "woocommerce", "--allow-root"), null, Duration.ofSeconds(30));
        }
    }

    private String secret(String namespace, String name, String key) {
        String encoded = run(List.of(kubectl, "--context", context, "-n", namespace, "get", "secret", name, "-o", "jsonpath={.data." + key + "}"), null, Duration.ofSeconds(20)).trim();
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
    private String jsonValue(String json, String key) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        if (!matcher.find()) throw new IllegalStateException("WooCommerce credential creation returned an unexpected response");
        return matcher.group(1);
    }
    public record StoreApiCredentials(String consumerKey, String consumerSecret) { }

    private void ensurePortForward(String namespace, String release, int port) {
        Process current = forwards.get(namespace);
        if (current != null && current.isAlive() && isListening(port)) return;
        if (current != null) { current.destroyForcibly(); forwards.remove(namespace, current); }
        if (isListening(port)) throw new IllegalStateException("The reserved local store port is already in use");
        try {
            Process process = new ProcessBuilder(resolveKubectl(), "--context", context, "-n", namespace,
                    "port-forward", "service/" + release + "-wordpress", port + ":80", "--address=" + portForwardAddress)
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            forwards.put(namespace, process);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (System.nanoTime() < deadline) {
                if (!process.isAlive()) throw new IllegalStateException("Store access forward exited before becoming ready");
                if (isListening(port)) return;
                Thread.sleep(250);
            }
            stopPortForward(namespace);
            throw new IllegalStateException("Timed out starting the local store access forward");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while starting store access", e); }
        catch (java.io.IOException e) { throw new IllegalStateException("Could not start kubectl port-forward", e); }
    }
    private boolean isListening(int port) {
        try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1", port), 200); return true; }
        catch (Exception ignored) { return false; }
    }
    private void verifyContext() {
        String selected = run(List.of(kubectl, "config", "current-context"), null, Duration.ofSeconds(15)).trim();
        if (!context.equals(selected)) throw new IllegalStateException("Kubernetes current context does not match the configured managed-store context");
        verifyApiConnectivity();
    }

    /** Fails quickly and clearly before a managed-store request is persisted. */
    public void verifyClusterReady() { verifyContext(); }
    private void verifyApiConnectivity() {
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                run(List.of(kubectl, "--context", context, "get", "--raw=/readyz"), null, Duration.ofSeconds(15));
                return;
            } catch (IllegalStateException failure) {
                String message = failure.getMessage() == null ? "" : failure.getMessage();
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("forbidden") || lower.contains("unauthorized") || lower.contains("provide credentials"))
                    throw new IllegalStateException("Kubernetes authorization failed for context '" + context + "'. Check the configured kubeconfig user and permissions.", failure);
                if (!isConnectivityFailure(lower)) throw failure;
                last = failure;
                if (attempt < 3) {
                    try { Thread.sleep(attempt * 400L); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while checking Kubernetes API connectivity", e); }
                }
            }
        }
        String detail = last == null ? "no response from /readyz" : last.getMessage();
        throw new IllegalStateException("Kubernetes API is unreachable for context '" + context + "'. Check minikube status and refresh the container kubeconfig after Minikube changes its API endpoint. " + detail, last);
    }
    private boolean isConnectivityFailure(String message) {
        return message.contains("connection refused") || message.contains("couldn't get current server api group list")
                || message.contains("unable to connect to the server") || message.contains("i/o timeout")
                || message.contains("no such host") || message.contains("dial tcp")
                || message.contains("context deadline exceeded") || message.contains("connection reset");
    }
    private String resolveKubectl() {
        return KubernetesExecutableResolver.resolve("kubectl", kubectlPath, kubectl, System.getenv("PATH"));
    }
    private String resolveHelm() {
        return KubernetesExecutableResolver.resolve("helm", helmPath, helm, System.getenv("PATH"));
    }
    private void stopPortForward(String namespace) {
        Process process = forwards.remove(namespace);
        if (process != null) process.destroyForcibly();
    }
    @PreDestroy
    public void closePortForwards() { forwards.keySet().forEach(this::stopPortForward); }

    private void ensureSecret(String namespace, String name, String storeId, String manifest) {
        String existing = run(List.of(kubectl, "--context", context, "-n", namespace, "get", "secret", name, "--ignore-not-found", "-o", "jsonpath={.metadata.name}"), null, Duration.ofSeconds(20)).trim();
        if (existing.isBlank()) {
            run(List.of(kubectl, "--context", context, "-n", namespace, "create", "-f", "-"), manifest, Duration.ofSeconds(30));
            return;
        }
        String owner = run(List.of(kubectl, "--context", context, "-n", namespace, "get", "secret", name, "-o", "jsonpath={.metadata.labels.mcp-gateway\\/store-id}"), null, Duration.ofSeconds(20)).trim();
        if (!storeId.equals(owner)) throw new IllegalStateException("Credential Secret exists without this store's ownership label");
    }

    private String namespaceYaml(String ns, String id) { return "apiVersion: v1\nkind: Namespace\nmetadata:\n  name: " + ns + "\n  labels:\n    app.kubernetes.io/managed-by: mcp-gateway\n    mcp-gateway/store-id: \"" + id + "\"\n"; }
    private String b64(String v) { return Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8)); }
    private void safe(String namespace, String release) {
        if (!namespace.matches("ecommerce-store-[23]") || !release.matches("ecommerce-store-[23]") || !namespace.equals(release) || namespace.equals("ecommerce") || release.equals("ecommerce-store"))
            throw new IllegalArgumentException("Invalid managed store target");
    }
    private String run(List<String> args, String stdin, Duration timeout) {
        List<String> resolved = new java.util.ArrayList<>(args);
        if (!resolved.isEmpty()) {
            String command = resolved.get(0);
            if (command.equals(kubectl)) resolved.set(0, resolveKubectl());
            else if (command.equals(helm)) resolved.set(0, resolveHelm());
        }
        return commandRunner.run(resolved, stdin, timeout, kubeconfig);
    }
    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "command failed";
        return message.replaceAll("(?i)(password|secret|token)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2[redacted]");
    }
}

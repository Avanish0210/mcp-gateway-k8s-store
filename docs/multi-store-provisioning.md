# Multi-store provisioning (local Minikube)

This guide manages only `ecommerce-store-2` and `ecommerce-store-3`. Store 1 remains in namespace `ecommerce` under Helm release `ecommerce-store`; these procedures never uninstall or modify it.

## Prerequisites and context check

Use PowerShell from the repository root. Minikube, Docker Desktop, `kubectl`, Helm, Java 21, Maven, Node.js, and PostgreSQL must be installed and running. The backend uses the configured database and never changes Kubernetes context automatically.

```powershell
minikube status
kubectl config current-context
kubectl config get-contexts
kubectl get namespace ecommerce
helm list --namespace ecommerce
kubectl get pods,services,pvc --namespace ecommerce
```

Confirm the current context is the intended Minikube cluster before starting the backend. Store 1's current NodePort is `30909`; on this Windows host the Minikube IP route timed out during the read-only check, so the managed stores use loopback port forwards instead.

## Build and start

Build the WooCommerce MCP process and validate the chart:

```powershell
Push-Location .\mcp-servers\woocommerce
npm ci
npm run typecheck
npm run build
Pop-Location

helm dependency list .\helm\store-template
helm show values .\helm\store-template\charts\wordpress-34.1.3.tgz | Select-String 'existingSecret|allowEmptyPassword|service:|type:|persistence:|size:|mariadb:'
helm lint .\helm\store-template
helm template ecommerce-store-2 .\helm\store-template --namespace ecommerce-store-2 --set store.id=00000000-0000-0000-0000-000000000002
```

Configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the backend process environment or its local secret manager. For Docker Compose, prepare a flattened, container-readable kubeconfig and rebuild the backend image. The helper verifies that the active context is Minikube, embeds the local certificates, and rewrites a loopback API endpoint to Docker Desktop's host gateway. The generated file contains client credentials and is gitignored.

```powershell
Push-Location .
.\scripts\Prepare-DockerKubeconfig.ps1
docker compose -f .\backend\compose.yaml build backend
docker compose -f .\backend\compose.yaml up -d backend
docker compose -f .\backend\compose.yaml logs --tail 100 backend
Pop-Location
```

The image includes `kubectl` and Helm. Compose mounts `.docker/kubeconfig` read-only and publishes the managed store forwards on host loopback ports 38082 and 38083. If you run Spring Boot directly on Windows instead, set `KUBERNETES_CONTEXT=minikube`, `KUBECONFIG`, `KUBECTL_EXECUTABLE`, `HELM_EXECUTABLE`, and `STORE_HELM_CHART` to paths available to that process.

```powershell
Push-Location .\backend
mvn test
mvn spring-boot:run
```

In a second PowerShell window:

```powershell
Push-Location .\dashboard
npm ci
npm run dev
```

The backend exposes authenticated management APIs on port 8081. Spring Security HTTP Basic remains the authentication method. Obtain the local backend credentials from the configured secret source; do not put them in source control or paste them into shell history.

## Create and monitor stores

The maximum is three total: the existing Store 1 plus two managed stores. Failed records retain their namespace reservation until a successful retry or deletion, preventing unsafe namespace reuse.

Open the dashboard's **Stores** tab to create stores, watch lifecycle states, retry failures, inspect registered tools, open a READY store, and confirm deletion. The dashboard polls while provisioning or deleting.

PowerShell API example (the credential prompt does not echo the password):

```powershell
$credential = Get-Credential
$pair = '{0}:{1}' -f $credential.UserName, $credential.GetNetworkCredential().Password
$headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair)) }
$body = @{ name = 'Store 2' } | ConvertTo-Json
$created = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/stores' -Headers $headers -ContentType 'application/json' -Body $body
$created | Select-Object id, name, namespace, status
```

Poll until the status is `READY` or `FAILED`:

```powershell
do {
  Start-Sleep -Seconds 5
  $stores = Invoke-RestMethod -Uri 'http://localhost:8081/api/stores' -Headers $headers
  $store = $stores | Where-Object id -eq $created.id
  $store | Select-Object name, namespace, status, storeUrl, errorMessage
} while ($store.status -in @('PROVISIONING', 'DELETING'))
```

Create Store 3 using the same POST with `name = 'Store 3'`. A further create request returns HTTP 409. The API returns a store ID while the asynchronous worker provisions it; it does not wait for Helm in the request.

## Inspect a managed store

Use the exact namespace returned by the API and confirm it is one of the managed namespaces before any manual cleanup:

```powershell
$namespace = $store.namespace
if ($namespace -notin @('ecommerce-store-2', 'ecommerce-store-3')) { throw 'Refusing to inspect an unmanaged namespace' }
kubectl get namespace $namespace --show-labels
kubectl get pods,services,pvc,resourcequota,limitrange,networkpolicy -n $namespace
helm list -n $namespace
kubectl get secrets -n $namespace -o custom-columns=NAME:.metadata.name,TYPE:.type
```

Secret output intentionally shows names and types only. Do not decode Secret values for routine diagnosis. Each managed namespace gets unique WordPress, MariaDB, and WooCommerce API credential Secrets; the bundled WordPress chart uses the two existing credential Secrets, 2Gi claims, pinned chart-compatible images, CPU/memory requests and limits, a quota and a LimitRange.

The local URL is `http://127.0.0.1:38082/` for Store 2 or `http://127.0.0.1:38083/` for Store 3. In Compose, kubectl listens on the container interface while Docker publishes each port on host loopback only. The backend checks the HTTP endpoint, then publishes the URL. It stops that process during deletion and recreates it when reconciling a healthy store after restart. The URL works only while the backend and Minikube are running on the same workstation; it is not a LAN or production address.

To change local resource settings, edit `helm/store-template/values.yaml`: `wordpress.persistence.size`, `wordpress.mariadb.primary.persistence.size`, the two containers' requests/limits, `resourceQuota`, and `limitRange` are the managed-store defaults. The chart is pinned to its bundled WordPress 34.1.3 / MariaDB 28.1.1 dependencies. Its local-development image overrides use archived Bitnami Legacy images because the old `bitnami/*` versioned tags are no longer published publicly. Legacy images no longer receive security updates and are only a short-term compatibility bridge; mirror them into a registry you control or migrate to supported images before production. Re-run `helm lint` and `helm template` after changes. NetworkPolicy objects are rendered by the chart; actual enforcement depends on the Minikube cluster's CNI implementation.

## Store-scoped WooCommerce tools

Read the store's ID from the list response, then:

```powershell
$storeId = $store.id
Invoke-RestMethod -Uri "http://localhost:8081/api/woocommerce/store-tools?storeId=$storeId" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8081/api/woocommerce/products?storeId=$storeId&page=1&perPage=10" -Headers $headers
```

Product, order, and customer endpoints accept the same `storeId` query parameter. Omitting it preserves the existing Store 1 WooCommerce client and API behavior. The management API never returns WordPress or WooCommerce credentials.

The first-run bootstrap uses the WordPress CLI in the new WordPress deployment to create a unique read/write WooCommerce API key for its admin user, stores it in a Kubernetes Secret, starts an isolated MCP process with that store's URL and key, checks the expected tool list, and performs a `list_products` connectivity call. If the WordPress CLI, WooCommerce plugin, endpoint, or MCP process is unavailable, the store does not become READY. Any resources in its correctly-owned namespace remain available for diagnosis and retry; cleanup occurs only through an explicit managed-store deletion.

## Retry and safe deletion

Retry a failed provision or failed teardown by clicking **Retry** in the dashboard or:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/stores/$($store.id)/retry" -Headers $headers
```

Delete only after confirming the exact managed namespace and store ID. The dashboard requires a confirmation dialog. The explicit API operation is:

```powershell
if ($store.namespace -notin @('ecommerce-store-2', 'ecommerce-store-3')) { throw 'Refusing to delete an unmanaged namespace' }
Invoke-RestMethod -Method Delete -Uri "http://localhost:8081/api/stores/$($store.id)" -Headers $headers
```

Poll `GET /api/stores` until the ID is absent. Deletion unregisters and closes the store MCP client, stops the local forward, validates the namespace ownership label and ID, uninstalls only the matching release, and removes only that namespace. Kubernetes removes its PVC objects with the namespace; the backing PV outcome follows the Minikube StorageClass reclaim policy. Never run cleanup commands against `ecommerce` or uninstall `ecommerce-store`.

## Recovery and diagnosis

At backend startup, recorded `PROVISIONING` stores resume idempotently, `DELETING` stores resume teardown, and READY stores have their workload, local endpoint, saved API Secret, and MCP client revalidated. Credentials are kept in Kubernetes Secrets rather than database columns; the namespace ownership label and unique namespace/release pair make the recovery target explicit. This local implementation assumes one backend instance; the create-slot synchronization is process-local.

Common checks:

```powershell
kubectl config current-context
minikube status
kubectl get pods -n ecommerce-store-2 -o wide
$podName = 'replace-with-the-pod-name-from-kubectl-get-pods'
kubectl describe pod -n ecommerce-store-2 $podName
kubectl get events -n ecommerce-store-2 --sort-by=.lastTimestamp
helm status ecommerce-store-2 -n ecommerce-store-2
```

Check backend logs for redacted command and connectivity diagnostics. Do not print Secrets or credentials. If a failure remains, retry from the dashboard after fixing the underlying Minikube, image pull, storage, database, or MCP issue.

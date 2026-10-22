param(
    [Parameter(Mandatory)][ValidatePattern('^ecommerce-store-[23]$')][string]$Namespace,
    [Parameter(Mandatory)][string]$StoreId,
    [string]$BackendContainer = 'backend-backend-1'
)
$ErrorActionPreference = 'Stop'
$owner = (& docker exec $BackendContainer kubectl -n $Namespace get namespace $Namespace -o 'jsonpath={.metadata.labels.mcp-gateway\/store-id}').Trim()
if ($LASTEXITCODE -ne 0 -or $owner -ne $StoreId) { throw 'Store namespace ownership did not match; no changes made.' }
$containerName = (& docker exec $BackendContainer kubectl -n $Namespace get statefulset "$Namespace-mariadb" -o 'jsonpath={.spec.template.spec.containers[0].name}').Trim()
if ($LASTEXITCODE -ne 0 -or $containerName -ne 'mariadb') { throw 'Unexpected database workload; no changes made.' }
$patch = foreach ($entry in @(@('startupProbe', 60), @('livenessProbe', 6), @('readinessProbe', 3))) {
    @{
        op = 'add'
        path = "/spec/template/spec/containers/0/$($entry[0])"
        value = @{tcpSocket=@{port=3306}; periodSeconds=10; timeoutSeconds=5; failureThreshold=$entry[1]}
    }
}
$patch | ConvertTo-Json -Depth 8 -Compress | & docker exec -i $BackendContainer kubectl -n $Namespace patch statefulset "$Namespace-mariadb" --type=json --patch-file=/dev/stdin
if ($LASTEXITCODE -ne 0) { throw 'Database probe update failed.' }
$targetRevision = (& docker exec $BackendContainer kubectl -n $Namespace get statefulset "$Namespace-mariadb" -o 'jsonpath={.status.updateRevision}').Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect database rollout revision.' }
$podRevision = (& docker exec $BackendContainer kubectl -n $Namespace get pod "$Namespace-mariadb-0" --ignore-not-found -o 'jsonpath={.metadata.labels.controller-revision-hash}').Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect database pod revision.' }
if ($podRevision -and $podRevision -ne $targetRevision) {
    # OrderedReady can wait indefinitely for a pod with the old failing probe.
    # Recreate that pod from the corrected template; the PVC is retained.
    & docker exec $BackendContainer kubectl -n $Namespace delete pod "$Namespace-mariadb-0" --wait=true --timeout=60s
    if ($LASTEXITCODE -ne 0) { throw 'Could not replace the old database pod.' }
}
& docker exec $BackendContainer kubectl -n $Namespace rollout status statefulset "$Namespace-mariadb" --timeout=180s
if ($LASTEXITCODE -ne 0) { throw 'Database rollout did not become ready; inspect the pod before further changes.' }

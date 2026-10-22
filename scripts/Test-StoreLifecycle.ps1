param([switch]$IncludeClusterRestart, [string]$ResumeVerificationStoreId, [string]$VerifyRecreatedStoreId)
$ErrorActionPreference = 'Stop'
$env:GOMEMLIMIT = '96MiB'
$env:GOMAXPROCS = '2'
$base = 'http://127.0.0.1:8081'
$backend = 'backend-backend-1'
$testName = 'Store lifecycle verification'
$marker = 'Recovery-test-' + [Guid]::NewGuid().ToString('N')
$headers = @{}
$createdIds = [Collections.Generic.List[string]]::new()

function Login {
    $container = (& docker inspect $backend | ConvertFrom-Json)[0]
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect backend for local test login.' }
    $settings = @{}
    foreach ($entry in $container.Config.Env) {
        $parts = $entry -split '=', 2
        $settings[$parts[0]] = $parts[1]
    }
    $body = @{username=$settings.INITIAL_ADMIN_USERNAME; password=$settings.INITIAL_ADMIN_PASSWORD} | ConvertTo-Json
    $session = Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 15
    $script:headers = @{Authorization='Bearer ' + $session.accessToken}
}
function Api([string]$method, [string]$path, $body = $null) {
    $options = @{Uri="$base$path"; Method=$method; Headers=$script:headers; TimeoutSec=120}
    if ($null -ne $body) { $options.ContentType='application/json'; $options.Body=($body | ConvertTo-Json -Depth 8) }
    $response = Invoke-RestMethod @options
    # Invoke-RestMethod can emit an entire JSON array as one pipeline object.
    # Enumerate explicitly so list filters/counts inspect individual stores.
    foreach ($entry in $response) { $entry }
}
function Wait-Ready([string]$id) {
    $deadline = [DateTime]::UtcNow.AddMinutes(16)
    $lastStatus = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        $store = Api Get "/api/stores/$id"
        if ($store.status -ne $lastStatus) { Write-Host "$($store.name): $($store.status)"; $lastStatus=$store.status }
        if ($store.status -eq 'READY') {
            $health = Api Get "/api/woocommerce/status?storeId=$id"
            if ($health.available) { return $store }
        }
        if ($store.status -eq 'FAILED') { throw $store.errorMessage }
        Start-Sleep -Seconds 5
    }
    throw "Timed out waiting for store $id"
}
function Wait-Backend {
    $deadline = [DateTime]::UtcNow.AddMinutes(3)
    while ([DateTime]::UtcNow -lt $deadline) {
        try { Login; return } catch { Start-Sleep -Seconds 3 }
    }
    throw 'Backend did not become available after restart.'
}
function Products([string]$id) { @(Api Get "/api/woocommerce/products?storeId=$id&perPage=100") }
function Delete-TestStore([string]$id) {
    # Only IDs created by this invocation are eligible for test cleanup.
    if (-not $createdIds.Contains($id)) { throw 'Refusing to delete a store not created by this test.' }
    $null = Api Delete "/api/stores/$id"
    $deadline = [DateTime]::UtcNow.AddMinutes(8)
    while ([DateTime]::UtcNow -lt $deadline) {
        try { $remaining = Api Get "/api/stores/$id" }
        catch {
            if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404) {
                Write-Host 'PASS: test store deleted'; return
            }
            throw
        }
        if ($remaining.errorMessage) { throw $remaining.errorMessage }
        Start-Sleep -Seconds 3
    }
    throw 'Store deletion did not complete.'
}

Wait-Backend
$before = @(Api Get '/api/stores')
if ($before.Count -ge 3 -and -not $ResumeVerificationStoreId -and -not $VerifyRecreatedStoreId) { throw 'No free store slot. Existing stores will not be deleted by this test.' }
try {
    if ($VerifyRecreatedStoreId) {
        $recreated = Wait-Ready $VerifyRecreatedStoreId
        if ($recreated.name -ne $testName) { throw 'Refusing cleanup of a store without the verification name.' }
        $createdIds.Add($recreated.id)
        if ((Products $recreated.id).Count -ne 0) { throw 'Recreated verification store contains old products.' }
        Write-Host 'PASS: recreated verification store has zero products after engine restart'
        Delete-TestStore $recreated.id
        return
    }
    if ($ResumeVerificationStoreId) {
        $first = Api Get "/api/stores/$ResumeVerificationStoreId"
        $first = Wait-Ready $first.id
        $existingProducts = Products $first.id
        if ($first.name -ne $testName -or $existingProducts.Count -ne 1 -or $existingProducts[0].name -notlike 'Recovery-test-*') {
            throw 'Refusing to resume a store without the verification name and sole test marker product.'
        }
        $marker = $existingProducts[0].name
    } else {
        $first = Api Post '/api/stores' @{name=$testName}
    }
    $createdIds.Add($first.id)
    $first = Wait-Ready $first.id
    if (-not $ResumeVerificationStoreId) {
        if ((Products $first.id).Count -ne 0) { throw 'A newly created store contains existing products.' }
        Write-Host 'PASS: fresh store has zero products'
        $null = Api Post "/api/woocommerce/products?storeId=$($first.id)" @{name=$marker; regularPrice='1.00'; description='Temporary lifecycle test'; type='simple'}
    }
    $chat = Api Post '/api/chat' @{message='List the product names in my selected store using the products tool.'; storeId=$first.id}
    if ($chat.reply -notlike "*$marker*") { throw 'Chat did not return the actual test product.' }
    Write-Host 'PASS: chat retrieved the selected store product through MCP'

    & docker restart $backend | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Backend restart failed.' }
    Wait-Backend
    $null = Wait-Ready $first.id
    if (-not ((Products $first.id) | Where-Object name -eq $marker)) { throw 'Existing store data did not survive backend restart.' }
    Write-Host 'PASS: backend restart rebuilt access and preserved data'

    $port = if ($first.namespace.EndsWith('2')) { '38082:80' } else { '38083:80' }
    & docker exec $backend node -e 'const fs=require("fs");for(const pid of fs.readdirSync("/proc").filter(x=>/^\d+$/.test(x))){try{const a=fs.readFileSync("/proc/"+pid+"/cmdline","utf8").split("\0");if(a.includes("port-forward")&&a.includes(process.argv[1])){process.kill(Number(pid),"SIGTERM");console.log("Stopped test-store forward");}}catch{}}' $port
    if ($LASTEXITCODE -ne 0) { throw 'Port-forward fault injection failed.' }
    Start-Sleep -Seconds 20
    $null = Wait-Ready $first.id
    if (-not ((Products $first.id) | Where-Object name -eq $marker)) { throw 'Port-forward did not recover.' }
    Write-Host 'PASS: supervised port-forward recovered'

    if ($IncludeClusterRestart) {
        try {
            & docker stop minikube | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Minikube stop failed.' }
            Start-Sleep -Seconds 25
            & docker restart $backend | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Backend restart during cluster outage failed.' }
            Wait-Backend
            $state = Api Get "/api/stores/$($first.id)"
            if ($state.status -eq 'FAILED') { throw 'Temporary cluster outage became a permanent store failure.' }
            $greeting = Api Post '/api/chat' @{message='Reply with the word hello. Do not use integration tools.'; storeId=$first.id}
            if ([string]::IsNullOrWhiteSpace($greeting.reply)) { throw 'Ordinary chat failed during store recovery.' }
            Write-Host 'PASS: ordinary chat works while Kubernetes is unavailable'
        } finally {
            # Starting the node container alone does not reliably restart the
            # kubelet/control plane in Minikube; use its supported lifecycle.
            & minikube start -p minikube
            if ($LASTEXITCODE -ne 0) { throw 'Minikube control plane did not restart.' }
        }
        $null = Wait-Ready $first.id
        if (-not ((Products $first.id) | Where-Object name -eq $marker)) { throw 'Store data did not survive cluster restart.' }
        Write-Host 'PASS: backend-before-cluster restart recovered automatically'
    }

    Delete-TestStore $first.id
    $second = Api Post '/api/stores' @{name=$testName}
    $createdIds.Add($second.id)
    $second = Wait-Ready $second.id
    if ($second.id -eq $first.id -or $second.namespace -ne $first.namespace) { throw 'Test did not exercise namespace reuse with a new store identity.' }
    if ((Products $second.id).Count -ne 0) { throw 'Deleted store products leaked into the recreated store.' }
    Write-Host 'PASS: same name and namespace recreated with zero products'
    Delete-TestStore $second.id
    Write-Host 'PASS: lifecycle verification complete; pre-existing stores were preserved'
} catch {
    Write-Warning 'Verification stopped. Any remaining verification stores are listed below for diagnosis.'
    foreach ($id in $createdIds) { Write-Host "Verification store ID: $id" }
    throw
}

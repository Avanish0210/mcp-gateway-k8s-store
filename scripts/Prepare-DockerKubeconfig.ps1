param(
    [string]$Context = 'minikube',
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\.docker\kubeconfig')
)

$ErrorActionPreference = 'Stop'

function Invoke-Kubectl {
    param([string[]]$Arguments)

    $output = & kubectl @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
    return ($output -join [Environment]::NewLine)
}

$currentContext = (Invoke-Kubectl -Arguments @('config', 'current-context')).Trim()
if ($currentContext -ne $Context) {
    throw "Current Kubernetes context is '$currentContext', expected '$Context'. Switch contexts explicitly before preparing the Docker kubeconfig."
}

# Minikube can move its API server to a new host port after a restart. Refresh
# the host kubeconfig first so the Docker copy never keeps the old endpoint.
& minikube update-context -p $Context
if ($LASTEXITCODE -ne 0) {
    throw "minikube update-context failed with exit code $LASTEXITCODE"
}

$configJson = Invoke-Kubectl -Arguments @('config', 'view', '--raw', '--flatten', '--minify', '-o', 'json')
$config = $configJson | ConvertFrom-Json
if (-not $config.clusters -or $config.clusters.Count -ne 1) {
    throw 'The selected context did not produce exactly one Kubernetes cluster.'
}

$serverUri = [Uri]$config.clusters[0].cluster.server
if ($serverUri.Scheme -ne 'https' -or $serverUri.Host -notin @('127.0.0.1', 'localhost', 'host.docker.internal')) {
    throw "Unexpected Minikube API endpoint host '$($serverUri.Host)'; refusing to rewrite it."
}
$builder = [UriBuilder]$serverUri
$builder.Host = 'host.docker.internal'
$config.clusters[0].cluster.server = $builder.Uri.AbsoluteUri.TrimEnd('/')
# Docker reaches the host through host.docker.internal, which is not in
# Minikube's API certificate. Validate the same certificate against its
# localhost SAN while retaining the Docker-reachable address above.
$config.clusters[0].cluster | Add-Member -NotePropertyName 'tls-server-name' -NotePropertyValue 'localhost' -Force

$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Path $parent -Force | Out-Null
$config | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $resolvedOutput -Encoding utf8
Write-Host "Docker kubeconfig refreshed for context '$Context' at $resolvedOutput"

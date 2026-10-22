param(
    [string]$Context = 'minikube',
    [switch]$ForceRecreate,
    [switch]$NoDeps,
    [switch]$Build,
    [switch]$SkipBuild,
    [string[]]$Services
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repoRoot
try {
    foreach ($tool in @('kubectl', 'minikube', 'docker')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            throw "Required command '$tool' was not found on PATH. Install it before starting the Compose stack."
        }
    }

    $currentContext = (& kubectl config current-context).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the current kubectl context.' }
    if ($currentContext -ne $Context) {
        throw "Current Kubernetes context is '$currentContext', expected '$Context'. Switch contexts explicitly before starting the Compose stack."
    }

    $clusterState = (& minikube status -p $Context --format='{{.Host}} {{.Kubelet}} {{.APIServer}}' 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or $clusterState -ne 'Running Running Running') {
        Write-Host "Starting Minikube profile '$Context'..."
        & minikube start -p $Context
        if ($LASTEXITCODE -ne 0) { throw "Minikube profile '$Context' could not be started." }
    }

    # Some local setups start Compose from a shell that supplies secrets but do
    # not keep a root .env file. Reuse the existing backend's Compose settings
    # for an in-place rebuild, without printing any secret values.
    $composeVariables = @(
        'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'SPRING_AI_MCP_CLIENT_ENABLED',
        'GOOGLE_API_KEY', 'GITHUB_PERSONAL_ACCESS_TOKEN', 'WOOCOMMERCE_URL', 'DASHBOARD_ALLOWED_ORIGIN',
        'KUBERNETES_CONTEXT', 'STORES_WOOCOMMERCE_PLUGIN_VERSION', 'JWT_SECRET', 'JWT_EXPIRATION_SECONDS',
        'SPRING_PROFILES_ACTIVE', 'TEAM_INVITATION_EXPIRATION_HOURS', 'TEAM_INVITATION_DEV_LINKS_ENABLED',
        'INVITATION_FRONTEND_BASE_URL', 'INITIAL_ADMIN_USERNAME', 'INITIAL_ADMIN_PASSWORD',
        'INITIAL_MEMBER_USERNAME', 'INITIAL_MEMBER_PASSWORD',
        'GEMINI_CHAT_MODEL', 'GEMINI_FALLBACK_MODEL',
        'TEAM_INVITATION_EMAIL_ENABLED', 'TEAM_INVITATION_FROM', 'SPRING_MAIL_HOST',
        'SPRING_MAIL_PORT', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD',
        'SPRING_MAIL_SMTP_AUTH', 'SPRING_MAIL_SMTP_STARTTLS'
    )
    $hasRootEnv = Test-Path '.env' -PathType Leaf
    $existingBackend = & docker ps -aq --filter 'label=com.docker.compose.project=backend' --filter 'label=com.docker.compose.service=backend' | Select-Object -First 1
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the current backend container.' }
    if ($existingBackend) { $existingBackend = $existingBackend.Trim() }
    if ($existingBackend -and -not $hasRootEnv) {
        $container = (& docker inspect $existingBackend | ConvertFrom-Json)[0]
        foreach ($entry in $container.Config.Env) {
            $parts = $entry -split '=', 2
            if ($parts.Count -eq 2 -and $composeVariables -contains $parts[0] -and -not [Environment]::GetEnvironmentVariable($parts[0], 'Process')) {
                [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
            }
        }
    }
    if (-not $hasRootEnv -and (-not [Environment]::GetEnvironmentVariable('GOOGLE_API_KEY', 'Process') -or
        -not [Environment]::GetEnvironmentVariable('GITHUB_PERSONAL_ACCESS_TOKEN', 'Process'))) {
        throw 'GOOGLE_API_KEY and GITHUB_PERSONAL_ACCESS_TOKEN must be set in the root .env file, the current environment, or the existing backend container.'
    }

    # Refresh certificate/context material before Compose bind-mounts the
    # Docker-facing kubeconfig. The helper rewrites the endpoint to the stable
    # Minikube in-network address, avoiding Minikube's changing host API port.
    & (Join-Path $PSScriptRoot 'Prepare-DockerKubeconfig.ps1') -Context $Context
    if ($LASTEXITCODE -ne 0) { throw 'Docker kubeconfig refresh failed; Compose was not started.' }

    $ComposeArguments = @()
    if (Test-Path '.env' -PathType Leaf) { $ComposeArguments += @('--env-file', '.env') }
    $ComposeArguments += @('-f', 'backend/compose.yaml', 'up', '--detach')
    # Routine restarts should reuse the image instead of rebuilding the whole
    # stack and pulling fresh base images while the laptop is starting up.
    if ($Build -and -not $SkipBuild) { $ComposeArguments += '--build' }
    if ($ForceRecreate) { $ComposeArguments += '--force-recreate' }
    if ($NoDeps) { $ComposeArguments += '--no-deps' }
    if ($Services) { $ComposeArguments += $Services }
    & docker compose @ComposeArguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

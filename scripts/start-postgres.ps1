$ErrorActionPreference = 'Stop'

$workspace = Split-Path -Parent $PSScriptRoot
$baseCompose = Join-Path $workspace 'docker-compose.yml'
$devCompose = Join-Path $workspace 'docker-compose.dev.yml'

Push-Location $workspace
try {
    docker compose -f $baseCompose -f $devCompose config --quiet
    docker compose -f $baseCompose -f $devCompose up -d postgres
    docker compose -f $baseCompose -f $devCompose ps postgres
} finally {
    Pop-Location
}

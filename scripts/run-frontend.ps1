# Start frontend: install deps (first run) and launch Vite dev server
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location (Join-Path $root 'frontend')

if (-not (Test-Path 'node_modules')) {
    Write-Host "First run: installing frontend dependencies..."
    npm ci --cache (Join-Path $root '.npm-cache')
}
npm run dev

# Start backend: load .env into env vars, then run Spring Boot (keys not hardcoded, not committed)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'

if (-not (Test-Path $envFile)) {
    Write-Error "Missing .env file. Copy .env.example to .env and fill in your keys first."
}

Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $idx = $line.IndexOf('=')
        if ($idx -gt 0) {
            $key = $line.Substring(0, $idx).Trim()
            $val = $line.Substring($idx + 1).Trim()
            [Environment]::SetEnvironmentVariable($key, $val, 'Process')
        }
    }
}

Set-Location (Join-Path $root 'backend')
$mavenSettings = Join-Path $root 'maven-settings.xml'
if (Test-Path $mavenSettings) {
    mvn -s $mavenSettings spring-boot:run
} else {
    Write-Host 'maven-settings.xml not found; using the default Maven configuration.'
    mvn spring-boot:run
}

param(
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [string]$ContainerName = 'techstack-postgres'
)

$ErrorActionPreference = 'Stop'

$workspace = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
if ($resolvedOutput.StartsWith($workspace, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw '脱敏迁移包必须保存在仓库外，避免被提交到 Git。'
}

$outputDirectory = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

$containerDump = '/tmp/devguide-sanitized.dump'
try {
    docker exec $ContainerName sh -lc 'rm -f /tmp/devguide-sanitized.dump && pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges --exclude-table-data=public.users --exclude-table-data=public.user_tokens --file=/tmp/devguide-sanitized.dump'
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump 脱敏导出失败。' }

    docker cp ($ContainerName + ':' + $containerDump) $resolvedOutput
    if ($LASTEXITCODE -ne 0) { throw '无法把脱敏迁移包复制到仓库外路径。' }

    Write-Host ('脱敏迁移包已生成：' + $resolvedOutput)
    Write-Host '下一步必须执行 verify-sanitized-restore.ps1 完成隔离恢复验证。'
} finally {
    docker exec $ContainerName rm -f $containerDump 2>$null
}

param(
    [Parameter(Mandatory = $true)]
    [string]$BackupPath,
    [int]$ExpectedTechStacks = 26,
    [int]$ExpectedVectorStore = 44
)

$ErrorActionPreference = 'Stop'

$resolvedBackup = (Resolve-Path -LiteralPath $BackupPath).Path
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$container = 'devguide-sanitized-verify-' + $suffix
$password = [Guid]::NewGuid().ToString('N')
$containerDump = '/tmp/devguide-sanitized.dump'

function Invoke-Checked {
    param([scriptblock]$Command, [string]$Message)
    & $Command
    if ($LASTEXITCODE -ne 0) { throw $Message }
}

try {
    Invoke-Checked { docker run -d --name $container -e ('POSTGRES_PASSWORD=' + $password) pgvector/pgvector:pg16 } '无法启动隔离 PostgreSQL。'

    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        docker exec $container pg_isready -U postgres -d postgres *> $null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw '隔离 PostgreSQL 未在 30 秒内就绪。' }

    Invoke-Checked { docker exec $container createdb -U postgres verifydb } '创建隔离验证数据库失败。'
    Invoke-Checked { docker cp $resolvedBackup ($container + ':' + $containerDump) } '复制迁移包到隔离容器失败。'
    Invoke-Checked { docker exec $container pg_restore -U postgres -d verifydb --no-owner --no-privileges $containerDump } '隔离恢复失败。'

    $sql = @'
SELECT 'tech_stacks=' || count(*) FROM tech_stacks;
SELECT 'vector_store=' || count(*) FROM vector_store;
SELECT 'users=' || count(*) FROM users;
SELECT 'user_tokens=' || count(*) FROM user_tokens;
SELECT 'flyway=' || count(*) FROM flyway_schema_history;
SELECT 'vector_extension=' || count(*) FROM pg_extension WHERE extname = 'vector';
SELECT 'vectors_present=' || count(*) FROM vector_store WHERE embedding IS NOT NULL;
'@
    $result = docker exec $container psql -U postgres -d verifydb -Atc $sql
    if ($LASTEXITCODE -ne 0) { throw '恢复后的数据验证查询失败。' }
    $result | ForEach-Object { Write-Host $_ }

    $values = @{}
    $result | ForEach-Object {
        $parts = $_ -split '=', 2
        if ($parts.Count -eq 2) { $values[$parts[0]] = [int]$parts[1] }
    }

    if ($values['tech_stacks'] -ne $ExpectedTechStacks) { throw 'tech_stacks 计数不符合预期。' }
    if ($values['vector_store'] -ne $ExpectedVectorStore) { throw 'vector_store 计数不符合预期。' }
    if ($values['users'] -ne 0 -or $values['user_tokens'] -ne 0) { throw '脱敏失败：用户或 Token 数据仍存在。' }
    if ($values['flyway'] -lt 1) { throw '缺少 Flyway 历史。' }
    if ($values['vector_extension'] -ne 1 -or $values['vectors_present'] -lt 1) { throw 'PGvector 扩展或向量数据验证失败。' }

    Write-Host '隔离恢复验证通过：展示内容完整，用户与 Token 行均为 0。'
} finally {
    docker rm -f $container 2>$null | Out-Null
}

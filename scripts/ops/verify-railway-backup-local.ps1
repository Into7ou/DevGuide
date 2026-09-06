# Restore only into fresh, network-isolated containers. No production connection.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$MetadataPath,
    [Parameter(Mandatory)][string]$AgeExe,
    [Parameter(Mandatory)][string]$IdentityPath,
    [string]$PostgresImage = 'pgvector/pgvector:pg16',
    [string]$BackendImage = 'ghcr.io/into7ou/devguide-backend:v0.7.0'
)
$ErrorActionPreference = 'Stop'
$meta = Get-Content -LiteralPath $MetadataPath -Raw | ConvertFrom-Json
if ((Get-FileHash -LiteralPath $meta.backupPath -Algorithm SHA256).Hash -ne $meta.ciphertextSha256) { throw 'Ciphertext checksum mismatch' }
$started = [DateTime]::UtcNow
$runId = [Guid]::NewGuid().ToString('N')
$db = $null; $backend = $null; $bytes = $null; $network = $null
$savedPassword = $env:POSTGRES_PASSWORD
$savedCipherKey = $env:TOKEN_CIPHER_KEY
function Start-RedirectedProcess([string]$Executable, [string[]]$Arguments, [bool]$InputStream, [bool]$OutputStream) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Executable; $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $InputStream; $info.RedirectStandardOutput = $OutputStream; $info.RedirectStandardError = $true
    foreach ($arg in $Arguments) { $info.ArgumentList.Add($arg) }
    return [Diagnostics.Process]::Start($info)
}
try {
    $ageProcess = Start-RedirectedProcess $AgeExe @('--decrypt', '--identity', $IdentityPath, $meta.backupPath) $false $true
    $ageErrors = $ageProcess.StandardError.ReadToEndAsync()
    $memory = [IO.MemoryStream]::new()
    $ageProcess.StandardOutput.BaseStream.CopyTo($memory)
    $ageProcess.WaitForExit()
    if ($ageProcess.ExitCode -ne 0) { throw 'Backup decryption failed' }
    $bytes = $memory.ToArray()
    [Array]::Clear($memory.GetBuffer(), 0, [int]$memory.Length); $memory.Dispose()
    if ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)) -ne $meta.plaintextSha256) { throw 'Decrypted archive checksum mismatch' }
    $env:POSTGRES_PASSWORD = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $env:TOKEN_CIPHER_KEY = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    # MyBatis-Plus needs a non-loopback interface. Internal bridge blocks external access.
    $network = (& docker network create --internal --label "devguide.backup-verification=$runId" "devguide-backup-$runId").Trim()
    if ($LASTEXITCODE -ne 0 -or $network -notmatch '^[a-f0-9]{64}$') { $network = $null; throw 'Isolated network creation failed' }
    $db = (& docker run -d --pull never --network $network --network-alias restoredb --name "devguide-backup-db-$runId" --label "devguide.backup-verification=$runId" -e POSTGRES_PASSWORD -e POSTGRES_DB=verifydb $PostgresImage).Trim()
    if ($LASTEXITCODE -ne 0 -or $db -notmatch '^[a-f0-9]{64}$') { $db = $null; throw 'Isolated database container creation failed' }
    $ready = $false
    for ($attempt=0; $attempt -lt 45; $attempt++) {
        & docker exec $db pg_isready -U postgres -d verifydb *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'Isolated PostgreSQL not ready' }
    $restoreStart = [DateTime]::UtcNow
    $dockerExe = (Get-Command docker).Source
    $restore = Start-RedirectedProcess $dockerExe @('exec', '-i', $db, 'pg_restore', '--exit-on-error', '--no-owner', '--no-privileges', '-U', 'postgres', '-d', 'verifydb') $true $true
    $restoreErrors = $restore.StandardError.ReadToEndAsync()
    $restoreOutput = $restore.StandardOutput.ReadToEndAsync()
    $restore.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
    $restore.StandardInput.Close(); $restore.WaitForExit()
    if ($restore.ExitCode -ne 0) { throw 'pg_restore failed; database content is not printed' }
    $restoreSeconds = [math]::Round(([DateTime]::UtcNow - $restoreStart).TotalSeconds, 2)
    $sql = @'
SELECT 'server_version='||current_setting('server_version');
SELECT 'tech_stacks='||count(*) FROM tech_stacks;
SELECT 'vector_store='||count(*) FROM vector_store;
SELECT 'users='||count(*) FROM users;
SELECT 'user_tokens='||count(*) FROM user_tokens;
SELECT 'flyway='||count(*) FROM flyway_schema_history;
SELECT 'flyway_failed='||count(*) FROM flyway_schema_history WHERE NOT success;
SELECT 'vector_version='||extversion FROM pg_extension WHERE extname='vector';
SELECT 'vectors_present='||count(*) FROM vector_store WHERE embedding IS NOT NULL;
SELECT 'vector_dimension='||vector_dims(embedding) FROM vector_store WHERE embedding IS NOT NULL LIMIT 1;
SELECT 'nearest_distance='||(SELECT embedding <=> sample.embedding FROM vector_store ORDER BY embedding <=> sample.embedding LIMIT 1) FROM (SELECT embedding FROM vector_store WHERE embedding IS NOT NULL LIMIT 1) sample;
SELECT 'orphan_tokens='||count(*) FROM user_tokens t LEFT JOIN users u ON u.id=t.user_id WHERE u.id IS NULL;
SELECT 'empty_tokens='||count(*) FROM user_tokens WHERE length(access_token)=0;
'@
    $rows = @(& docker exec $db psql -X -v ON_ERROR_STOP=1 -U postgres -d verifydb -At -c $sql)
    if ($LASTEXITCODE -ne 0) { throw 'Restored database SQL verification failed' }
    $actual = [ordered]@{}
    foreach ($row in $rows) { if ($row -match '^([^=]+)=(.*)$') { $actual[$Matches[1]] = $Matches[2] } }
    foreach ($key in @('tech_stacks','vector_store','users','user_tokens','flyway','flyway_failed','vectors_present','vector_dimension')) {
        if ($actual[$key] -ne $meta.baseline.$key -or $meta.baseline.$key -ne $meta.after.$key) { throw "Baseline mismatch: $key" }
    }
    if ($actual.flyway_failed -ne '0' -or $actual.orphan_tokens -ne '0' -or $actual.empty_tokens -ne '0') { throw 'Restored data integrity check failed' }
    if (!$actual.Contains('nearest_distance') -or [math]::Abs([double]::Parse($actual.nearest_distance, [Globalization.CultureInfo]::InvariantCulture)) -gt 0.00001) { throw 'Vector similarity check failed' }
    Write-Output 'Database restore, baseline and vector checks passed; starting isolated backend.'
    $backend = (& docker run -d --pull never --network $network --name "devguide-backup-app-$runId" --label "devguide.backup-verification=$runId" -e POSTGRES_PASSWORD -e TOKEN_CIPHER_KEY -e POSTGRES_HOST=restoredb -e POSTGRES_PORT=5432 -e POSTGRES_DB=verifydb -e POSTGRES_USER=postgres -e SPRING_PROFILES_ACTIVE=prod -e FRONTEND_BASE_URL=https://localhost -e DEEPSEEK_API_KEY=isolated-backup-verification -e DASHSCOPE_API_KEY=isolated-backup-verification -e GITHUB_OAUTH_CLIENT_ID=isolated-backup-verification -e GITHUB_OAUTH_CLIENT_SECRET=isolated-backup-verification $BackendImage).Trim()
    if ($LASTEXITCODE -ne 0 -or $backend -notmatch '^[a-f0-9]{64}$') { $backend = $null; throw 'Isolated backend creation failed' }
    $healthy = $false
    for ($attempt=0; $attempt -lt 90; $attempt++) {
        $running = & docker inspect --format '{{.State.Running}}' $backend
        if ($running -ne 'true') { throw 'Isolated backend exited before becoming healthy' }
        $health = @(& docker exec $backend wget -q -O - http://localhost:18080/api/health 2>$null) -join ''
        if ($LASTEXITCODE -eq 0 -and ($health | ConvertFrom-Json).status -eq 'UP') { $healthy = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$healthy) { throw 'Isolated backend health check failed; inspect its sanitized logs before retrying' }
    $showcase = @(& docker exec $backend wget -q -O - http://localhost:18080/api/v1/showcase/tech-stacks) -join ''
    if ($LASTEXITCODE -ne 0) { throw 'Restored application read failed' }
    $itemCount = @($showcase | ConvertFrom-Json).Count
    if ($itemCount -ne [int]$meta.baseline.tech_stacks) { throw 'Restored application count mismatch' }
    $logs = @(& docker exec $backend cat /tmp/devguide-logs/devguide.log) -join "`n"
    if ($logs -notmatch 'Successfully validated (\d+) migrations') { ($logs -split "`n") | Where-Object { $_ -match 'flyway|[Mm]igrat|[Vv]alidat' } | Select-Object -Last 12 | Write-Output; throw 'Expected Flyway validation evidence missing' }
    $verification = [ordered]@{
        completedUtc = [DateTime]::UtcNow.ToString('o')
        pgRestoreSeconds = $restoreSeconds
        totalSeconds = [math]::Round(([DateTime]::UtcNow - $started).TotalSeconds, 2)
        actual = $actual
        backendImage = $BackendImage
        health = 'UP'
        showcaseCount = $itemCount
        flywayValidated = $true
        validatedMigrationFiles = [int]$Matches[1]
        network = 'internal Docker bridge; no external access or published ports'
        oauthAndTokenDecryptionTested = $false
    }
    $meta.restoreVerified = $true
    $meta | Add-Member -NotePropertyName verification -NotePropertyValue $verification -Force
    [IO.File]::WriteAllText($MetadataPath, ($meta | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    $verification | ConvertTo-Json -Depth 6
} finally {
    if ($bytes) { [Array]::Clear($bytes, 0, $bytes.Length) }
    foreach ($id in @($backend, $db)) {
        if ($id) {
            $owner = & docker inspect --format '{{index .Config.Labels "devguide.backup-verification"}}' $id
            if ($LASTEXITCODE -eq 0 -and $owner -eq $runId) {
                & docker rm -f -v $id | Out-Null
                if ($LASTEXITCODE -ne 0) { Write-Warning "Temporary container cleanup failed: $id" }
            }
        }
    }
    if ($network) {
        $owner = & docker network inspect --format '{{index .Labels "devguide.backup-verification"}}' $network
        if ($LASTEXITCODE -eq 0 -and $owner -eq $runId) {
            & docker network rm $network | Out-Null
            if ($LASTEXITCODE -ne 0) { Write-Warning "Temporary network cleanup failed: $network" }
        }
    }
    if ($null -eq $savedPassword) { Remove-Item Env:POSTGRES_PASSWORD -ErrorAction SilentlyContinue } else { $env:POSTGRES_PASSWORD = $savedPassword }
    if ($null -eq $savedCipherKey) { Remove-Item Env:TOKEN_CIPHER_KEY -ErrorAction SilentlyContinue } else { $env:TOKEN_CIPHER_KEY = $savedCipherKey }
}

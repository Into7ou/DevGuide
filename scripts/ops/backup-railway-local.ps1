# Requires PowerShell 7, an authenticated Railway CLI 4.20.0 and age.
# Full production data is received in memory over TLS and only ciphertext is saved.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RailwayExe,
    [Parameter(Mandatory)][string]$AgeDirectory,
    [Parameter(Mandatory)][string]$ProjectId,
    [Parameter(Mandatory)][string]$EnvironmentId,
    [Parameter(Mandatory)][string]$ServiceId,
    [Parameter(Mandatory)][string]$BackupDirectory,
    [Parameter(Mandatory)][string]$IdentityPath
)
$ErrorActionPreference = 'Stop'
$started = [DateTime]::UtcNow
$age = Join-Path $AgeDirectory 'age.exe'
$keygen = Join-Path $AgeDirectory 'age-keygen.exe'
foreach ($tool in @($RailwayExe, $age, $keygen)) {
    if (!(Test-Path -LiteralPath $tool)) { throw "Missing tool: $tool" }
}
if (!(Test-Path -LiteralPath $IdentityPath)) {
    & $keygen -o $IdentityPath 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'age identity generation failed' }
}
$recipient = (& $keygen -y $IdentityPath).Trim()
if ($LASTEXITCODE -ne 0 -or !$recipient.StartsWith('age1')) { throw 'Invalid age recipient' }
New-Item -ItemType Directory -Path $BackupDirectory -Force | Out-Null
$stem = 'devguide-' + $started.ToString('yyyyMMddTHHmmssfffZ')
$backupPath = Join-Path $BackupDirectory ($stem + '.dump.age')
$partialPath = $backupPath + '.partial'
if ((Test-Path -LiteralPath $backupPath) -or (Test-Path -LiteralPath $partialPath)) { throw 'Backup path already exists' }

$sql = @'
SELECT 'database='||current_database();
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
'@
$remoteTemplate = @'
set -eu
printf 'DG_META_BEGIN\n'
psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At <<'DG_SQL'
__SQL__
DG_SQL
printf 'DG_META_END\nDG_DUMP_BEGIN\n'
bash -o pipefail -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges | base64 -w 76'
printf '\nDG_DUMP_END\nDG_AFTER_BEGIN\n'
psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At <<'DG_SQL'
__SQL__
DG_SQL
printf 'DG_AFTER_END\n'
'@
$remote = $remoteTemplate.Replace('__SQL__', $sql).Replace("`r", '')
$cfg = Get-Content -LiteralPath (Join-Path $env:USERPROFILE '.railway/config.json') -Raw | ConvertFrom-Json
if (!$cfg.user.accessToken) { throw 'Railway OAuth login is required' }
$previousToken = $env:RAILWAY_API_TOKEN
$dumpBytes = $null
try {
    $env:RAILWAY_API_TOKEN = $cfg.user.accessToken
    # Never send this captured output to the terminal: it contains full database data.
    $captured = @(& $RailwayExe ssh "--project=$ProjectId" "--environment=$EnvironmentId" "--service=$ServiceId" -- $remote)
    if ($LASTEXITCODE -ne 0) { throw 'Railway database export failed' }
    $exportText = $captured -join "`n"
    $dumpMatch = [regex]::Match($exportText, '(?s)DG_DUMP_BEGIN\s*([A-Za-z0-9+/=\s]+)\s*DG_DUMP_END')
    if (!$dumpMatch.Success) { throw 'Complete database payload markers missing' }
    $dumpBytes = [Convert]::FromBase64String($dumpMatch.Groups[1].Value)
    if ($dumpBytes.Length -lt 5 -or [Text.Encoding]::ASCII.GetString($dumpBytes, 0, 5) -ne 'PGDMP') { throw 'Not a PostgreSQL custom archive' }
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $age
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardError = $true
    foreach ($arg in @('--encrypt', '--recipient', $recipient, '--output', $partialPath)) { $info.ArgumentList.Add($arg) }
    $proc = [Diagnostics.Process]::Start($info)
    $errorTask = $proc.StandardError.ReadToEndAsync()
    $proc.StandardInput.BaseStream.Write($dumpBytes, 0, $dumpBytes.Length)
    $proc.StandardInput.Close()
    $proc.WaitForExit()
    if ($proc.ExitCode -ne 0) { throw 'age encryption failed; partial ciphertext retained for inspection' }
    Move-Item -LiteralPath $partialPath -Destination $backupPath
    $baseline = [ordered]@{}
    $after = [ordered]@{}
    foreach ($pair in @(@('META', $baseline), @('AFTER', $after))) {
        $match = [regex]::Match($exportText, "(?s)DG_$($pair[0])_BEGIN\s*(.*?)\s*DG_$($pair[0])_END")
        if (!$match.Success) { throw 'Database baseline missing; ciphertext retained' }
        foreach ($line in ($match.Groups[1].Value -split "`n")) {
            if ($line.Trim() -match '^([^=]+)=(.*)$') { $pair[1][$Matches[1]] = $Matches[2] }
        }
    }
    $metadata = [ordered]@{
        backupPath = $backupPath
        createdUtc = $started.ToString('o')
        completedUtc = [DateTime]::UtcNow.ToString('o')
        exportAndEncryptionSeconds = [math]::Round(([DateTime]::UtcNow - $started).TotalSeconds, 2)
        projectId = $ProjectId
        environmentId = $EnvironmentId
        serviceId = $ServiceId
        format = 'PostgreSQL custom archive encrypted with age'
        ciphertextBytes = (Get-Item -LiteralPath $backupPath).Length
        ciphertextSha256 = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash
        plaintextSha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($dumpBytes))
        baseline = $baseline
        after = $after
        restoreVerified = $false
    }
    $metadataPath = Join-Path $BackupDirectory ($stem + '.json')
    [IO.File]::WriteAllText($metadataPath, ($metadata | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
    $metadata | ConvertTo-Json -Depth 6
} finally {
    if ($null -eq $previousToken) { Remove-Item Env:RAILWAY_API_TOKEN -ErrorAction SilentlyContinue }
    else { $env:RAILWAY_API_TOKEN = $previousToken }
    if ($dumpBytes) { [Array]::Clear($dumpBytes, 0, $dumpBytes.Length) }
    $captured = $null; $exportText = $null; $dumpMatch = $null; $cfg = $null
}

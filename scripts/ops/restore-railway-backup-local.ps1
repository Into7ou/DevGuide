# Restore an age-encrypted custom archive into a confirmed-empty Railway PostgreSQL database.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$MetadataPath,
    [Parameter(Mandatory)][string]$AgeExe,
    [Parameter(Mandatory)][string]$IdentityPath,
    [Parameter(Mandatory)][string]$RailwayExe,
    [Parameter(Mandatory)][string]$ProjectId,
    [Parameter(Mandatory)][string]$EnvironmentId,
    [Parameter(Mandatory)][string]$ServiceId
)
$ErrorActionPreference = 'Stop'
$meta = Get-Content -LiteralPath $MetadataPath -Raw | ConvertFrom-Json
$remotePath = '/tmp/devguide-restore-' + [Guid]::NewGuid().ToString('N') + '.b64'
$bytes = $null
$encoded = $null
$cfg = Get-Content -LiteralPath (Join-Path $env:USERPROFILE '.railway/config.json') -Raw | ConvertFrom-Json
if (!$cfg.user.accessToken) { throw 'Railway OAuth login is required' }
$savedToken = $env:RAILWAY_API_TOKEN

function Invoke-RailwaySsh([string]$Command) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $RailwayExe
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($arg in @('ssh', "--project=$ProjectId", "--environment=$EnvironmentId", "--service=$ServiceId", '--', $Command)) {
        $info.ArgumentList.Add($arg)
    }
    $process = [Diagnostics.Process]::Start($info)
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) { throw "Railway SSH command failed (exit $($process.ExitCode)); remote output withheld" }
    return $stdout.Trim()
}

try {
    $env:RAILWAY_API_TOKEN = $cfg.user.accessToken
    $empty = Invoke-RailwaySsh 'psql -X -P pager=off -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "SELECT ''DG_EMPTY=''||count(*) FROM pg_tables WHERE schemaname NOT IN (''pg_catalog'',''information_schema'');"'
    $emptyMatch = [regex]::Match($empty, 'DG_EMPTY=(\d+)')
    if (!$emptyMatch.Success -or $emptyMatch.Groups[1].Value -ne '0') {
        throw 'Restore target is not an empty database'
    }

    $ageInfo = [Diagnostics.ProcessStartInfo]::new()
    $ageInfo.FileName = $AgeExe
    $ageInfo.UseShellExecute = $false
    $ageInfo.CreateNoWindow = $true
    $ageInfo.RedirectStandardOutput = $true
    $ageInfo.RedirectStandardError = $true
    foreach ($arg in @('--decrypt', '--identity', $IdentityPath, $meta.backupPath)) { $ageInfo.ArgumentList.Add($arg) }
    $ageProcess = [Diagnostics.Process]::Start($ageInfo)
    $ageErrorTask = $ageProcess.StandardError.ReadToEndAsync()
    $memory = [IO.MemoryStream]::new()
    $ageProcess.StandardOutput.BaseStream.CopyTo($memory)
    $ageProcess.WaitForExit()
    if ($ageProcess.ExitCode -ne 0) { throw 'Backup decryption failed' }
    $bytes = $memory.ToArray()
    [Array]::Clear($memory.GetBuffer(), 0, [int]$memory.Length)
    $memory.Dispose()
    $localHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
    if ($localHash -ne $meta.plaintextSha256) { throw 'Local plaintext checksum mismatch' }

    $encoded = [Convert]::ToBase64String($bytes)
    $chunkSize = 6000
    $chunkCount = [int][Math]::Ceiling($encoded.Length / $chunkSize)
    for ($index = 0; $index -lt $chunkCount; $index++) {
        $length = [Math]::Min($chunkSize, $encoded.Length - ($index * $chunkSize))
        $chunk = $encoded.Substring($index * $chunkSize, $length)
        $redirect = if ($index -eq 0) { '>' } else { '>>' }
        Invoke-RailwaySsh "umask 077; printf '%s' '$chunk' $redirect '$remotePath'" | Out-Null
    }
    $remoteHash = (Invoke-RailwaySsh "base64 -d '$remotePath' | sha256sum | cut -d' ' -f1").ToUpperInvariant()
    if ($remoteHash -ne $meta.plaintextSha256) { throw 'Remote plaintext checksum mismatch' }

    $started = [DateTime]::UtcNow
    Invoke-RailwaySsh "set -eu; base64 -d '$remotePath' | pg_restore --exit-on-error --single-transaction --no-owner --no-privileges -U `"`$POSTGRES_USER`" -d `"`$POSTGRES_DB`"; rm -f '$remotePath'" | Out-Null
    $remotePath = $null
    [ordered]@{
        completedUtc = [DateTime]::UtcNow.ToString('o')
        restoreSeconds = [Math]::Round(([DateTime]::UtcNow - $started).TotalSeconds, 2)
        plaintextSha256Matched = $true
        transferChunks = $chunkCount
        remoteTemporaryFileRemoved = $true
    } | ConvertTo-Json
} finally {
    if ($remotePath -and $env:RAILWAY_API_TOKEN) {
        try { Invoke-RailwaySsh "rm -f '$remotePath'" | Out-Null } catch { Write-Warning 'Remote temporary file cleanup could not be confirmed' }
    }
    if ($bytes) { [Array]::Clear($bytes, 0, $bytes.Length) }
    $encoded = $null
    $cfg = $null
    if ($null -eq $savedToken) { Remove-Item Env:RAILWAY_API_TOKEN -ErrorAction SilentlyContinue }
    else { $env:RAILWAY_API_TOKEN = $savedToken }
}

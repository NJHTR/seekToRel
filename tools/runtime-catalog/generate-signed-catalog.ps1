param(
    [Parameter(Mandatory = $true)] [string]$ImageDigest,
    [Parameter(Mandatory = $true)] [string]$LockFile,
    [Parameter(Mandatory = $true)] [string]$OutputDirectory,
    [string]$DependencyGraphPath = "",
    [string]$PrivateKeyPath = (Join-Path $OutputDirectory "catalog-ed25519-private.pem"),
    [string]$PublicKeyPath = (Join-Path $OutputDirectory "catalog-ed25519-public.pem"),
    [string]$OpenSslPath = ""
)

$ErrorActionPreference = "Stop"

if ($ImageDigest -notmatch '^[A-Za-z0-9./_-]+@sha256:[0-9a-fA-F]{64}$') {
    throw "ImageDigest must be an immutable repository@sha256:<64 hex> reference"
}
if (-not (Test-Path -LiteralPath $LockFile -PathType Leaf)) {
    throw "LockFile does not exist: $LockFile"
}
if ([string]::IsNullOrWhiteSpace($DependencyGraphPath)) {
    $DependencyGraphPath = Join-Path (Split-Path -Parent $LockFile) "catalog-dependencies.json"
}
if (-not (Test-Path -LiteralPath $DependencyGraphPath -PathType Leaf)) {
    throw "DependencyGraphPath does not exist: $DependencyGraphPath"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

if ([string]::IsNullOrWhiteSpace($OpenSslPath)) {
    $command = Get-Command openssl -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        $OpenSslPath = $command.Source
    } elseif (Test-Path "C:\Program Files\Git\usr\bin\openssl.exe") {
        $OpenSslPath = "C:\Program Files\Git\usr\bin\openssl.exe"
    } else {
        throw "openssl is required to generate an Ed25519 detached signature"
    }
}

if (-not (Test-Path -LiteralPath $PrivateKeyPath -PathType Leaf)) {
    & $OpenSslPath genpkey -algorithm Ed25519 -out $PrivateKeyPath
    if ($LASTEXITCODE -ne 0) { throw "openssl could not create the Ed25519 private key" }
}
& $OpenSslPath pkey -in $PrivateKeyPath -pubout -out $PublicKeyPath
if ($LASTEXITCODE -ne 0) { throw "openssl could not derive the Ed25519 public key" }

$entries = [System.Collections.Generic.List[object]]::new()
$currentName = $null
$currentVersion = $null
$currentHashes = [System.Collections.Generic.List[string]]::new()

function Flush-Entry {
    if ($null -eq $script:currentName) { return }
    if ($script:currentHashes.Count -ne 1) {
        throw "Each locked package must have exactly one wheel hash: $($script:currentName)==$($script:currentVersion)"
    }
    $script:entries.Add([pscustomobject]@{
        name = $script:currentName.ToLowerInvariant()
        version = $script:currentVersion
        sha256 = $script:currentHashes[0].ToLowerInvariant()
    })
    $script:currentName = $null
    $script:currentVersion = $null
    $script:currentHashes = [System.Collections.Generic.List[string]]::new()
}

foreach ($line in Get-Content -LiteralPath $LockFile) {
    $trimmed = $line.Trim()
    if ($trimmed -eq "" -or $trimmed.StartsWith("#")) { continue }
    if ($trimmed -match '^--(index-url|extra-index-url)\s+') {
        if ($trimmed -notin @(
                "--index-url https://pypi.org/simple",
                "--extra-index-url https://download.pytorch.org/whl/cpu")) {
            throw "Lock file contains an unapproved package index: $trimmed"
        }
        continue
    }
    if ($trimmed -match '^--(find-links|trusted-host|pre|no-annotate)') {
        throw "Lock file contains an unsafe pip option: $trimmed"
    }
    $package = [regex]::Match($trimmed, '^([A-Za-z0-9][A-Za-z0-9_.-]{0,127})==([0-9][A-Za-z0-9.+!~-]{0,63})')
    if ($package.Success) {
        Flush-Entry
        $currentName = $package.Groups[1].Value
        $currentVersion = $package.Groups[2].Value
    }
    $hashes = [regex]::Matches($trimmed, '--hash=sha256:([0-9a-fA-F]{64})')
    foreach ($hash in $hashes) { $currentHashes.Add($hash.Groups[1].Value) }
}
Flush-Entry

if ($entries.Count -eq 0) { throw "No pinned package entries were found in $LockFile" }
$graphNodes = Get-Content -LiteralPath $DependencyGraphPath -Raw | ConvertFrom-Json
$graphByKey = @{}
foreach ($node in $graphNodes) {
    if ($null -eq $node.name -or $null -eq $node.version -or $null -eq $node.sha256) {
        throw "Dependency graph contains an incomplete package entry"
    }
    $key = "$($node.name.ToLowerInvariant())==$($node.version)"
    if ($graphByKey.ContainsKey($key)) { throw "Dependency graph contains duplicate package: $key" }
    $graphByKey[$key] = $node
}

$lockKeys = @{}
foreach ($entry in $entries) {
    $key = "$($entry.name)==$($entry.version)"
    $lockKeys[$key] = $true
    if (-not $graphByKey.ContainsKey($key)) { throw "Dependency graph is missing lock package: $key" }
    $node = $graphByKey[$key]
    if ($node.sha256.ToLowerInvariant() -ne $entry.sha256.ToLowerInvariant()) {
        throw "Dependency graph hash does not match lock file: $key"
    }
}
foreach ($key in $graphByKey.Keys) {
    if (-not $lockKeys.ContainsKey($key)) { throw "Dependency graph contains package absent from lock file: $key" }
}

$dependencies = @($entries | Sort-Object name, version | ForEach-Object {
    $key = "$($_.name)==$($_.version)"
    $node = $graphByKey[$key]
    $requires = @()
    foreach ($required in @($node.requires)) {
        if ($null -eq $required.name -or $null -eq $required.version) {
            throw "Dependency graph contains an incomplete requirement for $key"
        }
        $requiredKey = "$($required.name.ToLowerInvariant())==$($required.version)"
        if (-not $graphByKey.ContainsKey($requiredKey)) {
            throw "Dependency graph requirement is not approved: $requiredKey"
        }
        $requires += [ordered]@{ name = $required.name.ToLowerInvariant(); version = $required.version }
    }
    [ordered]@{
        name = $_.name
        version = $_.version
        sha256 = $_.sha256
        requires = @($requires | Sort-Object name, version)
    }
})
$catalog = [ordered]@{
    schemaVersion = 1
    profiles = @([ordered]@{
        language = "python"
        imageDigest = $ImageDigest
        capabilityProfile = "scientific"
    })
    dependencies = $dependencies
}

$catalogPath = Join-Path $OutputDirectory "scientific.catalog.json"
$signaturePath = Join-Path $OutputDirectory "scientific.catalog.sig"
$json = $catalog | ConvertTo-Json -Depth 8 -Compress
[IO.File]::WriteAllText($catalogPath, $json, [Text.UTF8Encoding]::new($false))

& $OpenSslPath pkeyutl -sign -rawin -inkey $PrivateKeyPath -in $catalogPath -out $signaturePath 2>$null
if ($LASTEXITCODE -ne 0) {
    # OpenSSL 3.2 requires -rawin for Ed25519, while older builds accepted the
    # same operation without it. Keep the release script portable across the
    # trusted Windows build hosts we support.
    & $OpenSslPath pkeyutl -sign -inkey $PrivateKeyPath -in $catalogPath -out $signaturePath 2>$null
}
if ($LASTEXITCODE -ne 0) { throw "openssl could not sign the catalog" }

Write-Host "Catalog: $catalogPath"
Write-Host "Signature: $signaturePath"
Write-Host "Public key: $PublicKeyPath"
Write-Host "Configure REPORT_AI_RUNTIME_CATALOG_PATH, REPORT_AI_RUNTIME_CATALOG_SIGNATURE_PATH and REPORT_AI_RUNTIME_CATALOG_PUBLIC_KEY_PATH with these files."

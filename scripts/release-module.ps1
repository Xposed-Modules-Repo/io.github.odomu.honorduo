# HonorDuo - publish one release into the LSPosed module repository.
#
# release.ps1 calls this automatically at the end of a release, so one command
# keeps both repos on the same version. It also stays runnable standalone, to
# backfill a module-repo step that failed after the main repo already shipped.
# The main repo is the website / download home, while
# this puts the same APK where the LSPosed app looks for it. It is a separate
# script because the module repo is a different repository with its own spec.
#
# The module repo (Xposed-Modules-Repo/io.github.yixing233.hyperduo) is NOT cloned
# locally; `maintain` role cannot change repo settings but CAN create releases
# (verified: POST /releases -> 201). So this script drives the Releases API only.
#
# Spec (Xposed-Modules-Repo/submission README):
#   Release Tag   = [version code]-[version name]   e.g. 10400-1.4
#   Release Title = the version name                e.g. 1.4
#   Release body  = the changelog
#   asset         = the apk
#
# Usage (from the repository root; this script lives in scripts\):
#   .\scripts\release-module.ps1 -Version 1.4 -VersionCode 10400 `
#       -Apk .\dist\HonorDuo-1.4.apk -NotesFile .\docs\release-notes\release-notes-1.4.md
#   .\scripts\release-module.ps1 ... -DryRun     # validate badging only, create nothing
#   .\scripts\release-module.ps1 ... -SkipReadme # do not touch the module repo README
#
# After a successful publish this deletes every other release in the module repo,
# so the repo always shows exactly one version -- the same "only the newest
# release" policy release.ps1 applies to the main repo. Tags are left alone.
#
# The module repo's README.md IS published from .\module-README.md (tracked in
# this repo) so the published copy cannot drift away from the source. Pass
# -SkipReadme to leave it alone. SUMMARY is NOT touched here: it has no tracked
# source and goes through PUT /contents by hand (see docs/DEVELOPMENT.md).
#
# ASCII only, and NO BOM: this file has no Chinese, and PowerShell 5.1 reads a
# BOM-less .ps1 with the ANSI code page. Keep every byte < 0x80 so that is safe.

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][int]$VersionCode,
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$TagName,
    [string]$Title,
    [string]$Notes,
    [string]$NotesFile,
    [switch]$DryRun,
    [switch]$Force,
    [switch]$KeepOldReleases,
    [switch]$SkipReadme
)

$ErrorActionPreference = 'Stop'

$Root    = Split-Path $PSScriptRoot -Parent
$Module  = 'Xposed-Modules-Repo/io.github.odomu.honorduo'
$Package = 'io.github.odomu.honorduo'
if (-not $TagName) { $TagName = "$VersionCode-$Version" }
$Tag     = $TagName
if (-not $Title) { $Title = "HonorDuo $Version" }
$Asset   = "HonorDuo-$Version.apk"
$Readme  = Join-Path $Root 'module-README.md'

# Auto-detect Android SDK if not set
if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
    $localProp = Join-Path $Root 'local.properties'
    if (Test-Path $localProp) {
        $sdkLine = Get-Content $localProp -ErrorAction SilentlyContinue |
            Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($sdkLine) {
            $parsed = ($sdkLine -replace '^\s*sdk\.dir\s*=\s*', '').Trim()
            $parsed = $parsed -replace '\\\\', '\' -replace '\\:', ':'
            if (Test-Path $parsed) { $env:ANDROID_HOME = $parsed }
        }
    }
}
if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
    }
}
if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
    $stdPaths = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:ProgramFiles\Android\android-sdk",
        "$env:ProgramFiles(x86)\Android\android-sdk",
        "$HOME/Library/Android/sdk",
        "$HOME/Android/Sdk"
    )
    foreach ($p in $stdPaths) {
        if ($p -and (Test-Path $p)) { $env:ANDROID_HOME = $p; break }
    }
}
if (-not $env:ANDROID_HOME) { throw "ANDROID_HOME is not set. Please define ANDROID_HOME or sdk.dir in local.properties." }
$buildTools = Get-ChildItem (Join-Path $env:ANDROID_HOME 'build-tools') -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [version]$_.Name } -ErrorAction SilentlyContinue | Select-Object -Last 1
if (-not $buildTools) { throw "build-tools not found under: $env:ANDROID_HOME\build-tools" }
$Aapt = Join-Path $buildTools.FullName 'aapt2.exe'
if (-not (Test-Path $Aapt)) { $Aapt = Join-Path $buildTools.FullName 'aapt2' }

function Invoke-Native {
    param([string]$Exe, [string[]]$Arguments)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Exe @Arguments 2>&1
        return [pscustomobject]@{
            Output   = (@($output | ForEach-Object { "$_" }) -join "`n").Trim()
            ExitCode = $LASTEXITCODE
        }
    } finally { $ErrorActionPreference = $prev }
}

function Get-GitHubToken {
    $tmp = Join-Path $env:TEMP ("hd_cred_" + [guid]::NewGuid().ToString('N') + '.txt')
    try {
        [System.IO.File]::WriteAllText($tmp, "protocol=https`nhost=github.com`n`n")
        $raw = (Invoke-Native 'cmd' @('/c', "git credential fill < `"$tmp`"")).Output
        $line = $raw -split "`n" | Where-Object { $_ -match '^password=' } | Select-Object -First 1
        if (-not $line) { throw 'GitHub credential unavailable: git credential fill returned no password.' }
        return ($line -replace '^password=', '').Trim()
    } finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
}
function Invoke-RestMethodWithRetry {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [hashtable]$Headers,
        [string]$Method = 'Get',
        [string]$ContentType,
        $Body,
        [string]$InFile,
        [int]$MaxRetries = 3
    )
    for ($i = 1; $i -le $MaxRetries; $i++) {
        try {
            $params = @{ Uri = $Uri; Headers = $Headers; Method = $Method }
            if ($ContentType) { $params.ContentType = $ContentType }
            if ($Body) { $params.Body = $Body }
            if ($InFile) { $params.InFile = $InFile }
            return Invoke-RestMethod @params
        } catch {
            $status = $null
            try { $status = [int]$_.Exception.Response.StatusCode } catch {}
            if ($status -eq 404) { throw }
            if ($i -eq $MaxRetries) { throw }
            Write-Host "==> network transient issue ($($_.Exception.Message)), retrying ($i/$MaxRetries)..." -ForegroundColor DarkGray
            Start-Sleep -Seconds 2
        }
    }
}


# The module repo must show exactly one version: delete every release except the
# one just published. Only the release records go away; the tags stay.
function Remove-StaleReleases {
    param(
        [string]$ApiBase,
        [hashtable]$Headers,
        [int]$KeepId,
        [string]$KeepTag
    )
    $all = Invoke-RestMethodWithRetry -Uri "$ApiBase/releases?per_page=100" -Headers $Headers
    $stale = @($all | Where-Object { $_.id -ne $KeepId -and -not $_.draft })
    if ($stale.Count -eq 0) {
        Write-Host '==> no stale releases to remove' -ForegroundColor DarkGray
        return
    }
    foreach ($r in $stale) {
        Write-Host "==> deleting stale release $($r.tag_name) (tag kept)" -ForegroundColor Yellow
        Invoke-RestMethodWithRetry -Method Delete -Uri "$ApiBase/releases/$($r.id)" -Headers $Headers | Out-Null
    }
    Write-Host "==> removed $($stale.Count) stale release(s); only $KeepTag remains" -ForegroundColor Green
}

# ---- validate the apk --------------------------------------------------------

if (-not (Test-Path $Apk)) { throw "apk not found: $Apk" }
$Apk = (Resolve-Path $Apk).Path

$badgingRun = Invoke-Native $Aapt @('dump', 'badging', $Apk)
$badging = [string]($badgingRun.Output -split "`n" | Where-Object { $_ -match '^package:' } | Select-Object -First 1)
if (-not $badging) { throw "cannot read badging from $Apk" }

Write-Host "==> $Apk" -ForegroundColor Cyan
Write-Host "    $badging"

$errors = @()
if ($badging -notmatch "name='$([regex]::Escape($Package))'") { $errors += "package is not $Package" }
if ($badging -notmatch "versionCode='$VersionCode'")        { $errors += "versionCode is not $VersionCode" }
if ($badging -notmatch "versionName='$([regex]::Escape($Version))'") { $errors += "versionName is not $Version" }
if ($errors.Count) { throw ("apk does not match the intended release: " + ($errors -join '; ')) }
Write-Host "    [ok] package / versionCode / versionName all match" -ForegroundColor Green
Write-Host "==> target: $Module  tag=$Tag  asset=$Asset"

if ($DryRun) { Write-Host '==> -DryRun: nothing was created' -ForegroundColor Yellow; return }

# ---- changelog ---------------------------------------------------------------

if (-not $Notes -and $NotesFile) {
    if (-not (Test-Path $NotesFile)) { throw "notes file not found: $NotesFile" }
    $Notes = [System.IO.File]::ReadAllText((Resolve-Path $NotesFile), [Text.Encoding]::UTF8)
}
if (-not $Notes) { throw 'a changelog is required: pass -Notes or -NotesFile (the release body is the changelog).' }

$token = Get-GitHubToken
$headers = @{
    Authorization = "token $token"
    'User-Agent'  = 'HonorDuo-Release'
    Accept        = 'application/vnd.github+json'
}
$api = "https://api.github.com/repos/$Module"

# ---- existing release with this tag? ----------------------------------------

$existing = $null
try {
    $existing = Invoke-RestMethodWithRetry -Uri "$api/releases/tags/$Tag" -Headers $headers
} catch {
    $status = $null
    try { $status = [int]$_.Exception.Response.StatusCode } catch { $status = $null }
    if ($status -ne 404) { throw }
}

$payload = @{
    tag_name   = $Tag
    name       = $Title
    body       = [string]$Notes
    draft      = $false
    prerelease = $false
} | ConvertTo-Json -Depth 4

if ($existing) {
    if (-not $Force) { throw "release $Tag already exists in the module repo. Re-run with -Force to overwrite." }
    Write-Host "==> overwriting existing release $Tag" -ForegroundColor Yellow
    $release = Invoke-RestMethodWithRetry -Method Patch -Uri "$api/releases/$($existing.id)" `
        -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($payload))
} else {
    $release = Invoke-RestMethodWithRetry -Method Post -Uri "$api/releases" `
        -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($payload))
}
if ($release.draft) {
    Write-Host "==> publishing draft release..." -ForegroundColor Yellow
    $pubPayload = '{"draft":false}'
    $release = Invoke-RestMethodWithRetry -Method Patch -Uri "$api/releases/$($release.id)" `
        -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($pubPayload))
}

# ---- asset -------------------------------------------------------------------

foreach ($a in $release.assets) {
    if ($a.name -eq $Asset) {
        Write-Host "==> deleting stale asset $Asset" -ForegroundColor Yellow
        Invoke-RestMethodWithRetry -Method Delete -Uri "$api/releases/assets/$($a.id)" -Headers $headers | Out-Null
    }
}

$uploaded = Invoke-RestMethodWithRetry -Method Post `
    -Uri "https://uploads.github.com/repos/$Module/releases/$($release.id)/assets?name=$Asset" `
    -Headers $headers -ContentType 'application/vnd.android.package-archive' -InFile $Apk

Write-Host "==> uploaded $($uploaded.name)  $($uploaded.size) B" -ForegroundColor Green
Write-Host "==> $($uploaded.browser_download_url)" -ForegroundColor Green

# README is tracked and pushed via git; do not create duplicate commits via Contents API.

# Run last, so a cleanup failure can never undo a successful publish.
if ($KeepOldReleases) {
    Write-Host '==> -KeepOldReleases: keeping the previous releases' -ForegroundColor Yellow
} else {
    Remove-StaleReleases -ApiBase $api -Headers $headers -KeepId $release.id -KeepTag $Tag
}

Write-Host '==> still required for listing: the repo DESCRIPTION must be set by hand in the web UI.'
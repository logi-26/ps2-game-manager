<#
    Builds both distributables in one step:
      - Windows: a jpackage app-image with a bundled JRE (package.ps1)
      - Mac/Linux: a plain jar bundle, recipient needs their own Java (bundle-jar.ps1)

    jpackage never cross-compiles, so this always builds the Windows app-image
    from wherever it's run (it needs to run on Windows anyway - that's the only
    platform available here). The jar bundle has no native launcher to compile,
    so it's produced the same way regardless of host OS and works on all three.

    Usage:
        pwsh ./release.ps1
        pwsh ./release.ps1 -ApiBaseUrl http://10.0.0.5:8000/v1
        pwsh ./release.ps1 -Fresh

    Output: build-local/release/windows/OPLPOPS-Manager/OPLPOPS-Manager.exe
            build-local/release/jar/
#>
param(
    [string]$AppVersion = '0.6.1',
    [string]$Vendor = 'Logi26',
    [string]$ApiBaseUrl = 'http://127.0.0.1:8000/v1',
    [string]$Icon,
    [switch]$Fresh
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

Write-Host "===> Building Windows app-image (package.ps1)"
& (Join-Path $root 'package.ps1') -AppVersion $AppVersion -Vendor $Vendor `
    -ApiBaseUrl $ApiBaseUrl -Icon $Icon -Fresh:$Fresh
if ($LASTEXITCODE -ne 0) { throw "package.ps1 failed" }

Write-Host ""
Write-Host "===> Building cross-platform jar bundle (bundle-jar.ps1)"
& (Join-Path $root 'bundle-jar.ps1') -AppVersion $AppVersion -ApiBaseUrl $ApiBaseUrl -Fresh:$Fresh
if ($LASTEXITCODE -ne 0) { throw "bundle-jar.ps1 failed" }

Write-Host ""
Write-Host "===> Release built at $(Join-Path $root 'build-local\release')"
Write-Host "     windows\OPLPOPS-Manager\OPLPOPS-Manager.exe  - zip, no Java needed"
Write-Host "     jar\                                          - zip, needs Java 11+"

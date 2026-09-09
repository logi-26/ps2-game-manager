<#
    Builds both distributables in one step:
      - Windows: a jpackage app-image with a bundled JRE (package.ps1)
      - plain jar bundle, recipient needs their own Java (bundle-jar.ps1)

    jpackage never cross-compiles, so the app-image is always Windows (it needs
    to run on Windows anyway - that's the only platform here). The jar bundle has
    no native launcher to compile and now ships all four JavaFX classifiers, so
    it runs on Windows, macOS and Linux (recipient supplies Java 17+).

    Usage:
        pwsh ./release.ps1
        pwsh ./release.ps1 -ApiBaseUrl http://10.0.0.5:8000/v1
        pwsh ./release.ps1 -Fresh

    Output: build-local/release/windows/PS2GM/PS2GM.exe                      (run this locally)
            build-local/release/windows/PS2GM-windows-<version>.zip(.sha256)
            build-local/release/jar/                                        (unzipped, local testing)
            build-local/release/PS2GM-jarbundle-<version>.zip(.sha256)

    The two .zip + .sha256 pairs are what the app's self-updater downloads
    (api/app/github.py) - attach all four to a GitHub Release (this script
    prints the exact `gh release create` command at the end) and cutting
    that release *is* publishing the update, nothing else to do.
#>
param(
    [string]$AppVersion = '1.0',
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
Write-Host "===> Building cross-platform jar bundle (bundle-jar.ps1)  [win + mac + linux]"
& (Join-Path $root 'bundle-jar.ps1') -AppVersion $AppVersion -ApiBaseUrl $ApiBaseUrl -Fresh:$Fresh
if ($LASTEXITCODE -ne 0) { throw "bundle-jar.ps1 failed" }

$releaseDir = Join-Path $root 'build-local\release'
Write-Host ""
Write-Host "===> Release built at $releaseDir"
Write-Host "     windows\PS2GM\PS2GM.exe  - run this locally, no Java needed"
Write-Host "     jar\                     - unzipped jar bundle, needs Java 17+ (Windows / macOS / Linux)"
Write-Host ""
Write-Host "===> To publish this as an update, cut a GitHub Release with all four assets:"
$assets = @(
    (Join-Path $releaseDir "windows\PS2GM-windows-$AppVersion.zip")
    (Join-Path $releaseDir "windows\PS2GM-windows-$AppVersion.zip.sha256")
    (Join-Path $releaseDir "PS2GM-jarbundle-$AppVersion.zip")
    (Join-Path $releaseDir "PS2GM-jarbundle-$AppVersion.zip.sha256")
)
Write-Host "    gh release create v$AppVersion $($assets -join ' ') --title `"PS2GM v$AppVersion`" --notes `"...`""

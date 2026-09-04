<#
    Packages the desktop app into a standalone application image using jpackage -
    a folder with a native .exe launcher and a bundled, trimmed JRE. End users
    don't need Java installed at all.

    Windows-only output: jpackage never cross-compiles - run on Windows, it can
    only produce a Windows app-image, never a macOS .app or Linux AppImage. Mac
    and Linux users are served by bundle-jar.ps1 instead (plain jar, their own
    Java). See release.ps1 to build both distributables in one step.

    Requires a JDK with jpackage on PATH (tested with Temurin 25). Building an
    actual installer (--type exe / msi) additionally needs the WiX Toolset
    (https://wixtoolset.org/) - not required for the app-image this script builds.

    Usage:
        pwsh ./package.ps1                                  # app-image, defaults (127.0.0.1:8000/v1)
        pwsh ./package.ps1 -ApiBaseUrl http://10.0.0.5:8000/v1
        pwsh ./package.ps1 -Icon path\to\icon.ico
        pwsh ./package.ps1 -Fresh                           # re-stage lib/ hdd/ POPSTARTER/ first

    Output: build-local/release/windows/OPLPOPS-Manager/OPLPOPS-Manager.exe
    (zip the OPLPOPS-Manager folder to hand it to someone else)
#>
param(
    [string]$AppVersion = '0.6.1',
    [string]$Vendor = 'Logi26',
    [string]$ApiBaseUrl = 'http://127.0.0.1:8000/v1',
    [string]$Icon,
    [switch]$Fresh
)

$ErrorActionPreference = 'Stop'
$root       = $PSScriptRoot
$buildLocal = Join-Path $root 'build-local'
$runDir     = Join-Path $buildLocal 'run'
$packageDir = Join-Path $buildLocal 'release\windows'

# 1. Compile + jar + stage the app image's contents (jar + lib/ + hdd/ + POPSTARTER/
#    all as siblings, matching what PopsGameManager.getCurrentDirectory() expects).
#    Wipe build-local/run/ first: -Run leaves it live for interactive testing, and
#    the app rewrites files next to its jar on every launch (settings.xml,
#    READ ME.txt, start-oplpops.*, ...) - none of that belongs in a shipped image.
if (Test-Path $runDir) { Remove-Item -Recurse -Force $runDir }
& (Join-Path $root 'build.ps1') -Stage -Fresh:$Fresh
if ($LASTEXITCODE -ne 0) { throw "build.ps1 -Stage failed" }

# 2. Compute the minimal set of JDK modules the app actually needs, instead of
#    bundling the entire JDK (jpackage's default for a non-modular app).
$cp = @(
    (Join-Path $runDir 'lib\commons-net-3.5.jar')
    (Join-Path $runDir 'lib\sevenzipjbinding.jar')
    (Join-Path $runDir 'lib\sevenzipjbinding-AllPlatforms.jar')
    (Join-Path $runDir 'lib\flatlaf-3.7.2.jar')
) -join ';'

Write-Host "==> Computing required JDK modules with jdeps"
$modules = & jdeps --multi-release 11 --print-module-deps --ignore-missing-deps `
    --class-path $cp (Join-Path $runDir 'OPLPOPS-Manager-local.jar')
if ($LASTEXITCODE -ne 0) { throw "jdeps failed" }
$modules = ($modules | Select-Object -Last 1).Trim()
Write-Host "    modules: $modules"

# 3. Clean previous package output
if (Test-Path $packageDir) { Remove-Item -Recurse -Force $packageDir }
New-Item -ItemType Directory -Force -Path $packageDir | Out-Null

# 4. jpackage: --input's entire contents land in <image>/app/, alongside the
#    generated launcher - so the jar, lib/, hdd/ and POPSTARTER/ end up as
#    siblings there, exactly like build.ps1 -Run's build-local/run/.
$jpackageArgs = @(
    '--type', 'app-image'
    '--name', 'OPLPOPS-Manager'
    '--app-version', $AppVersion
    '--vendor', $Vendor
    '--input', $runDir
    '--main-jar', 'OPLPOPS-Manager-local.jar'
    '--main-class', 'oplpops.game.manager.Main'
    '--add-modules', $modules
    '--dest', $packageDir
    '--java-options', "-Doplpops.api.baseurl=$ApiBaseUrl"
)
if ($Icon) { $jpackageArgs += @('--icon', $Icon) }

Write-Host "==> Running jpackage (app-image, modules: $modules)"
& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

$exe = Join-Path $packageDir 'OPLPOPS-Manager\OPLPOPS-Manager.exe'
Write-Host "==> Packaged: $exe"
Write-Host "    No separate Java install needed. Zip the OPLPOPS-Manager folder to share it."
Write-Host "    (Mac/Linux users: see bundle-jar.ps1 instead - jpackage doesn't cross-compile.)"

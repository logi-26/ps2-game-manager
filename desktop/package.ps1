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
        pwsh ./package.ps1 -Icon path\to\icon.ico            # defaults to icons/ps2gm.ico
        pwsh ./package.ps1 -Fresh                           # re-stage lib/ hdd/ POPSTARTER/ first

    Output: build-local/release/windows/PS2GM/PS2GM.exe
    (zip the PS2GM folder to hand it to someone else)
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
if (-not $Icon) { $Icon = Join-Path $root 'icons\ps2gm.ico' }
$buildLocal = Join-Path $root 'build-local'
$runDir     = Join-Path $buildLocal 'run'
$packageDir = Join-Path $buildLocal 'release\windows'

# 1. Compile + jar + stage the app image's contents (jar + lib/ + hdd/ + POPSTARTER/
#    all as siblings, matching what PopsGameManager.getCurrentDirectory() expects).
#    Wipe build-local/run/ first: -Run leaves it live for interactive testing, and
#    the app rewrites files next to its jar on every launch (settings.xml,
#    READ ME.txt, start-ps2gm.*, ...) - none of that belongs in a shipped image.
if (Test-Path $runDir) { Remove-Item -Recurse -Force $runDir }
& (Join-Path $root 'build.ps1') -Stage -Fresh:$Fresh
if ($LASTEXITCODE -ne 0) { throw "build.ps1 -Stage failed" }

# 2. Compute the minimal set of JDK modules the app actually needs, instead of
#    bundling the entire JDK (jpackage's default for a non-modular app).
#    JavaFX + AtlantaFX ride the classpath (not the module path) and are bundled
#    as jars under app/lib/javafx/ via the manifest Class-Path - jdeps just needs
#    them here so --ignore-missing-deps can see (and skip) the javafx.* automatic
#    modules while still resolving the real JDK deps underneath.
$cp = (Get-ChildItem -Recurse -Path (Join-Path $runDir 'lib') -Filter *.jar |
       ForEach-Object { $_.FullName }) -join ';'

Write-Host "==> Computing required JDK modules with jdeps"
$modules = & jdeps --multi-release 21 --print-module-deps --ignore-missing-deps `
    --class-path $cp (Join-Path $runDir 'PS2GM-local.jar')
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
    '--name', 'PS2GM'
    '--app-version', $AppVersion
    '--vendor', $Vendor
    '--input', $runDir
    '--main-jar', 'PS2GM-local.jar'
    '--main-class', 'ps2gm.game.manager.Main'
    '--add-modules', $modules
    '--dest', $packageDir
    '--java-options', "-Dps2gm.api.baseurl=$ApiBaseUrl"
    # JavaFX rides the classpath, not the module path - silence its native-load warning
    '--java-options', '--enable-native-access=ALL-UNNAMED'
)
if ($Icon) { $jpackageArgs += @('--icon', $Icon) }

Write-Host "==> Running jpackage (app-image, modules: $modules)"
& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

$exe = Join-Path $packageDir 'PS2GM\PS2GM.exe'
Write-Host "==> Packaged: $exe"
Write-Host "    No separate Java install needed. Zip the PS2GM folder to share it."
Write-Host "    (Mac/Linux users: see bundle-jar.ps1 instead - jpackage doesn't cross-compile.)"

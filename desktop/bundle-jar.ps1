<#
    Builds a "plain jar" distribution - for anyone who'd rather not use the
    packaged .exe. Unlike package.ps1's jpackage app-image, no JRE is bundled:
    the recipient needs their own Java 17+ on PATH. Output is just the jar next
    to lib/, hdd/ and POPSTARTER/ - the layout PopsGameManager.getCurrentDirectory()
    expects - plus a run.sh / run.cmd so they don't have to type the java command.

    Cross-platform: sevenzipjbinding-AllPlatforms.jar and lib/data/tools/{windows,
    linux} carry the native code / tool binaries for every OS, and the bundle now
    ships all four JavaFX classifiers (win / mac / mac-aarch64 / linux, staged via
    build.ps1 -AllPlatformFx). The generated run.sh detects the host OS + arch and
    puts only the matching JavaFX jars on the classpath; run.cmd does the Windows
    equivalent. Recipient still needs their own Java 17+ on PATH.

    Usage:
        pwsh ./bundle-jar.ps1                          # defaults (127.0.0.1:8000/v1)
        pwsh ./bundle-jar.ps1 -ApiBaseUrl http://10.0.0.5:8000/v1
        pwsh ./bundle-jar.ps1 -Fresh                   # re-stage lib/ hdd/ POPSTARTER/ first

    Output: build-local/release/jar/                                  (unzipped, for local testing)
            build-local/release/PS2GM-jarbundle-<version>.zip         (attach to a GitHub
            build-local/release/PS2GM-jarbundle-<version>.zip.sha256   Release - see release.ps1)
#>
param(
    [string]$AppVersion = '1.0',
    [string]$ApiBaseUrl = 'http://127.0.0.1:8000/v1',
    [switch]$Fresh
)

$ErrorActionPreference = 'Stop'
$root       = $PSScriptRoot
$buildLocal = Join-Path $root 'build-local'
$runDir     = Join-Path $buildLocal 'run'
$bundleDir  = Join-Path $buildLocal 'release\jar'
$jarName    = "PS2GM_$AppVersion.jar"   # must match PopsGameManager.CURRENT_APP_NAME

# 1. Compile + stage jar + lib/ + hdd/ + POPSTARTER/ as siblings (same clean
#    staging package.ps1 uses - see build.ps1 for why it's wiped first).
if (Test-Path $runDir) { Remove-Item -Recurse -Force $runDir }
& (Join-Path $root 'build.ps1') -Stage -AllPlatformFx -Fresh:$Fresh
if ($LASTEXITCODE -ne 0) { throw "build.ps1 -Stage failed" }

# 2. Copy the staged tree into the bundle. The jar is renamed to match
#    PopsGameManager's hardcoded CURRENT_APP_NAME - needed because the app
#    deletes and regenerates start-ps2gm.sh/.bat on every launch pointing
#    at that exact filename (deletePreviousAppVersion()); if the jar isn't
#    actually called that, its own self-written launchers point at nothing.
if (Test-Path $bundleDir) { Remove-Item -Recurse -Force $bundleDir }
New-Item -ItemType Directory -Force -Path $bundleDir | Out-Null
Copy-Item (Join-Path $runDir 'lib')        $bundleDir -Recurse
Copy-Item (Join-Path $runDir 'hdd')        $bundleDir -Recurse
Copy-Item (Join-Path $runDir 'POPSTARTER') $bundleDir -Recurse
Copy-Item (Join-Path $runDir 'PS2GM-local.jar') (Join-Path $bundleDir $jarName)

# Marker file DistributionType.detect() looks for at runtime - see ps2gm.game.manager.DistributionType.
New-Item -ItemType File -Force -Path (Join-Path $bundleDir '.ps2gm-dist-jar-bundle') | Out-Null

# 3. Launch scripts, deliberately NOT named start-ps2gm.sh/.bat: the app
#    rewrites those two exact filenames on every startup with a bare
#    `java -jar <jar>`, no backend flags, so anything baked in there only
#    survives the first launch. run.sh/run.cmd are left alone by the app and
#    set the API base URL on every launch via the env var PopsGameManager
#    also checks (see getApiBaseUrl()).
#
#    They launch with an explicit -cp (not -jar): the jar's manifest Class-Path
#    hard-codes the -win JavaFX jars, so -jar only works on Windows. Instead each
#    script builds the classpath from lib/ + lib/javafx/, keeping every non-fx
#    jar and only the JavaFX jars for the host OS/arch, then runs the Main class.

$sh = @'
#!/bin/sh
cd "$(dirname "$0")"
chmod +x lib/data/tools/linux/* 2>/dev/null

case "$(uname -s)" in
    Darwin) case "$(uname -m)" in arm64|aarch64) FXCLS=mac-aarch64 ;; *) FXCLS=mac ;; esac ;;
    Linux)  FXCLS=linux ;;
    *)      FXCLS=win ;;
esac

CP="__JAR__"
for j in lib/*.jar lib/javafx/*.jar; do
    b=${j##*/}
    case "$b" in
        javafx-*-"$FXCLS".jar) CP="$CP:$j" ;;
        javafx-*-win.jar|javafx-*-mac.jar|javafx-*-mac-aarch64.jar|javafx-*-linux.jar) ;;
        *) CP="$CP:$j" ;;
    esac
done

export PS2GM_API_BASEURL=__URL__
exec java --enable-native-access=ALL-UNNAMED -cp "$CP" ps2gm.game.manager.Main "$@"
'@ -replace '__JAR__', $jarName -replace '__URL__', $ApiBaseUrl -replace "`r`n", "`n"
[IO.File]::WriteAllText((Join-Path $bundleDir 'run.sh'), $sh, [Text.UTF8Encoding]::new($false))

$cmd = @'
@echo off
setlocal enabledelayedexpansion
cd /d %~dp0

set "CP=__JAR__"
for %%f in (lib\*.jar lib\javafx\*.jar) do (
    echo %%~nxf| findstr /R /C:"javafx-.*-mac\.jar" /C:"javafx-.*-mac-aarch64\.jar" /C:"javafx-.*-linux\.jar" >nul
    if errorlevel 1 set "CP=!CP!;%%f"
)

set PS2GM_API_BASEURL=__URL__
java --enable-native-access=ALL-UNNAMED -cp "!CP!" ps2gm.game.manager.Main %*
'@ -replace '__JAR__', $jarName -replace '__URL__', $ApiBaseUrl -replace "`r?`n", "`r`n"
Set-Content -Path (Join-Path $bundleDir 'run.cmd') -Value $cmd -Encoding ascii -NoNewline

Write-Host "==> Bundled at $bundleDir  (all JavaFX platforms)"
Write-Host "    Mac/Linux: chmod +x run.sh && ./run.sh   (needs Java 17+ on PATH)"
Write-Host "    Windows:   run.cmd"

# 4. Zip the bundle's *contents* (not the folder itself, matching package.ps1's convention -
#    see AppUpdateStager) + a sha256 sidecar, named per api/app/github.py's asset-name regex.
#    Uses .NET's ZipFile, not Compress-Archive: the latter writes entry names with Windows
#    backslashes instead of the zip-spec's forward slashes, which java.util.zip.ZipInputStream
#    (AppUpdateStager's unzip) can't parse correctly - confirmed via an actual dry run.
$releaseDir = Join-Path $buildLocal 'release'
$zipName = "PS2GM-jarbundle-$AppVersion.zip"
$zipPath = Join-Path $releaseDir $zipName
if (Test-Path $zipPath) { Remove-Item -Force $zipPath }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($bundleDir, $zipPath)
$hash = (Get-FileHash -Algorithm SHA256 $zipPath).Hash.ToLower()
"$hash  $zipName" | Set-Content -Encoding ascii -NoNewline:$false (Join-Path $releaseDir "$zipName.sha256")

Write-Host "==> Release asset: $zipPath"

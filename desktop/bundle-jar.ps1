<#
    Builds a cross-platform "plain jar" distribution - for Mac and Linux users
    (and anyone on Windows who'd rather not use the packaged .exe). Unlike
    package.ps1's jpackage app-image, no JRE is bundled: the recipient needs
    their own Java 11+ on PATH. Output is just the jar next to lib/, hdd/ and
    POPSTARTER/ - the layout PopsGameManager.getCurrentDirectory() expects -
    plus a run.sh / run.cmd so they don't have to type the java command.

    The bundled sevenzipjbinding-AllPlatforms.jar and lib/data/tools/{windows,linux}
    already carry the native code / tool binaries for every OS the app supports,
    so this one bundle runs unmodified on Windows, macOS or Linux - it doesn't
    need building separately per platform.

    Usage:
        pwsh ./bundle-jar.ps1                          # defaults (api backend, 127.0.0.1:8000/v1)
        pwsh ./bundle-jar.ps1 -ApiBaseUrl http://10.0.0.5:8000/v1
        pwsh ./bundle-jar.ps1 -Backend tcp -Server 10.0.0.5 -Port 6789
        pwsh ./bundle-jar.ps1 -Fresh                   # re-stage lib/ hdd/ POPSTARTER/ first

    Output: build-local/release/jar/  (zip it and hand it out)
#>
param(
    [string]$AppVersion = '0.6.1',
    [ValidateSet('tcp', 'api')]
    [string]$Backend = 'api',
    [string]$Server = '127.0.0.1',
    [int]$Port = 6789,
    [string]$ApiBaseUrl = 'http://127.0.0.1:8000/v1',
    [switch]$Fresh
)

$ErrorActionPreference = 'Stop'
$root       = $PSScriptRoot
$buildLocal = Join-Path $root 'build-local'
$runDir     = Join-Path $buildLocal 'run'
$bundleDir  = Join-Path $buildLocal 'release\jar'
$jarName    = "OPLPOPS-Manager_$AppVersion.jar"   # must match PopsGameManager.CURRENT_APP_NAME

# 1. Compile + stage jar + lib/ + hdd/ + POPSTARTER/ as siblings (same clean
#    staging package.ps1 uses - see build.ps1 for why it's wiped first).
if (Test-Path $runDir) { Remove-Item -Recurse -Force $runDir }
& (Join-Path $root 'build.ps1') -Stage -Fresh:$Fresh
if ($LASTEXITCODE -ne 0) { throw "build.ps1 -Stage failed" }

# 2. Copy the staged tree into the bundle. The jar is renamed to match
#    PopsGameManager's hardcoded CURRENT_APP_NAME - needed because the app
#    deletes and regenerates start-oplpops.sh/.bat on every launch pointing
#    at that exact filename (deletePreviousAppVersion()); if the jar isn't
#    actually called that, its own self-written launchers point at nothing.
if (Test-Path $bundleDir) { Remove-Item -Recurse -Force $bundleDir }
New-Item -ItemType Directory -Force -Path $bundleDir | Out-Null
Copy-Item (Join-Path $runDir 'lib')        $bundleDir -Recurse
Copy-Item (Join-Path $runDir 'hdd')        $bundleDir -Recurse
Copy-Item (Join-Path $runDir 'POPSTARTER') $bundleDir -Recurse
Copy-Item (Join-Path $runDir 'OPLPOPS-Manager-local.jar') (Join-Path $bundleDir $jarName)

# 3. Launch scripts, deliberately NOT named start-oplpops.sh/.bat: the app
#    rewrites those two exact filenames on every startup with a bare
#    `java -jar <jar>`, no backend flags, so anything baked in there only
#    survives the first launch. run.sh/run.cmd are left alone by the app and
#    set the backend on every launch via the env vars PopsGameManager also
#    checks (see getBackendMode() etc.) - equivalent to package.ps1's
#    --java-options, just via env instead of a native launcher config.
$envLines = @(
    "OPLPOPS_BACKEND=$Backend"
    "OPLPOPS_API_BASEURL=$ApiBaseUrl"
    "OPLPOPS_SERVER_ADDRESS=$Server"
    "OPLPOPS_SERVER_PORT=$Port"
)

$sh = "#!/bin/sh`n" +
      "cd `"`$(dirname `"`$0`")`"`n" +
      "chmod +x lib/data/tools/linux/* 2>/dev/null`n" +
      (($envLines | ForEach-Object { "export $_" }) -join "`n") + "`n" +
      "exec java -jar $jarName `"`$@`"`n"
[IO.File]::WriteAllText((Join-Path $bundleDir 'run.sh'), $sh, [Text.UTF8Encoding]::new($false))

$cmd = "@echo off`r`n" +
       "cd /d %~dp0`r`n" +
       (($envLines | ForEach-Object { "set $_" }) -join "`r`n") + "`r`n" +
       "java -jar $jarName %*`r`n"
Set-Content -Path (Join-Path $bundleDir 'run.cmd') -Value $cmd -Encoding ascii -NoNewline

Write-Host "==> Bundled at $bundleDir"
Write-Host "    Mac/Linux: chmod +x run.sh && ./run.sh   (needs Java 11+ on PATH)"
Write-Host "    Windows:   run.cmd"
Write-Host "    Zip the 'jar' folder (rename it e.g. OPLPOPS-Manager-$AppVersion) to hand it out."

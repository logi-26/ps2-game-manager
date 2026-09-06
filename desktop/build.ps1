<#
    Standalone build for the desktop app (no NetBeans, no Ant).
    Requires a JDK on PATH (tested with Temurin 25; targets bytecode 11).

    Usage:
        pwsh ./build.ps1                              # compile + jar into build-local/
        pwsh ./build.ps1 -Run                         # build, then launch against the HTTP API (127.0.0.1:8000/v1)
        pwsh ./build.ps1 -Run -ApiBaseUrl http://10.0.0.5:8000/v1
        pwsh ./build.ps1 -Run -Fresh -Debug           # re-copy lib/ hdd/ POPSTARTER/ into the run dir
        pwsh ./build.ps1 -Stage                       # assemble build-local/run/ without launching (package.ps1 uses this)

    IMPORTANT: on launch the app rewrites sibling files next to its jar
    (start-ps2gm.*, READ ME.txt, settings.xml/ps2gm-settings, lib/data/data_3, ...).
    To keep the working tree clean it is NEVER run from the project folder. -Run
    assembles an isolated  build-local/run/  (jar + copies of the data dirs it reads)
    and launches from there; all app-generated churn stays inside build-local/ (gitignored).
#>
param(
    [switch]$Run,
    [switch]$Stage,
    [switch]$Debug,
    [switch]$Fresh,
    [string]$ApiBaseUrl = 'http://127.0.0.1:8000/v1'
)

$ErrorActionPreference = 'Stop'
$root       = $PSScriptRoot
$srcRoot    = Join-Path $root 'src'
$libDir     = Join-Path $root 'lib'
$buildLocal = Join-Path $root 'build-local'
$outDir     = Join-Path $buildLocal 'classes'
$jarPath    = Join-Path $buildLocal 'PS2GM-local.jar'
$runDir     = Join-Path $buildLocal 'run'

# JavaFX (Windows dev bundle) - the "-win" classified jars carry the native DLLs.
# The UI is mid-migration from Swing to JavaFX; both toolkits run in one process.
$javafxVersion = '21.0.5'
$javafxJars = @('javafx-base', 'javafx-graphics', 'javafx-controls', 'javafx-fxml') |
    ForEach-Object { "javafx/$_-$javafxVersion-win.jar" }

$libJars = @(
    'commons-net-3.5.jar'
    'sevenzipjbinding.jar'
    'sevenzipjbinding-AllPlatforms.jar'
    'flatlaf-3.7.2.jar'
) + $javafxJars

$cp = ($libJars | ForEach-Object { Join-Path $libDir $_ }) -join ';'

Write-Host "==> Cleaning build-local\classes"
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$sources = Get-ChildItem -Recurse -Path $srcRoot -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "==> Compiling $($sources.Count) source file(s) with --release 21"
& javac --release 21 -encoding UTF-8 -cp $cp -d $outDir @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "==> Copying resources (png / txt / fxml / css, skipping .java and .form)"
Get-ChildItem -Recurse -Path $srcRoot -File |
    Where-Object { $_.Extension -notin '.java', '.form' } |
    ForEach-Object {
        $rel  = $_.FullName.Substring($srcRoot.Length + 1)
        $dest = Join-Path $outDir $rel
        New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
        Copy-Item $_.FullName $dest -Force
    }

Write-Host "==> Packaging $jarPath"
$manifest = Join-Path $outDir 'manifest.txt'
$classPathLine = 'Class-Path: ' + (($libJars | ForEach-Object { "lib/$_" }) -join ' ')
@(
    'Main-Class: ps2gm.game.manager.Main'
    $classPathLine
    ''
) -join "`n" | Set-Content -Encoding ascii $manifest

Push-Location $outDir
try {
    & jar cfm $jarPath manifest.txt ps2gm
    if ($LASTEXITCODE -ne 0) { throw "jar failed" }
} finally { Pop-Location }

Write-Host "==> Built $jarPath"

if ($Run -or $Stage) {
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null

    # data dirs the app READS relative to its jar; copy the whole tree once
    # (lib/ carries lib/data/data_3 (settings key) + lib/data/images + lib/data/tools)
    foreach ($d in 'lib', 'hdd', 'POPSTARTER') {
        $target = Join-Path $runDir $d
        if ($Fresh -and (Test-Path $target)) { Remove-Item -Recurse -Force $target }
        if (-not (Test-Path $target)) {
            Write-Host "==> Seeding run\$d from $d"
            Copy-Item -Recurse -Force (Join-Path $root $d) $runDir
        }
    }
    Copy-Item (Join-Path $libDir 'commons-net-3.5.jar')              (Join-Path $runDir 'lib') -Force
    Copy-Item (Join-Path $libDir 'sevenzipjbinding.jar')            (Join-Path $runDir 'lib') -Force
    Copy-Item (Join-Path $libDir 'sevenzipjbinding-AllPlatforms.jar') (Join-Path $runDir 'lib') -Force
    Copy-Item (Join-Path $libDir 'flatlaf-3.7.2.jar')                (Join-Path $runDir 'lib') -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $runDir 'lib\javafx') | Out-Null
    Copy-Item (Join-Path $libDir 'javafx\*.jar')                     (Join-Path $runDir 'lib\javafx') -Force
    Copy-Item $jarPath (Join-Path $runDir 'PS2GM-local.jar') -Force
    Write-Host "==> Staged $runDir"
}

if ($Run) {
    # --enable-native-access silences the JDK 21+ warning from JavaFX's native lib load
    # (JavaFX rides on the classpath, not the module path, during the Swing->FX migration).
    $jvmArgs = @('--enable-native-access=ALL-UNNAMED', "-Dps2gm.api.baseurl=$ApiBaseUrl")
    $jvmArgs += @('-jar', (Join-Path $runDir 'PS2GM-local.jar'))
    if ($Debug) { $jvmArgs += '-DEBUG' }   # consumed by Main.main(args)

    Write-Host "==> Launching from $runDir  ($ApiBaseUrl)"
    Push-Location $runDir
    try { & java @jvmArgs } finally { Pop-Location }
}

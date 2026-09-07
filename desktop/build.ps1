<#
    Standalone build for the desktop app (no NetBeans, no Ant).
    Requires a JDK on PATH (tested with Temurin 25; targets bytecode 11).

    Usage:
        pwsh ./build.ps1                              # compile + jar into build-local/
        pwsh ./build.ps1 -Run                         # build, then launch against the HTTP API (127.0.0.1:8000/v1)
        pwsh ./build.ps1 -Run -ApiBaseUrl http://10.0.0.5:8000/v1
        pwsh ./build.ps1 -Run -Fresh -Debug           # re-copy lib/ hdd/ POPSTARTER/ into the run dir
        pwsh ./build.ps1 -Stage                       # assemble build-local/run/ without launching (package.ps1 uses this)
        pwsh ./build.ps1 -Stage -AllPlatformFx        # ...and stage the mac/linux JavaFX jars too (bundle-jar.ps1 uses this)

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
    [switch]$AllPlatformFx,   # stage every JavaFX classifier (win + mac + mac-aarch64 + linux), not just -win
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
# The UI has been migrated Swing -> JavaFX; the app entry is now an Application subclass.
$javafxVersion = '21.0.5'
$javafxJars = @('javafx-base', 'javafx-graphics', 'javafx-controls', 'javafx-fxml') |
    ForEach-Object { "javafx/$_-$javafxVersion-win.jar" }
# AtlantaFX supplies the light/dark JavaFX theme (replaces FlatLaf's Swing light/dark).
$javafxJars += 'javafx/atlantafx-base-2.0.1.jar'
# Ikonli = theme-aware font icons (Feather pack). AtlantaFX styles .ikonli-font-icon
# per theme, so the glyphs recolour automatically. Classpath (not module path).
$javafxJars += 'javafx/ikonli-core-12.3.1.jar'
$javafxJars += 'javafx/ikonli-javafx-12.3.1.jar'
$javafxJars += 'javafx/ikonli-feather-pack-12.3.1.jar'

$libJars = @(
    'commons-net-3.5.jar'
    'sevenzipjbinding.jar'
    'sevenzipjbinding-AllPlatforms.jar'
) + $javafxJars

$cp = ($libJars | ForEach-Object { Join-Path $libDir $_ }) -join ';'

Write-Host "==> Cleaning build-local\classes"
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$sources = Get-ChildItem -Recurse -Path $srcRoot -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "==> Compiling $($sources.Count) source file(s) with --release 21"
# Relax EAP around the native call: javac prints an unchecked-operations *Note*
# to stderr, which under an outer 'Stop' scope (bundle-jar.ps1 / release.ps1)
# PowerShell 5.1 would otherwise turn into a terminating NativeCommandError.
$eap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
& javac --release 21 -encoding UTF-8 -cp $cp -d $outDir @sources 2>&1 | ForEach-Object { "$_" }
$javacExit = $LASTEXITCODE
$ErrorActionPreference = $eap
if ($javacExit -ne 0) { throw "javac failed" }

Write-Host "==> Copying resources (png / txt / fxml / css, skipping .java and .form)"
Get-ChildItem -Recurse -Path $srcRoot -File |
    Where-Object { $_.Extension -notin '.java', '.form' } |
    ForEach-Object {
        $rel  = $_.FullName.Substring($srcRoot.Length + 1)
        $dest = Join-Path $outDir $rel
        New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
        if ($_.Extension -eq '.fxml') {
            # Scene Builder 26 stamps xmlns="http://javafx.com/javafx/26..." on every
            # save; the JavaFX 21 runtime then warns "Loading FXML ... API of version 26
            # by ... runtime of version 21" on each load. Pin it to 21 in the copied
            # (packaged) file only - the source keeps whatever Scene Builder wrote.
            # Read AND write as no-BOM UTF-8 explicitly: Get-Content -Raw on PS 5.1
            # decodes as the ANSI codepage and mangles non-ASCII glyphs (e.g. the
            # arrow chars on some buttons); Set-Content -Encoding UTF8 adds a BOM,
            # which makes FXMLLoader fail ("Content is not allowed in prolog").
            $utf8 = [Text.UTF8Encoding]::new($false)
            $fxml = [IO.File]::ReadAllText($_.FullName, $utf8) `
                -replace 'http://javafx\.com/javafx/[\d.]+', 'http://javafx.com/javafx/21'
            [IO.File]::WriteAllText($dest, $fxml, $utf8)
        } else {
            Copy-Item $_.FullName $dest -Force
        }
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
    $eap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    & jar cfm $jarPath manifest.txt ps2gm 2>&1 | ForEach-Object { "$_" }
    $jarExit = $LASTEXITCODE
    $ErrorActionPreference = $eap
    if ($jarExit -ne 0) { throw "jar failed" }
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
    New-Item -ItemType Directory -Force -Path (Join-Path $runDir 'lib\javafx') | Out-Null
    Copy-Item (Join-Path $libDir 'javafx\*.jar') (Join-Path $runDir 'lib\javafx') -Force
    # lib\javafx now holds every JavaFX classifier. package.ps1 / -Run only need
    # -win, and shipping the mac/linux native jars in the Windows app-image just
    # bloats it - so prune them unless -AllPlatformFx (bundle-jar.ps1) asked for
    # the cross-platform set, whose run.sh picks the right jars at launch.
    if (-not $AllPlatformFx) {
        Get-ChildItem (Join-Path $runDir 'lib\javafx') -Filter 'javafx-*.jar' |
            Where-Object { $_.Name -match '-(mac|mac-aarch64|linux)\.jar$' } |
            Remove-Item -Force
    }
    Copy-Item $jarPath (Join-Path $runDir 'PS2GM-local.jar') -Force
    Write-Host "==> Staged $runDir  ($(if ($AllPlatformFx) { 'all JavaFX platforms' } else { 'Windows JavaFX only' }))"
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

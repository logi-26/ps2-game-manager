<#
    Runs the JUnit 5 tests in test/ against the sources in src/ - no Maven/Gradle,
    mirrors build.ps1's plain javac/classpath approach.

    Usage:
        pwsh ./test.ps1
#>
param()

$ErrorActionPreference = 'Stop'
$root       = $PSScriptRoot
$srcRoot    = Join-Path $root 'src'
$testRoot   = Join-Path $root 'test'
$libDir     = Join-Path $root 'lib'
$buildLocal = Join-Path $root 'build-local'
$outDir     = Join-Path $buildLocal 'test-classes'
$junitJar   = Join-Path $libDir 'test\junit-platform-console-standalone-1.11.4.jar'

$javafxVersion = '21.0.5'
$javafxJars = @('javafx-base', 'javafx-graphics', 'javafx-controls', 'javafx-fxml') |
    ForEach-Object { "javafx/$_-$javafxVersion-win.jar" }
$javafxJars += 'javafx/atlantafx-base-2.0.1.jar'
$javafxJars += 'javafx/ikonli-core-12.3.1.jar'
$javafxJars += 'javafx/ikonli-javafx-12.3.1.jar'
$javafxJars += 'javafx/ikonli-feather-pack-12.3.1.jar'

$libJars = @(
    'commons-net-3.5.jar'
    'sevenzipjbinding.jar'
    'sevenzipjbinding-AllPlatforms.jar'
) + $javafxJars

# src is on the classpath directly (not just compiled) so getResourceAsStream(...)
# can read non-.java resources (e.g. PS1CompatabilityList.txt) without a separate
# resource-copy step.
$cp = (@($srcRoot, $junitJar) + ($libJars | ForEach-Object { Join-Path $libDir $_ })) -join ';'

Write-Host "==> Cleaning build-local\test-classes"
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$mainSources = Get-ChildItem -Recurse -Path $srcRoot -Filter *.java
$testSources = Get-ChildItem -Recurse -Path $testRoot -Filter *.java
$sources = $mainSources + $testSources

Write-Host "==> Compiling $($sources.Count) source file(s) (main + test) with --release 21"
& javac --release 21 -encoding UTF-8 -cp $cp -d $outDir $sources.FullName
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "==> Running tests"
& java -jar $junitJar execute --class-path "$outDir;$cp" --scan-class-path $outDir --details=tree
if ($LASTEXITCODE -ne 0) { throw "tests failed" }

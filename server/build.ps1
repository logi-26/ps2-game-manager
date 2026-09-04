<#
    Standalone build for the server (no NetBeans, no Ant).
    Requires a JDK on PATH (tested with Temurin 25; targets bytecode 11).

    Usage:
        pwsh ./build.ps1            # compile + jar into build-local/
        pwsh ./build.ps1 -Run       # build, then serve build-local/serverdata on :6789

    The server resolves its data root from the FOLDER ITS JAR SITS IN
    (WorkerRunnable.getCurrentDirectory uses the jar's CodeSource, not the cwd),
    so -Run copies the jar into the serverdata fixture and launches it there.
    Create that fixture first with  ../local-dev/setup-serverdata.ps1
#>
param(
    [switch]$Run
)

$ErrorActionPreference = 'Stop'
$root    = $PSScriptRoot
$srcRoot = Join-Path $root 'src'
$outDir  = Join-Path $root 'build-local\classes'
$jarPath = Join-Path $root 'build-local\TCPServer.jar'
$dataDir = Join-Path $root 'build-local\serverdata'

Write-Host "==> Cleaning build-local\classes"
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$sources = Get-ChildItem -Recurse -Path $srcRoot -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "==> Compiling $($sources.Count) source file(s) with --release 11"
& javac --release 11 -encoding UTF-8 -d $outDir @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "==> Packaging $jarPath"
Push-Location $outDir
try {
    & jar cfe $jarPath tcpserver.TCPServer tcpserver
    if ($LASTEXITCODE -ne 0) { throw "jar failed" }
} finally { Pop-Location }

Write-Host "==> Built $jarPath"

if ($Run) {
    if (-not (Test-Path $dataDir)) {
        Write-Warning "No serverdata fixture at $dataDir"
        Write-Warning "Run  ../local-dev/setup-serverdata.ps1  first to create it."
        New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    }
    $runJar = Join-Path $dataDir 'TCPServer.jar'
    Copy-Item $jarPath $runJar -Force
    Write-Host "==> Starting server on port 6789, data root = $dataDir"
    Write-Host "    (Ctrl+C to stop)"
    & java -jar $runJar
}

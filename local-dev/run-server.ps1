<# Build the server and run it against the local serverdata fixture. #>
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot '..\server\build.ps1') -Run

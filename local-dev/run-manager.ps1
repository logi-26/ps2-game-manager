<# Build the desktop app and launch it pointed at a local server (127.0.0.1:6789). #>
param(
    [string]$Server = '127.0.0.1',
    [int]$Port = 6789,
    [switch]$Debug = $true
)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot '..\desktop\build.ps1') -Run -Server $Server -Port $Port -Debug:$Debug

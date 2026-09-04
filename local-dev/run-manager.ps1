<#
    Build the desktop app and launch it against a local backend.

    Usage:
        ./run-manager.ps1                 # against the HTTP API, 127.0.0.1:8000/v1
        ./run-manager.ps1 -Backend tcp     # fall back to the old TCP server, 127.0.0.1:6789
#>
param(
    [ValidateSet('tcp', 'api')]
    [string]$Backend = 'api',
    [string]$Server = '127.0.0.1',
    [int]$Port = 6789,
    [string]$ApiBaseUrl = 'http://127.0.0.1:8000/v1',
    [switch]$Debug = $true
)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot '..\desktop\build.ps1') -Run -Backend $Backend -Server $Server -Port $Port -ApiBaseUrl $ApiBaseUrl -Debug:$Debug

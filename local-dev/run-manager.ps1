<#
    Build the desktop app and launch it against a local backend.

    Usage:
        ./run-manager.ps1                          # against the TCP server, 127.0.0.1:6789
        ./run-manager.ps1 -Backend api              # against the HTTP API, 127.0.0.1:8000/v1
        ./run-manager.ps1 -Backend api -ApiKey dev-key
#>
param(
    [ValidateSet('tcp', 'api')]
    [string]$Backend = 'tcp',
    [string]$Server = '127.0.0.1',
    [int]$Port = 6789,
    [string]$ApiBaseUrl = 'http://127.0.0.1:8000/v1',
    [string]$ApiKey,
    [switch]$Debug = $true
)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot '..\desktop\build.ps1') -Run -Backend $Backend -Server $Server -Port $Port -ApiBaseUrl $ApiBaseUrl -ApiKey $ApiKey -Debug:$Debug

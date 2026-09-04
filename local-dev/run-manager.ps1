<#
    Build the desktop app and launch it against a local API.

    Usage:
        ./run-manager.ps1
        ./run-manager.ps1 -ApiBaseUrl http://10.0.0.5:8000/v1
#>
param(
    [string]$ApiBaseUrl = 'http://127.0.0.1:8000/v1',
    [switch]$Debug = $true
)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot '..\desktop\build.ps1') -Run -ApiBaseUrl $ApiBaseUrl -Debug:$Debug

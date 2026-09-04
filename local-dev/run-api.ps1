<#
    Runs the HTTP API (api/) against its SQLite dev database.
    Requires a venv already set up per api/README.md:
        cd api; python -m venv .venv; .\.venv\Scripts\Activate.ps1; pip install -e ".[dev]"
        python -m alembic upgrade head
        python -m scripts.import_content --content-dir "..\..\Server Content"
#>
$ErrorActionPreference = 'Stop'
$apiDir = Join-Path $PSScriptRoot '..\api'
$venvPython = Join-Path $apiDir '.venv\Scripts\python.exe'

if (-not (Test-Path $venvPython)) {
    throw "No venv at api\.venv - see api\README.md 'Quick start' to set one up first."
}

Write-Host "==> Starting API on http://127.0.0.1:8000  (Ctrl+C to stop, /docs for the interactive reference)"
Push-Location $apiDir
try { & $venvPython -m uvicorn app.main:app --port 8000 } finally { Pop-Location }

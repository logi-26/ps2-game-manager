# api/ — HTTP API + database

Database-backed replacement for the original raw-TCP file server (retired).
FastAPI + SQLAlchemy 2.0 + Alembic. Read-only: it serves the imported
catalogue, nothing writes to it at runtime.

> **Not meant to be self-hosted.** This source is included for transparency
> and so developers can run a local instance to test API changes. The PS2GM
> desktop app talks to its own hosted API at **https://ps2gm.logi26.co.uk** by
> default — end users don't need to run this themselves.

## Quick start

```powershell
cd api
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev]"

python -m alembic upgrade head          # creates api/var/dev.db
uvicorn app.main:app --reload           # http://127.0.0.1:8000/docs
```

`GET /v1/health` should return `{"status":"ok", ...}`. Full endpoint reference
is the interactive docs at `/docs` once it's running.

## Load test data

```powershell
python -m scripts.import_content --content-dir "..\..\Server Content" --console PS2 --limit 500
```

Idempotent — safe to re-run.

## Configuration

`api/.env` (see `.env.example`), all `OPLAPI_`-prefixed — defaults to SQLite +
local filesystem; set `OPLAPI_DATABASE_URL` for Postgres (`compose.yaml` has a
throwaway Postgres + MinIO for that). See `api/app/config.py` for the full list.

## Layout

```
api/
  app/            FastAPI app, models, routers
  alembic/        migrations
  scripts/        import_content.py - legacy "Server Content" -> DB + blobs
  var/            dev.db + blobs/  (gitignored)
```

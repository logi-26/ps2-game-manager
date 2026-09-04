# api/ — HTTP API + database

Database-backed replacement for the raw-TCP `server/`. FastAPI + SQLAlchemy 2.0 +
Alembic. Runs on **SQLite + local filesystem** out of the box; a single env var
switches it to Postgres.

## Quick start

```powershell
cd api
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev]"

python -m alembic upgrade head          # creates api/var/dev.db
uvicorn app.main:app --reload           # http://127.0.0.1:8000/docs
```

`GET /v1/health` should return `{"status":"ok", ...}`.

## Load the legacy data

```powershell
# from api/, venv active, after `alembic upgrade head`
python -m scripts.import_content --content-dir "..\..\Server Content"

# quick partial run while iterating:
python -m scripts.import_content --content-dir "..\..\Server Content" --console PS2 --limit 500
```

Idempotent — safe to re-run. Blobs land in `api/var/blobs/` (content-addressed
`<sha[:2]>/<sha[2:4]>/<sha>`); the DB stores only the sha256.

## App updates

`/v1/app/latest` and `/v1/app/releases/{version}/download` read live from
**GitHub Releases** on this repo (`app/github.py`) — there's no jar mirrored
into the DB. To publish an update: attach a `.jar` asset to a GitHub release
(`gh release create v0.7.0 OPLPOPS-Manager_0.7.0.jar`). Mark it a
pre-release for the `beta` channel; anything else counts as `stable`. An
unauthenticated client is capped at 60 req/hr by GitHub — the module caches
responses for `OPLAPI_GITHUB_CACHE_SECONDS` (default 300s), and setting
`OPLAPI_GITHUB_TOKEN` (no special scope needed) raises that to 5000/hr.

## Switching to Postgres

```powershell
docker compose up -d db                 # from repo root
$env:OPLAPI_DATABASE_URL = "postgresql+psycopg://oplpops:oplpops@localhost:5432/oplpops"
pip install -e ".[postgres]"
python -m alembic upgrade head
```

## Configuration

`api/.env` (see `.env.example`), all `OPLAPI_`-prefixed:

| key | default | meaning |
|---|---|---|
| `OPLAPI_DATABASE_URL` | `sqlite:///api/var/dev.db` | any SQLAlchemy URL |
| `OPLAPI_BLOB_ROOT` | `api/var/blobs` | blob storage root |
| `OPLAPI_WRITE_API_KEY` | *(unset)* | when set, `POST` routes require `X-API-Key` |
| `OPLAPI_MAX_UPLOAD_BYTES` | `33554432` | per-upload cap |
| `OPLAPI_GITHUB_REPO` | `logi-26/ps2-game-manager` | where app releases are read from |
| `OPLAPI_GITHUB_TOKEN` | *(unset)* | raises the GitHub API rate limit from 60/hr to 5000/hr |
| `OPLAPI_GITHUB_CACHE_SECONDS` | `300` | how long a release lookup is cached |

## Endpoints (v1)

| Old TCP command | Endpoint |
|---|---|
| `ART` | `GET /v1/games/{id}/artwork/{kind}/{variant}` → bytes, `ETag`, `304` |
| `ART_NUM` | `GET /v1/games/{id}/artwork/{kind}` → `{count, variants[]}` |
| `ART_LIST` | `GET /v1/artwork?console=PS2` (paged) |
| `CONFIG` / `CONFIG_LIST` | `GET /v1/games/{id}/config` · `GET /v1/configs` |
| `CHEAT` / `CHEAT_LIST` | `GET /v1/games/{id}/cheats` · `GET /v1/cheats` |
| `VMC` / `VMC_LIST` | `GET /v1/games/{id}/vmc[/{vmc_id}]` · `GET /v1/vmc` |
| `UPLOAD_ART/CFG/VMC` | `POST /v1/games/{id}/artwork` · `.../config` · `.../vmc` (→ `pending`) |
| `VERSION` / `UPDATE` | `GET /v1/app/latest` · `GET /v1/app/releases/{v}/download` (both read GitHub Releases live) |
| `CUE2POPS` | `GET /v1/tools/cue2pops?os=windows` |
| `REPORT` | `POST /v1/reports` |
| `RESPOND` | `GET /v1/health` |

Full interactive reference at `/docs` when the server is running.

## Layout

```
api/
  app/
    main.py          FastAPI app + router wiring
    config.py        pydantic-settings (OPLAPI_* / .env)
    db.py            engine + session
    models.py        SQLAlchemy models (the schema)
    schemas.py       pydantic response models
    blobstore.py     content-addressed filesystem blob store
    deps.py          write-key gate, pagination
    routers/         health, games, artwork, files, releases, uploads, reports
  alembic/           migrations
  scripts/
    import_content.py  legacy "Server Content" -> DB + blobs
  var/               dev.db + blobs/  (gitignored)
```

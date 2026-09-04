# ps2-game-manager

Game manager for Open PS2 Loader (OPL) and POPSTARTER — a cross-platform desktop
app for managing PlayStation 1 / PlayStation 2 game, art, config, cheat and save
files, plus the backend that serves shared art/config data.

This is the consolidated repo. It supersedes the three earlier repos
`oplpops-manager`, `oplpops-server`, `oplpops-api` (kept archived for history).

## Layout

| Path | What |
|---|---|
| `desktop/` | Java 8 Swing desktop app (`oplpops.game.manager`). Build with `desktop/build.ps1`. |
| `server/` | Raw-TCP file server (`tcpserver`), port 6789. Build with `server/build.ps1`. **Being replaced by `api/`.** |
| `api/` | HTTP API + database (FastAPI + SQLAlchemy + Alembic). SQLite + local blobs by default; Postgres via one env var. See [`api/README.md`](api/README.md). |
| `local-dev/` | Scripts + fixture generator to run desktop ↔ server on localhost. |
| `docs/plan.html` | The rebuild + overhaul plan (open in a browser; progress is checkable). |
| `compose.yaml` | Optional Postgres + MinIO for running `api/` on non-default infra. |

The desktop app can now talk to either backend: `desktop/src/.../BackendClient.java`
is the shared interface, `MyTCPClient` (old) and `MyApiClient` (new, on
`java.net.http.HttpClient`) both implement it, and
`PopsGameManager.newBackendClient()` picks one — `tcp` by default, `api` via
`-Doplpops.backend=api -Doplpops.api.baseurl=http://host:8000/v1`. Still to
come: cutting the app over screen by screen, then retiring `server/`.

## Build & run locally

Needs a JDK on `PATH` (`javac` + `jar`; tested with Temurin 25 — bytecode targets 11).
PowerShell scripts: run with `pwsh ./x.ps1` or `powershell -ExecutionPolicy Bypass -File x.ps1`.

```powershell
./local-dev/setup-serverdata.ps1     # once — builds server/build-local/serverdata/ fixture
./local-dev/run-server.ps1           # terminal 1 — builds + serves on :6789
./local-dev/run-manager.ps1          # terminal 2 — builds + launches the app at 127.0.0.1:6789
```

`local-dev/protocol-check.py` exercises the server's wire protocol without the GUI.
Full runbook and smoke test: [`local-dev/README.md`](local-dev/README.md).

The desktop app targets the server via `-Doplpops.server.address` /
`-Doplpops.server.port` (or the `OPLPOPS_SERVER_ADDRESS` / `OPLPOPS_SERVER_PORT`
env vars); the run script sets them.

## Local data corpora (not in git)

Two large data sets live **next to** this repo, not inside it (`.gitignore` guards
against committing them):

- **`program_test_folder/`** — a real OPL drive (ART/CFG/CHT/DVD/POPS/…) used as
  the desktop app's test target. ~31 GB of game images.
- **`Server Content/`** — the files the old server served (Covers/Configs/Cheats/…).
  Source data for building the database in the overhaul.

## Attribution

Third-party components bundled under `desktop/lib/`: Apache Commons Net 3.5,
sevenzipjbinding. Bundled tools (cue2pops, hdl_dump, genvmc) credited in
`desktop/`'s generated README. Licensed under GPL-3.0 (see `LICENSE`).

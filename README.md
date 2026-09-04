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
| `api/` | HTTP API + database (FastAPI + SQLAlchemy + Alembic) — the default backend. SQLite + local blobs by default; Postgres via one env var. See [`api/README.md`](api/README.md). |
| `server/` | Raw-TCP file server (`tcpserver`), port 6789. Build with `server/build.ps1`. **Legacy** — kept as a fallback (`-Backend tcp`) until it's retired. |
| `local-dev/` | Scripts + fixture generator to run desktop ↔ backend on localhost. |
| `docs/plan.html` | The rebuild + overhaul plan (open in a browser; progress is checkable). |
| `compose.yaml` | Optional Postgres + MinIO for running `api/` on non-default infra. |

`desktop/src/.../BackendClient.java` is the shared interface; `MyApiClient` (on
`java.net.http.HttpClient`) is the default implementation, `MyTCPClient` (the
original) is still there as a fallback.
`PopsGameManager.newBackendClient()` picks one — `api` unless overridden with
`-Doplpops.backend=tcp -Doplpops.server.address=... -Doplpops.server.port=...`.
Still to come: retiring `server/`/`MyTCPClient` once the API backend has had
enough real-world runway.

## Build & run locally

Needs a JDK on `PATH` (`javac` + `jar`; tested with Temurin 25 — bytecode targets 11).
PowerShell scripts: run with `pwsh ./x.ps1` or `powershell -ExecutionPolicy Bypass -File x.ps1`.

```powershell
# default: HTTP API + the real imported data (after api/'s one-time setup, see api/README.md)
./local-dev/run-api.ps1          # terminal 1 — serves :8000
./local-dev/run-manager.ps1      # terminal 2 — builds + launches the app

# fallback: the old TCP server + a placeholder fixture
./local-dev/setup-serverdata.ps1        # once
./local-dev/run-server.ps1              # terminal 1 — builds + serves on :6789
./local-dev/run-manager.ps1 -Backend tcp  # terminal 2
```

`local-dev/protocol-check.py` exercises the TCP server's wire protocol without
the GUI. Full runbook and smoke test: [`local-dev/README.md`](local-dev/README.md).

## Standalone package (no Java required)

```powershell
./desktop/package.ps1                        # -> desktop/build-local/package/OPLPOPS-Manager/
```

Builds a `jpackage` app-image: a folder with a native `OPLPOPS-Manager.exe` and
a bundled, `jdeps`-trimmed JRE (`java.base` + `java.desktop` + `java.net.http`,
~86 MB total) — zip the `OPLPOPS-Manager` folder and hand it to someone with no
Java installed. `-ApiBaseUrl`/`-Backend` bake in a different default backend for
the recipient. An actual installer (`--type exe`/`msi`) additionally needs the
[WiX Toolset](https://wixtoolset.org/) — not required for the app-image above.

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

# ps2-game-manager

Game manager for Open PS2 Loader (OPL) and POPSTARTER — a cross-platform desktop
app for managing PlayStation 1 / PlayStation 2 game, art, config, cheat and save
files, plus the API that serves shared art/config data.

This is the consolidated repo. It supersedes the three earlier repos
`oplpops-manager`, `oplpops-server`, `oplpops-api` (kept archived for history).

## Layout

| Path | What |
|---|---|
| `desktop/` | Java 8 Swing desktop app (`oplpops.game.manager`). Build with `desktop/build.ps1`. |
| `api/` | HTTP API + database (FastAPI + SQLAlchemy + Alembic) — the backend. SQLite + local blobs by default; Postgres via one env var. See [`api/README.md`](api/README.md). |
| `local-dev/` | Scripts to run desktop ↔ API on localhost. |
| `docs/plan.html` | The rebuild + overhaul plan (open in a browser; progress is checkable). |
| `compose.yaml` | Optional Postgres + MinIO for running `api/` on non-default infra. |

`desktop/src/.../BackendClient.java` is the shared interface; `MyApiClient` (on
`java.net.http.HttpClient`) is the only implementation. The original raw-TCP
file server (`server/`, `MyTCPClient`) has been retired — it had enough
real-world runway on the API backend and was removed rather than kept as a
fallback.

## Build & run locally

Needs a JDK on `PATH` (`javac` + `jar`; tested with Temurin 25 — bytecode targets 11).
PowerShell scripts: run with `pwsh ./x.ps1` or `powershell -ExecutionPolicy Bypass -File x.ps1`.

```powershell
# after api/'s one-time setup, see api/README.md
./local-dev/run-api.ps1          # terminal 1 — serves :8000
./local-dev/run-manager.ps1      # terminal 2 — builds + launches the app
```

Full runbook and smoke test: [`local-dev/README.md`](local-dev/README.md).

## Packaging a release

```powershell
./desktop/release.ps1                         # both of the below in one step
```

Windows users get a standalone `.exe`, no Java required; Mac and Linux users
get a plain jar and use their own Java install:

```powershell
./desktop/package.ps1     # -> desktop/build-local/release/windows/OPLPOPS-Manager/OPLPOPS-Manager.exe
./desktop/bundle-jar.ps1  # -> desktop/build-local/release/jar/
```

`package.ps1` builds a `jpackage` app-image: a folder with a native
`OPLPOPS-Manager.exe` and a bundled, `jdeps`-trimmed JRE (`java.base` +
`java.desktop` + `java.net.http`, ~86 MB total) — zip the `OPLPOPS-Manager`
folder and hand it to a Windows user with no Java installed. jpackage doesn't
cross-compile, so this only ever produces a Windows artifact (a macOS `.app`/
Linux AppImage would need building on those platforms). An actual installer
(`--type exe`/`msi`) additionally needs the [WiX Toolset](https://wixtoolset.org/)
— not required for the app-image above.

`bundle-jar.ps1` builds a plain-jar bundle instead: the jar next to `lib/`,
`hdd/` and `POPSTARTER/`, plus a `run.sh`/`run.cmd`. No bundled JRE — the
recipient needs their own Java 11+ on PATH — but it's the same bundle for
every OS (the native tool binaries and 7-Zip bindings it ships already cover
Windows/macOS/Linux), so it only needs building once, here.

Both scripts take `-ApiBaseUrl` to bake in a different default API for the
recipient.

## Local data corpora (not in git)

Two large data sets live **next to** this repo, not inside it (`.gitignore` guards
against committing them):

- **`program_test_folder/`** — a real OPL drive (ART/CFG/CHT/DVD/POPS/…) used as
  the desktop app's test target. ~31 GB of game images.
- **`Server Content/`** — the files the old server served (Covers/Configs/Cheats/…).
  Source data for building the database in the overhaul.

## Attribution

Third-party components bundled under `desktop/lib/`: Apache Commons Net 3.5,
sevenzipjbinding, [FlatLaf](https://www.formdev.com/flatlaf/) 3.7.2 (Apache-2.0)
— the app's look and feel. Bundled tools (cue2pops, hdl_dump, genvmc) credited
in `desktop/`'s generated README. Licensed under GPL-3.0 (see `LICENSE`).

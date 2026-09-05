# Local dev runbook

The desktop app (`desktop/`) talks to the HTTP API (`api/`) via `MyApiClient`,
the only `BackendClient` implementation (the original raw-TCP server and its
`MyTCPClient` client were retired once the API backend had had enough
real-world runway — see `docs/plan.html`). `desktop`'s `MyFTPClient` is
unrelated to any of this — it talks to a real PS2 console running OPL over
FTP, port 21, and is left as-is.

---

## Prerequisites

- A JDK on `PATH` with `javac` + `jar` (verified: `javac 25.0.1`).
- PowerShell (Windows). Run scripts with `pwsh ./x.ps1` or
  `powershell -ExecutionPolicy Bypass -File x.ps1`.
- Python 3.11 for `api/`'s venv — see `api/README.md`.

---

## Run it

Needs `api/`'s venv set up once (see `api/README.md` "Quick start" +
"Load the legacy data" — `alembic upgrade head` then
`python -m scripts.import_content --content-dir "../Server Content"`).

```powershell
./local-dev/run-api.ps1          # terminal 1 — serves :8000
./local-dev/run-manager.ps1      # terminal 2 — desktop app, real data
```

`http://127.0.0.1:8000/docs` for the interactive API reference while it runs.

This is real, complete data (14,354 games at last import) — no fixture step.
Set the OPL folder to `program_test_folder/` and click through as normal. The
API is read-only (user uploads and bad-file reports were removed as a
feature), so there's nothing to review afterwards.

`run-manager.ps1 -ApiBaseUrl http://<ip>:8000/v1` to point at a non-local API.

---

### Why the app is never run from the project folder

On launch it **rewrites files next to its jar** (`start-ps2gm.*`,
`READ ME.txt`, `settings.xml` / `ps2gm-settings`, `lib/data/data_3`,
`tools/windows/backup/`, …). `desktop/build.ps1 -Run` therefore assembles
`desktop/build-local/run/` and launches from there; all that churn stays
inside `build-local/` (gitignored). `-Fresh` re-copies `lib/ hdd/ POPSTARTER/`
into it.

---

## Source changes carried into this repo

**Part 1** — build on a modern JDK:
- `javax/swing/JCheckBoxList.java` → `ps2gm/game/manager/JCheckBoxList.java`
  — a user class can't live in the `javax.swing` package under the module
  system (JDK 9+). `TestScreen` (dead code), its one importer, updated.

**Part 2** — the API backend:
- `BackendClient` interface; `MyApiClient` is the HTTP implementation
  (`java.net.http.HttpClient`, no new dependency — see `MiniJson` for why).
  All call sites go through `PopsGameManager.newBackendClient()`.
- User uploads and bad-file reports removed as a feature, desktop and API
  side both — the API is read-only.
- `MyAPIClient.java` (the old empty stub) deleted.

**Part 2 (retire)** — the raw-TCP server:
- `server/` (the `tcpserver` project), `MyTCPClient.java`, and the TCP-only
  dev scripts (`run-server.ps1`, `setup-serverdata.ps1`, `protocol-check.py`)
  deleted. `-Backend`/`-Server`/`-Port` flags dropped from every build script
  (`desktop/build.ps1`, `package.ps1`, `bundle-jar.ps1`, `release.ps1`,
  `run-manager.ps1`) along with `PopsGameManager.getServerAddress()` /
  `getServerPort()` / `getBackendMode()` / `TestMode` — the API is now the
  only backend, so there's nothing left to switch between.

---

## Known rough edges (noted, not fixed)

- Swing on JDK 25 / Win10 looks dated; HiDPI scaling may be off. Not blocking.
- `sevenzipjbinding` native load can fail on modern Windows → PS2 game-ID
  detection breaks silently. Usually caused by another PS2GM instance (old
  or new) holding the native library's temp folder locked — don't run two at
  once; clearing `%LOCALAPPDATA%\Temp\SevenZipJBinding-*` also helps.
- `java.net.http.HttpClient` defaults to an HTTP/2 upgrade attempt that
  uvicorn's dev server mishandles (drops POST bodies) — `MyApiClient` pins
  `HTTP_1_1`.

# Local dev runbook

The desktop app (`desktop/`) talks to the HTTP API (`api/`) via `MyApiClient`.
`desktop`'s `MyFTPClient` is unrelated to any of this — it talks to a real PS2
console running OPL over FTP, port 21.

---

## Prerequisites

- A JDK on `PATH` with `javac` + `jar` (verified: `javac 25.0.1`).
- PowerShell (Windows). Run scripts with `pwsh ./x.ps1` or
  `powershell -ExecutionPolicy Bypass -File x.ps1`.
- Python 3.11 for `api/`'s venv — see `api/README.md`.

---

## Run it

Needs `api/`'s venv set up once (see `api/README.md` "Quick start" +
"Load test data" — `alembic upgrade head` then
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

## Known rough edges (noted, not fixed)

- JavaFX/AtlantaFX on JDK 25 / Win10 looks dated in places; HiDPI scaling may
  be off. Not blocking.
- `sevenzipjbinding` native load can fail on modern Windows → PS2 game-ID
  detection breaks silently. Usually caused by another PS2GM instance holding
  the native library's temp folder locked — don't run two at once; clearing
  `%LOCALAPPDATA%\Temp\SevenZipJBinding-*` also helps.
- `java.net.http.HttpClient` defaults to an HTTP/2 upgrade attempt that
  uvicorn's dev server mishandles (drops POST bodies) — `MyApiClient` pins
  `HTTP_1_1`.

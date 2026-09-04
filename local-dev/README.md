# Local dev runbook

Two backends the desktop app (`desktop/`) can run against:

| Component | Talks to | Transport | Status |
|---|---|---|---|
| `desktop` `MyApiClient` | `api` | HTTP, `/v1` | **default** |
| `desktop` `MyTCPClient` | `server` | raw TCP, port 6789 | legacy — kept as a `-Backend tcp` fallback |
| `desktop` `MyFTPClient` | a real PS2 console running OPL | FTP, port 21 | unrelated to either — leave as-is |

Both implement `BackendClient`; `PopsGameManager.newBackendClient()` picks one via
`-Doplpops.backend=api|tcp` (default **`api`** — click-through against the real
data confirmed working, see [`docs/plan.html`](../docs/plan.html) stage 11).

---

## Prerequisites

- A JDK on `PATH` with `javac` + `jar` (verified: `javac 25.0.1`).
- PowerShell (Windows). Run scripts with `pwsh ./x.ps1` or
  `powershell -ExecutionPolicy Bypass -File x.ps1`.
- Python 3.11 with Pillow (for `setup-serverdata.ps1`'s placeholder art, and for
  `api/`'s venv — see `api/README.md`).

---

## Option A — HTTP API, real data (default)

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

---

## Option B — TCP server + fixture (legacy fallback)

```powershell
./local-dev/setup-serverdata.ps1          # once — builds server/build-local/serverdata/
./local-dev/run-server.ps1                # terminal 1 — builds server/, serves on :6789
./local-dev/run-manager.ps1 -Backend tcp  # terminal 2 — builds desktop/, launches against it
```

Fixture has two sample games (`SLUS_207.68` God of War / NTSCU,
`SCES_509.24` Gran Turismo 4 / PAL). `run-manager.ps1 -Backend tcp -Server <ip> -Port <n>`
to point elsewhere.

### Using the real `Server Content` corpus with the TCP server instead

The server resolves its data root from **the folder its jar sits in**:

```powershell
./server/build.ps1
Copy-Item ./server/build-local/TCPServer.jar "../Server Content/"
pushd "../Server Content"; java -jar TCPServer.jar; popd
```

### TCP smoke test

1. Start the server. Expect: `Running OPLPOPS TCP LIVE Server ... Port: 6789`.
2. `python ./local-dev/protocol-check.py` — expect `ART (hit) -> ~5–6 KB` (a
   JPEG), `ART (miss) -> NO_IMAGE`, `ART_NUM -> 1`, `CONFIG`/`CHEAT` -> file
   text, `VERSION -> 0.5,05 April 2017`, `RESPOND -> RESPONSE`.
3. Start the GUI. Expect `Detected OS: Windows 10 64bit` with **no**
   `settings.xml` parse error, then a window.
4. Set the OPL folder (the `program_test_folder/` drive next to the repo
   works), pick **PS2**, load a game list, pull cover art — watch
   `server/build-local/serverdata/Log/traffic_log.txt` fill in.
   `Log/error_log.txt` should **not** appear.

---

### Why the app is never run from the project folder

On launch it **rewrites files next to its jar** (`start-oplpops.*`,
`READ ME.txt`, `settings.xml` / `oplpops-settings`, `lib/data/data_3`,
`tools/windows/backup/`, …), regardless of which backend it talks to.
`desktop/build.ps1 -Run` therefore assembles `desktop/build-local/run/` and
launches from there; all that churn stays inside `build-local/` (gitignored).
`-Fresh` re-copies `lib/ hdd/ POPSTARTER/` into it.

---

## Source changes carried into this repo

**Part 1** — build on a modern JDK:
- `PopsGameManager.getServerAddress()` / `getServerPort()` honour
  `-Doplpops.server.address` / `-Doplpops.server.port` (env vars too),
  falling back to the old hardcoded `192.168.0.60` / `6789`.
- `javax/swing/JCheckBoxList.java` → `oplpops/game/manager/JCheckBoxList.java`
  — a user class can't live in the `javax.swing` package under the module
  system (JDK 9+). `TestScreen` (dead code), its one importer, updated.

**Part 2** — the API backend:
- `BackendClient` interface; `MyTCPClient` implements it unchanged;
  `MyApiClient` is the HTTP implementation (`java.net.http.HttpClient`,
  no new dependency — see `MiniJson` for why). All 21 call sites go through
  `PopsGameManager.newBackendClient()`, whose default flipped from `tcp` to
  `api` once the click-through passed.
- User uploads and bad-file reports removed as a feature, desktop and API
  side both (see `docs/plan.html` stage 11) — the API is read-only.
- `MyAPIClient.java` (the old empty stub) deleted.

---

## Known rough edges (noted, not fixed)

- Swing on JDK 25 / Win10 looks dated; HiDPI scaling may be off. Not blocking.
- `sevenzipjbinding` native load can fail on modern Windows → PS2 game-ID
  detection breaks silently. Usually caused by another OPLPOPS instance (old
  or new) holding the native library's temp folder locked — don't run two at
  once; clearing `%LOCALAPPDATA%\Temp\SevenZipJBinding-*` also helps.
- TCP protocol only: the upload header's 2-digit length prefix means headers
  <10 or >99 chars are already malformed. Not applicable to the API backend.
- `server/` has no path sanitisation, auth, or connection limits — fine for
  localhost, do not expose it.
- `java.net.http.HttpClient` defaults to an HTTP/2 upgrade attempt that
  uvicorn's dev server mishandles (drops POST bodies) — `MyApiClient` pins
  `HTTP_1_1`.

# Local dev runbook (Part 1: get it running again)

Goal: run the **desktop app** (`desktop/`) against the **backend file server**
(`server/`) entirely on `localhost`, built from source with the JDK you already
have (Temurin 25 — bytecode is targeted at 11).

The `api/` service (database + HTTP API) is Part 2 and doesn't exist yet.

---

## What each piece is

| Component | Talks to | Transport | Touched in Part 1 |
|---|---|---|---|
| `desktop` `MyTCPClient` | `server` | raw TCP, port 6789 | **yes** — pointed at localhost |
| `desktop` `MyFTPClient` | a real PS2 console running OPL | FTP, port 21 | no — leave as-is |
| `desktop` `MyAPIClient` | nothing (empty stub) | — | no — filled in during Part 2 |
| `server` | the filesystem next to its own jar | — | **yes** — served from a fixture |

---

## Prerequisites

- A JDK on `PATH` with `javac` + `jar` (verified: `javac 25.0.1`).
- PowerShell (Windows). Run scripts with `pwsh ./x.ps1` or
  `powershell -ExecutionPolicy Bypass -File x.ps1`.
- Python 3.11 with Pillow (only for `setup-serverdata.ps1`'s placeholder art —
  verified present as `python`).

---

## One-time setup

```powershell
# from the repo root
./local-dev/setup-serverdata.ps1        # builds server/build-local/serverdata/
```

This creates the folder tree the server expects and drops in placeholder art +
config/cheat files for two sample games:

| game id | region | title |
|---|---|---|
| `SLUS_207.68` | `NTSCU` | God of War |
| `SCES_509.24` | `PAL`   | Gran Turismo 4 |

### Using the real `Server Content` corpus instead

The server resolves its data root from **the folder its jar sits in**. To serve
the real data instead of the fixture, copy the built jar next to the corpus and
run it there:

```powershell
./server/build.ps1                                    # just build
Copy-Item ./server/build-local/TCPServer.jar "../Server Content/"
pushd "../Server Content"; java -jar TCPServer.jar; popd
```

(`Server Content/` lives next to the repo, not in it.)

---

## Run it (fixture)

Two terminals from the repo root:

```powershell
# terminal 1 — backend
./local-dev/run-server.ps1
#   builds server/, copies the jar into serverdata/, starts it on :6789

# terminal 2 — desktop app
./local-dev/run-manager.ps1
#   builds desktop/, assembles an isolated desktop/build-local/run/ dir
#   (jar + copies of lib/ hdd/ POPSTARTER/), launches it with
#   -Doplpops.server.address=127.0.0.1 -Doplpops.server.port=6789 -DEBUG
```

`run-manager.ps1 -Server <ip> -Port <n>` to point elsewhere.
`./desktop/build.ps1 -Run -Fresh` to re-copy the data dirs into `run/`.

### Why the app is never run from the project folder

On launch the app **rewrites files next to its jar** (`start-oplpops.*`,
`READ ME.txt`, `settings.xml` / `oplpops-settings`, `lib/data/data_3`,
`tools/windows/backup/`, …). `build.ps1 -Run` therefore assembles
`desktop/build-local/run/` and launches from there; all that churn stays inside
`build-local/` (gitignored).

---

## Smoke test

1. Start the server (terminal 1). Expect:
   `Running OPLPOPS TCP LIVE Server ... Port: 6789`.
2. Protocol check without the GUI, while the server runs:
   ```powershell
   python ./local-dev/protocol-check.py
   ```
   Expect `ART (hit) -> ~5–6 KB` (a JPEG), `ART (miss) -> NO_IMAGE`,
   `ART_NUM -> 1`, `CONFIG`/`CHEAT` -> file text, `VERSION -> 0.5,05 April 2017`,
   `RESPOND -> RESPONSE`.
3. Start the GUI (terminal 2). Expect it to reach
   `Detected OS: Windows 10 64bit` with **no** `settings.xml` parse error, then a
   window.
4. In the GUI: set the OPL folder when prompted (the `program_test_folder/` OPL
   drive next to the repo works), pick **PS2**, load a game list, pull cover art —
   the request goes to the local server; watch
   `server/build-local/serverdata/Log/traffic_log.txt` fill in.
5. `server/build-local/serverdata/Log/error_log.txt` should **not** appear (only
   created on a bad/oversized request).

---

## Source changes carried into this repo (Part 1)

- **`PopsGameManager.getServerAddress()` / `getServerPort()`** — now honour
  `-Doplpops.server.address` / `-Doplpops.server.port` (or the
  `OPLPOPS_SERVER_ADDRESS` / `OPLPOPS_SERVER_PORT` env vars), falling back to the
  old hardcoded `192.168.0.60` / `6789`.
- **`javax/swing/JCheckBoxList.java` → `oplpops/game/manager/JCheckBoxList.java`** —
  a user class can't live in the `javax.swing` package under the module system
  (JDK 9+). Repackaged; the one importer (`TestScreen`, dead code) updated.
- New `desktop/build.ps1`, `server/build.ps1`, this `local-dev/`, and a
  monorepo-appropriate root `.gitignore`.

Nothing else in the ~24k-LOC app was modified.

---

## Known rough edges (noted, not fixed)

- Swing on JDK 25 / Win10 looks dated; HiDPI scaling may be off. Not blocking.
- `sevenzipjbinding` native load can fail on modern Windows → archive
  extraction features break, app still starts.
- The upload protocol prefixes the text header with a **2-digit** length, so
  requests whose header is <10 or >99 chars are already malformed.
- `server/` has no path sanitisation, auth, or connection limits — fine for
  localhost, do not expose it.

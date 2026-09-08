# PS2GM

**Game manager for Open PS2 Loader (OPL) and POPSTARTER.**

PS2GM is a cross-platform desktop app for managing your PlayStation 1 and
PlayStation 2 game library on an OPL setup: it lists the games on your drive and
lets you download and organise the box art, per-game configs, cheat files and
virtual memory cards that go with them. It talks to the PS2GM database API to
pull that shared content, and bundles the tools needed to add games in the first
place (`cue2pops`, `hdl_dump`, `genvmc`).

It works against an OPL folder wherever it lives — on this PC, on a USB drive, or
on the PS2's internal hard drive over the network — for both PS1 (POPS) and PS2
(OPL) titles.

> **Beta.** PS2GM can write to your PS2's internal HDD. There is a real risk of
> data corruption. Use it at your own risk and keep backups.

## Features

- **Game list** for PS1 and PS2, read straight from your OPL folder, with
  compatibility-flag colouring (PS1) and UL-game highlighting (PS2).
- **Artwork** — front/rear cover, spine, disc, logo, screenshots and background,
  per game. Pick a local image, download from the database, or auto-fetch, all
  from one screen.
- **Per-game config** (`.cfg`) — video mode, aspect ratio, GSM, compatibility
  modes, PADEMU, cheat toggles, ratings, plus free-text metadata (developer,
  release date, players, description). Download from the database or edit by hand.
- **Cheats** (`.cht`) — edit the local cheat file or pull cheats from the
  database, with the codes colour-coded by type.
- **Virtual memory cards** — create per-game VMCs (`genvmc`) and download
  ready-made ones.
- **Add games**
  - **PS1** — from a `.cue`/`.bin` (converted with `cue2pops`), with ELF and
    POPSTARTER handling.
  - **PS2** — from an ISO, copied to the PS2 HDD with `hdl_dump`.
  - **Batch** add many at once, and **batch download** art/config/cheats for the
    whole list.
- **UL format** — convert a PS2 ISO to OPL's split "UL" format and back.
- **Generate** `conf_apps.cfg`, `ul.cfg`, PS1 ELF files and spine artwork.
- **Housekeeping** — find and delete unused or all ART / CFG / CHT / ELF / spine
  files; rename games (32-char limit); fix mis-named VCD/ISO files; MD5-hash a
  game.
- **Launch** the selected game in your configured PS1 or PS2 emulator, or from
  the game-list right-click menu.
- **File transfer** — browse the PS2 over FTP and push files to it, or set the
  remote ART/CFG/CHT locations.
- **Themes** — five built-in looks (Primer, Nord in light and dark, plus Dracula)
  chosen from *File → Settings → Theme*; the choice is remembered.
- **Update check** against the API.

## Download

Grab a build from the [Releases](https://github.com/logi-26/ps2-game-manager/releases)
page.

### Windows

Download the `PS2GM` folder (zipped), unzip it anywhere, and run `PS2GM.exe`.
A trimmed Java runtime is bundled — **no Java install needed**.

### macOS / Linux

Download the jar bundle (zipped), unzip it, and run the launcher:

```sh
chmod +x run.sh
./run.sh
```

You need **Java 17 or newer** on your `PATH` (`java -version` to check). The
bundle ships the JavaFX libraries for every OS; `run.sh` picks the right ones for
your machine. On Windows the same bundle has a `run.cmd`.

## Getting started

On first launch PS2GM asks for two things:

1. **Your OPL folder** — the directory that contains `ART/`, `CFG/`, `CHT/`,
   `POPS/`, etc.
2. **Where it lives** — see *Modes* below.

Then pick **PlayStation 1** or **PlayStation 2** from the *Console* menu, select a
game in the list, and use the **ART / CFG / CHT / VMC** buttons (or the menus) to
work on it. Right-click a game for quick **Run in Emulator**, **Rename** and
**Delete**.

## Notes

##### Modes

Set from *Console → Mode → Set Mode*, or on first launch:

- **OPL folder on this PC / network share** — read and write files directly.
- **OPL folder on a USB drive** — same, on removable media.
- **OPL folder on the PS2's internal HDD** — connects to the console over the
  network (FTP + `hdl_dump`); you enter the PS2's IP address. Some operations
  (e.g. renaming a PS2 game) aren't available in this mode.

##### Downloading content

Art, configs, cheats and VMCs are fetched from the PS2GM database API. By default
the app talks to PS2GM's own hosted API at `https://ps2gm.logi26.co.uk/v1` — no
setup needed. A packaged build can be pointed elsewhere at build time
(`-ApiBaseUrl`) or at runtime with the `PS2GM_API_BASEURL` environment variable
/ `-Dps2gm.api.baseurl=` system property (mainly useful for local API
development, see *Running against the API*). The app also contacts the API on
startup (game-list sync, update check), so it needs to be reachable to launch.

##### Adding games

`cue2pops`, `pops2cue`, `hdl_dump` and `genvmc` binaries for Windows and Linux
ship in `lib/data/tools/`. PS1 games are converted from `.cue`/`.bin`; PS2 games
are transferred from an ISO. Batch variants handle a whole folder at once.

##### UL format

*Tools → Convert ISO to UL Format* splits a PS2 ISO into OPL's `UL.<...>` chunks
and updates `ul.cfg`; *Convert UL Format to ISO* rebuilds a single ISO.

##### Files the app writes

PS2GM regenerates a few files **next to its own jar/exe** on every launch —
`READ ME.txt`, `start-ps2gm.*`, its settings file, and parts of `lib/data/`. This
is why the build scripts always run it from an isolated copy (see below) rather
than from a source checkout.

## Building from source

### Requirements

- A **JDK** on `PATH` (`javac` + `jar`; developed with Temurin 25, bytecode
  targets Java 21).
- **PowerShell** to run the build scripts — Windows PowerShell 5.1, or
  [PowerShell 7+](https://github.com/PowerShell/PowerShell) (`pwsh`) on
  macOS/Linux.

The app talks to PS2GM's hosted API by default, so no API setup is needed just
to build and run it. You'd only run your own API instance (see `api/README.md`)
if you're developing against API changes yourself.

### Run it

```powershell
pwsh ./desktop/build.ps1 -Run          # compile, then launch
pwsh ./desktop/build.ps1 -Run -ApiBaseUrl http://10.0.0.5:8000/v1
```

`-Run` compiles into `desktop/build-local/`, assembles an isolated
`desktop/build-local/run/` (jar + the data folders the app reads), and launches
from there so your checkout stays clean. `-Stage` does the assembly without
launching; `-Fresh` re-seeds the staged data folders; `-Debug` passes `-DEBUG`
(see *Debugging*).

For a one-command "API + app on localhost" loop:

```powershell
pwsh ./local-dev/run-api.ps1        # terminal 1
pwsh ./local-dev/run-manager.ps1    # terminal 2
```

### Package a release

```powershell
pwsh ./desktop/release.ps1     # builds both artifacts below
```

- **`desktop/package.ps1`** → `desktop/build-local/release/windows/PS2GM/PS2GM.exe`
  — a `jpackage` app-image with a trimmed bundled JRE. `jpackage` can't
  cross-compile, so this only ever produces a **Windows** build; a macOS `.app`
  or Linux package would have to be built on those platforms.
- **`desktop/bundle-jar.ps1`** → `desktop/build-local/release/jar/` — the plain
  jar plus `run.sh` / `run.cmd`. Runs on **Windows, macOS and Linux** (recipient
  supplies Java 17+); the bundle carries the JavaFX natives for all four
  win/mac/mac-aarch64/linux targets and the launcher selects the right set.

Both scripts accept `-ApiBaseUrl` to bake a different default API into the build.

### Editing the UI

Screens are FXML (`desktop/src/ps2gm/game/manager/fx/*.fxml`) and open in
[Gluon Scene Builder](https://gluonhq.com/products/scene-builder/). Scene Builder
stamps a newer `xmlns` version on save; `build.ps1` rewrites it back to 21 in the
packaged copies, so that warning in a dev run is harmless.

## Running against the API

The desktop app needs the PS2GM API for downloads and update checks, and by
default it talks to PS2GM's own hosted instance at `https://ps2gm.logi26.co.uk`
— nothing to set up.

The API's source (`api/`, FastAPI + SQLite/Postgres) is included in this repo
for transparency and so developers can run their own instance to test API
changes; it isn't meant for end users to self-host. See
[`api/README.md`](api/README.md) and [`local-dev/README.md`](local-dev/README.md)
if you're doing that kind of development.

## Debugging

- **`-DEBUG`** (from `build.ps1 -Run -Debug`, or `PS2GM.exe -DEBUG`, or
  `./run.sh -DEBUG`) prints extra startup and error detail to the console. The
  packaged `.exe` has no console, so run it from a terminal to see the output.
- The app logs errors and the detected OS/arch to **stdout** — launch from a
  terminal to watch it.
- Settings and generated files live **next to the jar/exe**. When debugging a
  packaged build, that's the `PS2GM/app/` folder (Windows) or the unzipped
  bundle folder.
- A dev build launches from `desktop/build-local/run/` — all its runtime churn
  stays there and is gitignored.

## Repository layout

| Path | What |
|---|---|
| `desktop/` | The desktop app (`ps2gm.game.manager`, Java + JavaFX). Build with `desktop/build.ps1`. |
| `api/` | Source for the hosted API (art/config/cheat/VMC content). Included for transparency / local dev testing, not for self-hosting. See [`api/README.md`](api/README.md). |
| `local-dev/` | Scripts to run the app and API together on localhost. |
| `docs/` | Design notes. |

## Credits

- `cue2pops` / `pops2cue` — krHACKen
- `hdl_dump` — The Wizard of Oz, AKuHAK and contributors
- `genvmc` — jimmikaelkael
- Inspired by the official OPL Manager by danielb
- UI: [JavaFX](https://openjfx.io/), [AtlantaFX](https://github.com/mkpaz/atlantafx),
  [Ikonli](https://kordamp.org/ikonli/); also Apache Commons Net and
  sevenzipjbinding

Licensed under **GPL-3.0** — see [`LICENSE`](LICENSE).

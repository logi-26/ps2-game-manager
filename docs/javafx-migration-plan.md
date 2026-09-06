# JavaFX migration plan

Branch: **`javafx-ui`** (off `main` at `2bc8f67`).

**STATUS: code-complete (`19e0952`).** Every screen is JavaFX, the app entry is
`ps2gm.game.manager.fx.MainApp` (an `Application` subclass), Swing / FlatLaf /
dead code is deleted, packaging is wired. What's left before merging to `main`:
1. An interactive test pass on a real PS2 (Tier C + MainScreen's console paths
   have only been build- + headless-smoke-tested).
2. ~~Vendor mac / mac-aarch64 / linux JavaFX native jars so `bundle-jar.ps1`
   isn't Windows-only.~~ **Done:** all four JavaFX classifiers are vendored under
   `lib/javafx/`; `build.ps1 -AllPlatformFx` stages them and `bundle-jar.ps1`'s
   generated `run.sh` / `run.cmd` pick the host set at launch (via an explicit
   `-cp`, since the jar manifest's `Class-Path` still hard-codes `-win`).

**Decisions:** FXML + Scene Builder · Java 21 / JavaFX 21.0.5 + AtlantaFX 2.0.1
(all on the classpath, not the module path) · dev JDK is Temurin 25
(`--enable-native-access=ALL-UNNAMED` mutes the native-load warning).

---

## Established patterns

Every ported screen is a triple in `ps2gm.game.manager.fx`:

| file | role |
|---|---|
| `<Name>Screen.fxml` | Scene Builder layout, `fx:controller` = the controller |
| `<Name>Controller.java` | `@FXML` fields + handlers; an `init(...)` / `setX(...)` package-private method the façade calls before the window shows |
| `<Name>Screen.java` | public façade with the **old call signature**, body is one `FxScreens.open(...)` |

- **`FxRuntime.ensureStarted()`** — one-time `Platform.startup` + `setImplicitExit(false)`.
- **`FxScreens.open(fxml, title, resizable, Consumer<C> init)`** — boot / load / wire
  controller / show. If the controller `implements FxScreens.StageAware`, its
  `stageReady(Stage)` is called before `show()` (for close-veto, title that depends
  on a ctor arg, `FileChooser` owner, …).
- **Wire the Swing call site** (`MainScreen.jMenuItem…ActionPerformed`, always
  outside `//GEN` regions) to the façade; **leave the Swing class in place** until
  the whole branch merges.
- **Backend coupling** (a manager calls back into the Swing dialog) → extract a
  toolkit-agnostic callback interface in `ps2gm.game.manager`, make the manager take
  it, have the Swing screen implement it (marshalling to the EDT) and the FX
  controller implement it (marshalling to `Platform.runLater`). Done once for
  `USBUtil` → `SplitMergeProgress`.
- **Verification:** a throwaway `scratchpad/FxSmoke*.java` that drives the real
  façade and inspects the scene (labels set, list populated, `snapshot()` renders,
  a background task wrote its file). Anything with a real filesystem/console side
  effect that can't be faked is verified by running the app.
- **Already toolkit-agnostic, reuse as-is:** `MyListener`
  (`updateGameList`, via `PopsGameManager.callbackToUpdateGUIGameList`),
  `ImageSelectListener`, `ImageChangedListener`.

---

## Done

**Screens:** About · Changelog · HashChecker · SetPartition · EmulatorSettings ·
GameVMC · SplitMerge · GameLongName · **GameRenamingPS1/PS2** ·
**BatchDownloadPS1/PS2** · **GameImagePS1/PS2 + GameImageSelectorPS1/PS2** ·
**AddGameHDDPS1/PS2** · **SetModeScreen** · **AddGameSMBScreen** ·
**SyncFileScreen** · **GameCheatScreen** · **GameConfigScreen**.
**Infra:** `FxRuntime`, `FxScreens.open` + `openModal` + `StageAware`.
**Decouples:** `SplitMergeProgress` (USBUtil), `GameLongNameRenamer`,
`FtpTransferProgress` (MyFTPClient + HDLDumpManager).
**Made public for the `fx` subpackage:** `GameLongNameRenamer`,
`GameArtFileManager` (+ `isMissing`/`resolve`/`baseName`/`deleteAll`),
`GameConfigFileManager.renameConfigTitlesToMatch`.

Tier A + the GameImage pair (Tier B) are done. **Tier C is done** except its
overlap with GameCheat/GameConfig (still Tier B):
- `FtpTransferProgress` — one toolkit-agnostic progress sink for `MyFTPClient`
  (PS1 FTP upload) and `HDLDumpManager` (PS2 hdl_dump). Both managers' inner
  SwingWorkers became plain daemon-thread methods; the Swing screens implement
  the interface marshalling to the EDT until they're deleted.
- `AddGameHddScreen` — one façade, `AddGameHddPs1Controller` (VCD/ELF folder
  combos, POPSTARTER check, cue2pops + ELF gen + FTP connect on a daemon thread)
  and `AddGameHddPs2Controller` (IP + hdl_dump).
- `SetModeScreen` — `FxScreens.openModal`, still blocks first launch. Browse uses
  a `DirectoryChooser`; the console-list fetches in `connectToPS2` run off the
  FX thread. Dead "USB - HDD" stub dropped.
- `AddGameSmbScreen` — fire-and-forget progress window; both inner SwingWorkers
  → daemon-thread work + `Platform.runLater`.
- `SyncFileScreen` — two-pane browser; FTP directory listings / get / put /
  delete on a daemon thread (`runFtp`). No decouple (no progress-bar path).

---

## Remaining — 3 screen classes (MainScreen + 2 dead)

Delete first, not ports:
- **`TestScreen`** (96) — dead code.
- **`GameCheatScreenNew`** (176) — no references anywhere; dead code.

### Tier A — DONE

| Screen | LOC | Notes / plan |
|---|---|---|
| **GameRenamingScreenPS1 / PS2** | 575 / 484 | Batch-fix invalidly-named VCD/ISO files: a list of bad files + target-name field + Rename; `File.renameTo` then `GameListManager.addToGameListsPS1/2`, leftovers go to `stillInvalidGameList…`. Self-contained. FX: `ListView` + `TextField` + button; same list-refresh + `callbackToUpdateGUIGameList` pattern as GameLongName. **Do these next.** |
| ~~**GameImageSelectorScreenPS1 / PS2**~~ | 295 / 298 | **DONE** (`cdec299`, with the GameImage screens). `GameImageSelectorController` — one preview `ImageView`, arrows to cycle, "Use this image". Parent passes itself as both `ImageChangedListener` / `ImageSelectListener` and gets the live controller back through a `Consumer` so it can push the next downloaded file in. |
| **BatchDownloadScreenPS1 / PS2** | 814 / 812 | Batch art/config download. Big because of the 8 image-preview panels (already resized during earlier bug-fix work) + an inner `BackgroundWorker` whose callbacks are **all internal** (`jTextFieldGameName`, `createList`, `displayGameImages`) — no manager coupling, so no decouple. FX: `GridPane` of preview panes, checkboxes per art type, a `ProgressBar`, and one `Task` per file (reuse the HashChecker `Task` pattern). Medium-large, mechanical. |

### Tier B — large, self-contained

| Screen | LOC | Notes / plan |
|---|---|---|
| ~~**GameImageScreenPS1 / PS2**~~ | 1124 / 1125 | **DONE** (`cdec299`). `GameImageController` + `GameImageSelectorController`, one pair for both consoles (differ only in game list, cover aspect ratio, "no image" cover placeholder). FXML `FlowPane` of 8 `TitledPane`s, each `ImageView` + File/Auto/Del `Button`s carrying the cover type in `userData`. Fixed preview dims carried across (no live node size → no window creep). File button = `manualImageSelection` on the EDT via `SwingUtilities.invokeAndWait`, then re-read `ART/`. Auto/next backend calls on daemon threads + `Platform.runLater`. Selector close-without-save deletes the file + fires `imageSelected` so the parent falls back to its placeholder. |
| ~~**GameCheatScreen**~~ | 810 | **DONE** (`90e9856`). `GameCheatController` - editable `TextArea` (local cheat file) + a colour-coded `ListView` of server cheats (styling via a cell factory + `styleFor()`, replacing the `JTextPane` `StyledDocument`). Server fetch (index + per-game cheats + widescreen resource scan) on a daemon thread. Swing's drag-select + right-click-append → multi-select `ListView` + "<< Add" button; the PS1 `$`-prefix transform kept. `compareCheatFile` still prompts to save on nav / close. |
| ~~**GameConfigScreen**~~ | 2975 | **DONE** (`79fdf23`). `GameConfigController` - the 27-slot `newConfigData` + 6-slot global model kept as-is (read/written via `GameConfigFileManager`); GSM/Cheat/PADEMU Source radios + the preserve-the-other-side logic ported; the 9 nested rating switches became 3 data tables (`RATING_IMAGE`/`RATING_READ`/`RATING_WRITE`, "ofcl" typo kept); the 5-star widget is 3 `ImageView` mouse handlers + `renderStars(n)`; converters ported verbatim. Deliberate deviation: Save stays enabled while the form is active (Swing disabled it right after a load). Smoke-tested: a hand-written .cfg loads into every widget, Save round-trips, unmanaged keys survive. Per-game OPL `.cfg` editor — compatibility flags, GSM, cheats, VMC slots, PADEMU, a 5-star rating widget (custom mouse-hover handlers → an FX star control or a `Rating` from ControlsFX/AtlantaFX), ~11 `JOptionPane`. Reads/writes via `GameConfigFileManager` (`readGameConfigFormatted` / `writeGameConfigFile`, index-mapped `NEW_CONFIG_DATA` array) — **that mapping stays; only the widgets change.** No worker. Break the FXML into `TitledPane` sections. Budget a whole session; consider sub-tasking (layout, then per-section binding, then save round-trip). |

### Tier C — DONE (`eef4a05` decouple, `b45bf0e` `50df44e` `5d04f88` `1995af6`)

| Screen | LOC | Outcome |
|---|---|---|
| ~~**AddGameHDDScreenPS1 / PS2**~~ | 813 / 427 | `FtpTransferProgress` extracted (`setProgressRange/setProgress/setTimeRemaining/setUploadSpeed/setGameName/setGameCounter/setInProgress/closeWindow`; `includeElf` passed as a method arg). `MyFTPClient.addGameToPS2` + `HDLDumpManager.hdlDumpUploadGame/…Batch` take it; every inner SwingWorker → a plain daemon-thread method. `AddGameHddPs1Controller` / `AddGameHddPs2Controller` + one `AddGameHddScreen` façade. PS1 cue2pops + ELF gen + `connectToConsole` on a daemon thread; PS2 single upload still leaves its window open, matching the manager. |
| ~~**SetModeScreen**~~ | 701 | `FxScreens.openModal` still blocks first launch. `DirectoryChooser` for Browse; the `hdl_dump` (PS2) + FTP (PS1) list fetches in `connectToPS2` run off the FX thread; the two "fetch which list?" confirms stay on it. Save ports `jButtonSaveModeActionPerformed` verbatim. Cancel / close still `Runtime.exit(0)` when no OPL dir is set. Dead "USB - HDD" stub panel dropped. IP field is a plain `TextField`. |
| ~~**AddGameSMBScreen**~~ | 657 | Fire-and-forget progress window - starts the copy on show, closes itself when done. No manager coupling, so no decouple; the two inner SwingWorkers → daemon-thread work + `Platform.runLater`. `addGame` / `batchAddGame` / `launchCueToPops` / `launchCueToPopsBatch` / `alreadyInGameList*` ported. `finish()` = the workers' shared `done()`. |
| ~~**SyncFileScreen**~~ | 964 | No progress-bar upload path → no decouple. FTP directory listings / `getFile` / `addFileToPS2` / `deleteRemoteFile` moved onto a daemon thread (`runFtp`). Two `ListView`s with mutual-exclusion selection. Partition `ComboBox` writes `RemoteOPLPath` + settings.xml. ART region-code filtering + PS1 `__common` cheat browsing ported. |

**Not yet verified against a real console** - built + FXML-load / field-inject
smoke only. The FTP / hdl_dump paths need a live PS2.

### Last — DONE

| Screen | LOC | Outcome |
|---|---|---|
| ~~**MainScreen**~~ | 2731 | **DONE** (`598c435` port, `8ade8d1` Swing deletion). `MainScreen.fxml` + `MainController implements MyListener`; `ps2gm.game.manager.fx.MainApp` (an `Application` subclass) is the entry point. `Main.main` → `PopsGameManager.startApplication()` (headless setup) → `MainApp.run()`. FlatLaf gone → **AtlantaFX** (`PrimerLight`/`PrimerDark`, live toggle - Swing needed a restart). `updateGameList` / `displayGameDetails` cascade, console switching, compat-colour list cells, all ~50 menu actions ported. Blocking actions (emulator launch, update check, console refresh, FTP delete / ELF regen) now on daemon threads. `FxScreens.openModal` gained an FX-thread `showAndWait` path; `FxRuntime.markStarted()` for the `Application.launch` lifecycle. `FxRuntime` / `FxScreens` / the screen façades were **kept** - they're just an FXML-into-Stage helper, no coexistence cost. |

---

## Cross-cutting

- **Packaging (`19e0952`):** `package.ps1` (jpackage app-image) works - the
  JavaFX + AtlantaFX jars ride into `app/lib/javafx/` via the manifest
  `Class-Path` and load off the classpath; verified the built `PS2GM.exe`
  launches and boots the FX toolkit. `build.ps1` vendors
  `atlantafx-base-2.0.1.jar` + Ikonli. `bundle-jar.ps1` (plain jar) is now
  cross-platform: `lib/javafx/` carries all four JavaFX classifiers,
  `build.ps1 -AllPlatformFx` stages them, and the generated `run.sh` / `run.cmd`
  select the host's set on an explicit `-cp` (the manifest `Class-Path` still
  points at `-win`, so `java -jar` alone stays Windows-only).
- **Done:** deleted `TestScreen`, `GameCheatScreenNew`, `AppTheme`, `flatlaf`,
  every replaced Swing `*Screen.java` (`8ade8d1`).

## Suggested order

1. ~~`GameRenamingScreenPS1/PS2`~~ — done (`713f0cc`)
2. ~~`GameImageSelectorScreenPS1/PS2` + `GameImageScreenPS1/PS2`~~ — done (`cdec299`)
3. ~~`BatchDownloadScreenPS1/PS2`~~ — done (`0c6309f`)
4. ~~`FtpTransferProgress` decouple → `AddGameHDDScreenPS1/PS2`~~ — done (`eef4a05`, `b45bf0e`)
5. ~~`SetModeScreen`~~ — done (`50df44e`)
6. ~~`AddGameSMBScreen`, `SyncFileScreen`~~ — done (`5d04f88`, `1995af6`)
7. ~~`GameCheatScreen` (Tier B)~~ — done (`90e9856`)
8. ~~`GameConfigScreen` (Tier B monster)~~ — done (`79fdf23`)
9. ~~`MainScreen` + entry-point flip + AtlantaFX~~ — done (`598c435`)
10. ~~Packaging scripts, delete Swing classes~~ — done (`8ade8d1`, `19e0952`)
11. ~~mac/linux JavaFX natives for `bundle-jar.ps1`~~ — done
12. **Left:** real-PS2 test pass · merge to `main`

# JavaFX migration plan

Branch: **`javafx-ui`** (off `main` at `2bc8f67`). Incremental — Swing stays the
entry point and opens FX windows as separate `Stage`s in one process; merge back to
`main` only when every screen is ported and tested.

**Decisions:** FXML + Scene Builder · Java 21 / JavaFX 21.0.5 (classpath, not module
path) · dev JDK is Temurin 25 (`--enable-native-access=ALL-UNNAMED` mutes the
warnings) · AtlantaFX considered later for FlatLaf-like theming.

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
**BatchDownloadPS1/PS2** · **GameImagePS1/PS2 + GameImageSelectorPS1/PS2**.
**Infra:** `FxRuntime`, `FxScreens.open` + `openModal` + `StageAware`.
**Decouples:** `SplitMergeProgress` (USBUtil), `GameLongNameRenamer`.
**Made public for the `fx` subpackage:** `GameLongNameRenamer`,
`GameArtFileManager` (+ `isMissing`/`resolve`/`baseName`/`deleteAll`),
`GameConfigFileManager.renameConfigTitlesToMatch`.

Tier A is done. `GameImageScreen` / `GameImageSelectorScreen` (Tier B) are done
too — one `GameImageController` / `GameImageSelectorController` pair for both
consoles; File button runs the Swing `manualImageSelection` on the EDT then
re-reads `ART/` (no `javafx-swing`); Auto / next-image on daemon threads.

---

## Remaining — 10 screen classes

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
| **GameCheatScreen** | 810 | Cheat-code editor: game list + a big editable text area of codes + Save + server fetch (download shared cheats). No `SwingWorker`; server calls are synchronous today (move them to a `Task`). `GameConfigFileManager` / server for read/write. Self-contained. Medium-large. **Do this next.** |
| **GameConfigScreen** | 2975 | **The monster.** Per-game OPL `.cfg` editor — compatibility flags, GSM, cheats, VMC slots, PADEMU, a 5-star rating widget (custom mouse-hover handlers → an FX star control or a `Rating` from ControlsFX/AtlantaFX), ~11 `JOptionPane`. Reads/writes via `GameConfigFileManager` (`readGameConfigFormatted` / `writeGameConfigFile`, index-mapped `NEW_CONFIG_DATA` array) — **that mapping stays; only the widgets change.** No worker. Break the FXML into `TitledPane` sections. Budget a whole session; consider sub-tasking (layout, then per-section binding, then save round-trip). |

### Tier C — needs a backend decouple first

| Screen | LOC | Blocker → decouple |
|---|---|---|
| **AddGameHDDScreenPS1** | 813 | `MyFTPClient.addGameToPS2(AddGameHDDScreenPS1, …)` + its inner `BackgroundWorker extends SwingWorker` call `screen.getProgressBar / getTimeRemainingLabel / getUploadSpeedLabel / getGameNameLabel / getGameCounterLabel / setUploadInProgress(bool) / closeDialog() / includeElfFile()`. **Extract `FtpTransferProgress`** (like `SplitMergeProgress` but richer: `setProgressRange/setProgress/setTimeRemaining(String)/setUploadSpeed(String)/setGameName(String)/setGameCounter(String)/setInProgress(bool)/finished()`); `includeElfFile()` is an *input* — pass it as a method arg, don't call back for it. `BackgroundWorker` → plain daemon thread. |
| **AddGameHDDScreenPS2** | 427 | Same, via `HDLDumpManager.hdlDumpUploadGame/…Batch(AddGameHDDScreenPS2, …)` + two `SwingWorker`s with the identical label/progress surface. **Reuse the same `FtpTransferProgress`.** |
| **SetModeScreen** | 701 | First-launch mode picker — **critical path**. `HDLDumpManager.hdlDumpGetTOC` + `GameListManager.getGameListFromConsolePS1()` (FTP) run **synchronously on the EDT** in `connectToPS2()`; 4 `JFileChooser`; 11 `JOptionPane`; window-close **exits the app** (`Runtime.getRuntime().exit(0)`) if the OPL dir isn't set. Radio-driven panel enable/disable, `saveMode()` writes settings.xml. Move the console fetch to a `Task`; FX `Alert`/`FileChooser` for the dialogs; keep the exit-on-close in `stageReady`'s `setOnCloseRequest`. Do this **after** `AddGameHDD*` so the TOC download can reuse the decouple. Verify by real first-launch run. |
| **AddGameSMBScreen** | 657 | SMB/USB import: 3 `SwingWorker`s, `launchCueToPops` / `launchCueToPopsBatch` (public — cue2pops archive extraction), 11 `JOptionPane`, 7-arg ctor (batchMode, path, ext, file, index). Callbacks are mostly internal but the workers touch a progress bar. Convert the workers to `Task`s; a small internal progress interface if any manager reaches in (check `AddGameManager`). Large. |
| **SyncFileScreen** | 964 | Two-pane local/remote file diff + sync via `MyFTPClient` (`listRemoteDirectory` + per-file transfer). Uses FTP reads mainly; check whether it hits the same progress-bar upload path as `AddGameHDD*` (if so, reuse `FtpTransferProgress`; if only directory listing + `renameFile`, no decouple). `compose{Local,Remote}FileList` are public but likely self-calls. Large. |

### Last

| Screen | LOC | Notes |
|---|---|---|
| **MainScreen** | 2785 | The shell: `JMenuBar`, game `JList`, detail panel, toolbar, `implements MyListener`. Port last — every child screen must be FX first (or still openable from an FX menu). When done, **retire `FxRuntime` / `FxScreens` coexistence glue**, make an `Application` subclass the real entry point, and update `Main.java` + `build.ps1` (`Application.launch`, module-path option revisited). |

---

## Cross-cutting

- **Packaging is broken on this branch.** `package.ps1` (jpackage) and
  `bundle-jar.ps1` have no JavaFX: need `jlink` with the JavaFX `.jmods`
  (or `jpackage --module-path <jmods>`), and **mac/Linux FX natives** added
  alongside the current `-win` jars (or switch to the classified
  `org.openjfx:*:{win,mac,linux}` set and pick per-OS in the scripts).
  `release.ps1` calls both. Do this before any release off `javafx-ui`.
- **New shared interface:** `FtpTransferProgress` (Tier C) — one interface for
  `MyFTPClient` + `HDLDumpManager`, mirrors `SplitMergeProgress`.
- **Delete** `TestScreen`, `GameCheatScreenNew` (dead), and every Swing
  `*Screen.java` once its FX replacement is merged.
- **AtlantaFX**: add `atlantafx-base` to `lib/javafx/`, `Application.setUserAgentStylesheet(...)`
  once MainScreen is FX; gives light/dark to match the current FlatLaf toggle.

## Suggested order

1. `GameRenamingScreenPS1/PS2` (Tier A, quick win)
2. ~~`GameImageSelectorScreenPS1/PS2` + `GameImageScreenPS1/PS2`~~ — done (`cdec299`)
3. `BatchDownloadScreenPS1/PS2` (Tier A)
4. `GameCheatScreen` (Tier B)
5. `FtpTransferProgress` decouple → `AddGameHDDScreenPS1/PS2` (Tier C)
6. `SetModeScreen` (Tier C, reuses the decouple)
7. `AddGameSMBScreen`, `SyncFileScreen` (Tier C)
8. `GameConfigScreen` (Tier B monster — own session)
9. `MainScreen` + retire coexistence glue + AtlantaFX
10. Packaging scripts, delete Swing classes, merge to `main`

package ps2gm.game.manager.fx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import ps2gm.game.manager.AddGameManager;
import ps2gm.game.manager.BackgroundTasks;
import ps2gm.game.manager.Console;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.Mode;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code AddGameSmbScreen.fxml} - a fire-and-forget progress window
 * that copies a game (PS1 VCD/CUE, PS2 ISO/ZSO) into the OPL folder for SMB / USB
 * modes, running cue2pops first when needed. JavaFX replacement for the Swing
 * {@code AddGameSMBScreen}; the two inner SwingWorkers become plain daemon-thread
 * work reporting through {@code Platform.runLater}. No manager coupling - every
 * callback in the Swing version was internal.
 */
public class AddGameSmbController implements FxScreens.StageAware {

    @FXML private TitledPane outerPane;
    @FXML private Label gameNameLabel, sizeLabel;
    @FXML private ProgressBar progressBar;

    private Stage stage;
    private boolean batchMode;
    private String fileExtension;
    private File selectedFile;
    private final List<File> badCueFileList = new ArrayList<>();

    /** Called from the façade before the window is shown. */
    void init(boolean batchMode, String fileExtension, File selectedFile) {
        this.batchMode = batchMode;
        this.fileExtension = fileExtension;
        this.selectedFile = selectedFile;
    }

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(PopsGameManager.getCurrentConsole() == Console.PS1 ? " Add PlayStation Game" : " Add PlayStation 2 Game");
        if (selectedFile != null) { outerPane.setText(selectedFile.getName()); }
        // The Swing X-close just told the user to wait; do the same.
        stage.setOnCloseRequest(e -> {
            e.consume();
            alert(Alert.AlertType.WARNING, "Please wait for the process to complete, this window should close automatically.", " Performing Operation!");
        });

        int playstation = PopsGameManager.getCurrentConsole() == Console.PS1 ? 1 : 2;
        BackgroundTasks.runDaemon("fx-add-game-smb", () -> {
            try {
                if (!batchMode) { addGame(playstation); } else { batchAddGame(playstation); }
            } catch (Exception ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
                finish();
            }
        });
    }

    // --- port of addGame(int) ----------------------------------------------

    private void addGame(int playstation) {
        if (selectedFile == null) { finish(); return; }

        switch (fileExtension.toLowerCase()) {
            case "cue":
                if (playstation == 1) {
                    try { launchCueToPops(selectedFile); }
                    catch (IOException | InterruptedException ex) { PopsGameManager.displayErrorMessageDebug("Error launching cue2pops!\n\n" + ex.toString()); finish(); }
                } else { finish(); }
                break;
            case "vcd":
                if (playstation == 1) {
                    File vcdFile = selectedFile;
                    String newFileName = AddGameManager.truncate(vcdFile.getName(), vcdFile.getName().length() - 3);
                    String newFileFullName = newFileName + "VCD";
                    String out = PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + newFileFullName;
                    if (alreadyInGameListPS1(vcdFile)) {
                        info("This game is already in your game list.", " No Games to Add!");
                        copyThenRefresh(false, vcdFile.getAbsolutePath(), out, newFileName, false);
                    } else {
                        copyThenRefresh(true, vcdFile.getAbsolutePath(), out, newFileName, false);
                    }
                } else { finish(); }
                break;
            case "iso":
            case "zso":
                if (playstation == 2) {
                    addPs2Game();
                } else { finish(); }
                break;
            default:
                finish();
                break;
        }
    }

    private void addPs2Game() {
        File isoFile = selectedFile;
        String gameID = null;
        try { gameID = GameListManager.getPS2GameIDFromArchive(isoFile.getAbsolutePath()); }
        catch (Exception ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }

        if (gameID == null) {
            info("The unique ID for this game could not be identified.", " Invalid Game!");
            copyThenRefresh(false, isoFile.getAbsolutePath(), PopsGameManager.getOPLFolder() + File.separator + "DVD" + File.separator + "null", null, false);
            return;
        }

        if (alreadyInGameListPS2(isoFile)) {
            info("This game is already in your game list.", " No Games to Add!");
            copyThenRefresh(false, isoFile.getAbsolutePath(), PopsGameManager.getOPLFolder() + File.separator + "DVD" + File.separator + "null", null, false);
            return;
        }

        String newFileName = AddGameManager.truncate(isoFile.getName(), isoFile.getName().length() - 3);
        String newFileFullName = newFileName + fileExtension.toLowerCase();

        if (PopsGameManager.getCurrentMode() == Mode.SMB) {
            copyThenRefresh(true, isoFile.getAbsolutePath(), PopsGameManager.getOPLFolder() + File.separator + "DVD" + File.separator + newFileFullName, newFileName, false);
        } else if (PopsGameManager.getCurrentMode() == Mode.HDD_USB) {
            // USB mode: a straight move, no progress copy (matches the Swing shortcut).
            if (fileExtension.equalsIgnoreCase("zso")) {
                isoFile.renameTo(new File(PopsGameManager.getOPLFolder() + File.separator + newFileFullName));
            } else if (isoFile.length() > 4294967296L) {
                PopsGameManager.displayMessageDebug("PS2 ISO File greater than 4GB!");
            } else {
                PopsGameManager.displayMessageDebug("PS2 ISO File smaller than 4GB!\nAdding to USB!");
                isoFile.renameTo(new File(PopsGameManager.getOPLFolder() + File.separator + newFileFullName));
            }
            finish();
        } else {
            finish();
        }
    }

    // --- port of batchAddGame(int) ---------------------------------------

    private void batchAddGame(int playstation) {
        if (playstation == 1) {
            List<File> cueFileList = new ArrayList<>();
            File[] files = selectedFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith("cue")) {
                        File associatedBin = new File(file.getAbsolutePath().substring(0, file.getAbsolutePath().length() - 3) + "bin");
                        File associatedVcd = new File(file.getAbsolutePath().substring(0, file.getAbsolutePath().length() - 3) + "VCD");
                        if (associatedBin.exists() && associatedBin.isFile() && !associatedVcd.exists()) { cueFileList.add(file); }
                    }
                }
            }
            launchCueToPopsBatch(cueFileList);
        } else {
            List<File> isoFileList = new ArrayList<>();
            File[] files = selectedFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String ext = file.getName().toLowerCase().substring(file.getName().length() - 3);
                        if ((ext.equals("iso") || ext.equals("zso")) && !alreadyInGameListPS2(file)) { isoFileList.add(file); }
                    }
                }
            }
            if (isoFileList.isEmpty()) { info("Could not locate any new games to add.", " No Games to Add!"); }
            batchCopyThenRefresh(isoFileList);
        }
    }

    private void launchCueToPops(File cueFile) throws IOException, InterruptedException {
        String newFileName = AddGameManager.truncate(cueFile.getName(), cueFile.getName().length() - 3);
        String newFileFullName = newFileName + "VCD";
        String newFilePath = AddGameManager.truncate(cueFile.toString(), cueFile.toString().length() - newFileFullName.length()) + newFileFullName;

        boolean vcdGenerated = false;
        try { vcdGenerated = AddGameManager.launchCueToPops(cueFile); }
        catch (IOException | InterruptedException ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }

        if (alreadyInGameListPS1(new File(cueFile.getAbsolutePath().substring(0, cueFile.getAbsolutePath().length() - 3) + "VCD"))) {
            info("This game is already in your game list.", " No Games to Add!");
            vcdGenerated = false;
        }

        copyThenRefresh(vcdGenerated, newFilePath, PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + newFileFullName, newFileName, true);
    }

    private void launchCueToPopsBatch(List<File> cueFileList) {
        badCueFileList.clear();
        for (File cueFile : cueFileList) {
            try { if (!AddGameManager.launchCueToPops(cueFile)) { badCueFileList.add(cueFile); } }
            catch (IOException | InterruptedException ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
        }

        List<File> vcdFileList = new ArrayList<>();
        File[] files = selectedFile.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().substring(file.getName().length() - 3).equalsIgnoreCase("vcd")) {
                    if (!alreadyInGameListPS1(new File(file.getAbsolutePath().substring(0, file.getAbsolutePath().length() - 3) + "VCD"))) { vcdFileList.add(file); }
                }
            }
        }
        if (vcdFileList.isEmpty()) { info("Could not locate any new games to add.", " No Games to Add!"); }
        batchCopyThenRefresh(vcdFileList);
    }

    // --- port of the single BackgroundWorker -----------------------------

    private void copyThenRefresh(boolean vcdGenerated, String inPath, String outPathIn, String fileName, boolean deleteOriginal) {
        if (!vcdGenerated) { finish(); return; }

        String outPath = outPathIn;
        try {
            String newFileName = AddGameManager.truncate(new File(inPath).getName(), new File(inPath).getName().length() - 3);
            String gameID;
            if (PopsGameManager.getCurrentConsole() == Console.PS1) { gameID = GameListManager.getPS1GameIDFromVCD(new File(inPath)); }
            else { gameID = GameListManager.getPS2GameIDFromArchive(inPath); }

            if (gameID == null) {
                info("The unique ID for this game could not be identified.", " Invalid Game!");
                finish();
                return;
            }

            setGameName(" " + selectedFile.getName().substring(0, selectedFile.getName().length() - 4) + " - (" + gameID + ")");

            if (PopsGameManager.getCurrentConsole() == Console.PS1) {
                if (!outPath.contains(gameID)) { outPath = outPath.substring(0, outPath.length() - 4) + "-" + gameID + ".VCD"; }
            } else {
                if (!outPath.contains(gameID)) {
                    outPath = outPath.substring(0, outPath.lastIndexOf(File.separator) + 1) + gameID + "." + newFileName + new File(inPath).getName().substring(new File(inPath).getName().length() - 4);
                }
            }

            File originalPath = new File(inPath);
            copyWithProgress(originalPath, new File(outPath));

            if (PopsGameManager.getCurrentConsole() == Console.PS1 && new File(outPath).isFile()) {
                AddGameManager.generateElf(PopsGameManager.getFilePrefix() + newFileName.substring(0, newFileName.length() - 1) + "-" + gameID + ".ELF",
                        PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator);
            }
            if (deleteOriginal) { originalPath.delete(); }
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
        finish();
    }

    // --- port of BatchBackgroundWorker ---------------------------------

    private void batchCopyThenRefresh(List<File> fileList) {
        for (File sel : fileList) {
            try {
                String newFileName;
                String newFileFullName;
                String inPath;
                String outPath;
                if (PopsGameManager.getCurrentConsole() == Console.PS1) {
                    newFileName = AddGameManager.truncate(sel.getName(), sel.getName().length() - 3);
                    newFileFullName = newFileName + "VCD";
                    inPath = AddGameManager.truncate(sel.toString(), sel.toString().length() - newFileFullName.length()) + newFileFullName;
                    outPath = PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + newFileFullName;
                } else {
                    newFileName = AddGameManager.truncate(sel.getName(), sel.getName().length() - 3);
                    newFileFullName = newFileName + sel.getName().substring(sel.getName().length() - 3);
                    inPath = AddGameManager.truncate(sel.toString(), sel.toString().length() - newFileFullName.length()) + newFileFullName;
                    outPath = PopsGameManager.getOPLFolder() + File.separator + "DVD" + File.separator + newFileFullName;
                }

                String gameID;
                if (PopsGameManager.getCurrentConsole() == Console.PS1) { gameID = GameListManager.getPS1GameIDFromVCD(new File(inPath)); }
                else { gameID = GameListManager.getPS2GameIDFromArchive(inPath); }

                if (gameID == null) {
                    info("The unique ID for this game could not be identified.", " Invalid Game!");
                    continue;
                }

                setGameName(" " + sel.getName().substring(0, sel.getName().length() - 4) + " - (" + gameID + ")");

                if (PopsGameManager.getCurrentConsole() == Console.PS1) { outPath = outPath.substring(0, outPath.length() - 4) + "-" + gameID + ".VCD"; }
                else { outPath = outPath.substring(0, outPath.lastIndexOf(File.separator) + 1) + gameID + "." + newFileFullName; }

                File originalPath = new File(inPath);
                copyWithProgress(originalPath, new File(outPath));

                if (PopsGameManager.getCurrentConsole() == Console.PS1 && new File(outPath).isFile()) {
                    AddGameManager.generateElf(PopsGameManager.getFilePrefix() + newFileName.substring(0, newFileName.length() - 1) + "-" + gameID + ".ELF",
                            PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator);
                }
                originalPath.delete();
            } catch (Exception ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
        }

        if (!badCueFileList.isEmpty()) {
            StringBuilder sb = new StringBuilder("Unable to convert the following files to VCD:    \n\n");
            for (File file : badCueFileList) { sb.append(file.getName(), 0, file.getName().length() - 4).append("\n"); }
            sb.append("\n");
            alert(Alert.AlertType.ERROR, sb.toString(), " Unable to Convert Files");
        }
        finish();
    }

    private void copyWithProgress(File source, File dest) throws IOException {
        long total = source.length();
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            long counter = 0;
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
                counter += length;
                long done = counter;
                Platform.runLater(() -> {
                    sizeLabel.setText(PopsGameManager.bytesToHuman(done));
                    if (total > 0) { progressBar.setProgress(done / (double) total); }
                });
            }
        }
    }

    // --- helpers --------------------------------------------------------

    private boolean alreadyInGameListPS1(File vcdFile) {
        String vcdGameID = null;
        try { vcdGameID = GameListManager.getPS1GameIDFromVCD(vcdFile); }
        catch (Exception ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
        if (vcdGameID == null) { return false; }
        List<Game> list = GameListManager.getGameListPS1();
        if (list == null) { return false; }
        for (Game g : list) { if (g.getGameID().equals(vcdGameID)) { return true; } }
        return false;
    }

    private boolean alreadyInGameListPS2(File isoFile) {
        String isoGameID = null;
        try { isoGameID = GameListManager.getPS2GameIDFromArchive(isoFile.getAbsolutePath()); }
        catch (Exception ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
        if (isoGameID == null) { return false; }
        List<Game> list = GameListManager.getGameListPS2();
        if (list == null) { return false; }
        for (Game g : list) { if (g.getGameID().equals(isoGameID)) { return true; } }
        return false;
    }

    // Port of the workers' done(): regen the list, write conf_elm (PS1), refresh the main GUI, close.
    private boolean finished = false;

    private void finish() {
        if (finished) { return; }
        finished = true;
        if (PopsGameManager.getCurrentConsole() == Console.PS1) {
            GameListManager.createGameListsPS1();
            GameListManager.writeConfigELM();
        } else {
            GameListManager.createGameListsPS2(true);
        }
        PopsGameManager.callbackToUpdateGUIGameList(null, -1);
        Platform.runLater(() -> { if (stage != null) stage.close(); });
    }

    private void setGameName(String text) { Platform.runLater(() -> gameNameLabel.setText(text)); }

    private void info(String message, String title) { alert(Alert.AlertType.INFORMATION, message, title); }

    private void alert(Alert.AlertType type, String message, String title) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message);
            alert.setHeaderText(null);
            alert.setTitle(title);
            if (stage != null) { alert.initOwner(stage); }
            WindowsDarkTitleBar.apply(alert);
            alert.showAndWait();
        });
    }
}

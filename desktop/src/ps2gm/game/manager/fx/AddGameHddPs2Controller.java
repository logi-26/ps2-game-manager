package ps2gm.game.manager.fx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import ps2gm.game.manager.FtpTransferProgress;
import ps2gm.game.manager.HDLDumpManager;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code AddGameHddPs2Screen.fxml} - upload a PS2 game (or a folder
 * of them, batch mode) to the console's internal HDD via {@code hdl_dump}. JavaFX
 * replacement for the Swing {@code AddGameHDDScreenPS2}; the transfer runs on
 * {@link HDLDumpManager}'s own daemon thread and reports back through
 * {@link FtpTransferProgress}.
 */
public class AddGameHddPs2Controller implements FxScreens.StageAware, FtpTransferProgress {

    private static final String[] REGION_CODES = {"SCES_","SLES_","SCUS_","SLUS_","SLPS_","SCAJ_","SLKA_","SLPM_","SCPS_"};

    @FXML private TextField gamePathField, gameNameField, gameCounterField, ipField;
    @FXML private Button uploadButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label timeRemainingLabel, uploadSpeedLabel;

    private Stage stage;
    private boolean batchMode;
    private String selectedPath;
    private File gameFile;
    private volatile boolean uploadInProgress;
    private int progressMax = 100;

    @FXML
    private void initialize() {
        // FX has no MaskFormatter - just restrict to digits and dots (matches SetPartitionScreen).
        ipField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("[0-9.]{0,15}") ? change : null));
    }

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(batchMode ? "Add PS2 Game to HDD - Batch Mode" : "Add PS2 Game to HDD");
        stage.setOnCloseRequest(e -> {
            if (uploadInProgress) {
                e.consume();
                warn("This window will automatically close once the current operation has completed.", " Currently Performing Operation!");
            }
        });
    }

    /** Called from the façade before the window is shown. */
    void init(boolean batchMode, String selectedPath, File selectedFile) {
        this.batchMode = batchMode;
        this.selectedPath = selectedPath;
        this.gameFile = selectedFile;

        gamePathField.setText(" " + gameFile.getPath());

        if (!batchMode) {
            String gameName = gameFile.getName().substring(0, gameFile.getName().length() - 4);
            for (String regionCode : REGION_CODES) {
                if (gameName.contains(regionCode)) { gameName = gameName.substring(12); }
            }
            gameNameField.setText(gameName);
        } else {
            File[] files = new File(selectedPath).listFiles();
            int isoCount = 0;
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().length() > 4 && f.getName().substring(f.getName().length() - 3).equalsIgnoreCase("ISO")) { isoCount++; }
                }
            }
            gameNameField.setText("         " + isoCount + " games will be uploaded to the console!");
            gameNameField.setEditable(false);
            gameCounterField.setVisible(true);
            gameCounterField.setManaged(true);
            gameCounterField.setText("0/" + isoCount);
        }

        if (PopsGameManager.getPS2IP() != null) { ipField.setText(PopsGameManager.getPS2IP()); }
    }

    @FXML
    private void onUpload() {
        if (ipField.getText() == null || ipField.getText().isEmpty()) {
            warn("You need to enter the IP address of your PS2 console into the text field.", " No IP Address Entered!");
            return;
        }
        uploadGamePS2();
    }

    private void uploadGamePS2() {
        String ip = ipField.getText();

        if (!batchMode) {
            if (gameFile.getPath() == null) {
                warn("You need to select a game to upload.", " No game selected!");
                return;
            }
            String name = gameNameField.getText();
            String path = gameFile.getPath();
            uploadButton.setDisable(true);
            uploadInProgress = true;
            new Thread(() -> {
                try {
                    new HDLDumpManager().hdlDumpUploadGame(this, ip, name, path);
                } catch (IOException | InterruptedException ex) {
                    PopsGameManager.displayErrorMessageDebug(ex.toString());
                }
            }, "fx-add-game-hdd-ps2").start();
        } else {
            ArrayList<Path> ps2GamesInDirectory = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(Paths.get(selectedPath))) {
                paths.forEach(filePath -> {
                    String fn = filePath.getFileName().toString();
                    if (Files.isRegularFile(filePath) && fn.length() > 4 && fn.substring(fn.length() - 4).equalsIgnoreCase(".iso")) {
                        ps2GamesInDirectory.add(filePath);
                    }
                });
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }

            if (ps2GamesInDirectory.isEmpty()) { return; }
            uploadButton.setDisable(true);
            uploadInProgress = true;
            new Thread(() -> {
                try {
                    new HDLDumpManager().hdlDumpUploadGameBatch(this, ip, ps2GamesInDirectory);
                } catch (IOException | InterruptedException ex) {
                    PopsGameManager.displayErrorMessageDebug(ex.toString());
                }
            }, "fx-add-game-hdd-ps2-batch").start();
        }
    }

    // --- FtpTransferProgress (called from HDLDumpManager's thread) ------------

    @Override public void setProgressRange(int max) { this.progressMax = max <= 0 ? 100 : max; }
    @Override public void setProgress(int value) { Platform.runLater(() -> progressBar.setProgress(value / (double) progressMax)); }
    @Override public void setTimeRemaining(String text) { Platform.runLater(() -> timeRemainingLabel.setText(text)); }
    @Override public void setUploadSpeed(String text) { Platform.runLater(() -> uploadSpeedLabel.setText(text)); }
    @Override public void setGameName(String text) { Platform.runLater(() -> gameNameField.setText(text)); }
    @Override public void setGameCounter(String text) { Platform.runLater(() -> gameCounterField.setText(text)); }
    @Override public void setInProgress(boolean uploading) { this.uploadInProgress = uploading; }
    @Override public void closeWindow() { Platform.runLater(() -> { if (stage != null) stage.close(); }); }

    private void warn(String message, String title) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.setTitle(title);
        if (stage != null) { alert.initOwner(stage); }
        WindowsDarkTitleBar.apply(alert);
        alert.showAndWait();
    }
}

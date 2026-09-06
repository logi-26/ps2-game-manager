package ps2gm.game.manager.fx;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import ps2gm.game.manager.AddGameManager;
import ps2gm.game.manager.FtpTransferProgress;
import ps2gm.game.manager.MyFTPClient;
import ps2gm.game.manager.PopsGameManager;
import ps2gm.game.manager.XMLFileManager;

/**
 * Controller for {@code AddGameHddPs1Screen.fxml} - upload a PS1 game (or a folder
 * of them, batch mode) to the console's internal HDD over FTP. JavaFX replacement
 * for the Swing {@code AddGameHDDScreenPS1}. cue2pops conversion, ELF generation
 * and the FTP connect run on a daemon thread; {@link MyFTPClient} then runs the
 * upload on its own thread and reports back through {@link FtpTransferProgress}.
 */
public class AddGameHddPs1Controller implements FxScreens.StageAware, FtpTransferProgress {

    private static final String[] REGION_CODES = {"SCES_","SLES_","SCUS_","SLUS_","SLPS_","SCAJ_","SLKA_","SLPM_","SCPS_"};
    private static final String[] VCD_DRIVES = {"hdd0:/", "mass:/", "mass1:/", "mass2:/"};
    private static final String[] VCD_FOLDERS_FULL = {"__.POPS", "__.POPS0", "__.POPS1", "__.POPS2", "__.POPS3", "__.POPS4", "__.POPS5", "__.POPS6", "__.POPS7", "__.POPS8", "__.POPS9"};
    private static final String[] VCD_FOLDERS_MASS = {"POPS"};
    private static final String[] ELF_FOLDERS_FULL = {"+OPL/", "__.POPS/", "__.POPS0/", "__.POPS1/", "__.POPS2/", "__.POPS3/", "__.POPS4/", "__.POPS5/", "__.POPS6/", "__.POPS7/", "__.POPS8/", "__.POPS9/"};
    private static final String[] ELF_FOLDERS_MASS = {"POPS/"};

    @FXML private TextField gamePathField, gameNameField, gameCounterField, ipField, elfSubfolderField;
    @FXML private ComboBox<String> vcdDriveCombo, vcdFolderCombo, elfFolderCombo;
    @FXML private CheckBox includeElfCheck;
    @FXML private Button uploadButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label timeRemainingLabel, uploadSpeedLabel;

    private Stage stage;
    private boolean batchMode;
    private String selectedPath;
    private File gameFile;
    private volatile boolean uploadInProgress;
    private int progressMax = 100;

    private final List<File> vcdFilesInDirectory = new ArrayList<>();
    private final List<File> cueFilesInDirectory = new ArrayList<>();

    @FXML
    private void initialize() {
        vcdDriveCombo.setItems(FXCollections.observableArrayList(VCD_DRIVES));
        vcdFolderCombo.setItems(FXCollections.observableArrayList(VCD_FOLDERS_FULL));
        elfFolderCombo.setItems(FXCollections.observableArrayList(ELF_FOLDERS_FULL));

        ipField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("[0-9.]{0,15}") ? change : null));

        // mass: VCD drive -> only "POPS" is valid for the VCD/ELF sub-folders (matches the Swing combo listener)
        vcdDriveCombo.valueProperty().addListener((obs, old, val) -> {
            boolean mass = val != null && val.startsWith("m");
            vcdFolderCombo.setItems(FXCollections.observableArrayList(mass ? VCD_FOLDERS_MASS : VCD_FOLDERS_FULL));
            elfFolderCombo.setItems(FXCollections.observableArrayList(mass ? ELF_FOLDERS_MASS : ELF_FOLDERS_FULL));
            vcdFolderCombo.getSelectionModel().selectFirst();
            elfFolderCombo.getSelectionModel().selectFirst();
        });

        includeElfCheck.selectedProperty().addListener((obs, old, on) -> {
            elfFolderCombo.setDisable(!on);
            elfSubfolderField.setDisable(!on);
        });
    }

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(batchMode ? "Add PS1 Game to HDD - Batch Mode" : "Add PS1 Game to HDD");
        stage.setOnCloseRequest(e -> {
            if (uploadInProgress) {
                e.consume();
                warn("This window cannot be closed until the current upload has been completed.", " Upload in Progress");
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
            if (gameName.length() > 12) {
                List<String> regionCodeList = new ArrayList<>(Arrays.asList(REGION_CODES));
                if (regionCodeList.contains(gameName.substring(0, 5))) {
                    gameName = gameName.substring(0, gameName.length() - 12);
                } else if (regionCodeList.contains(gameName.substring(gameName.length() - 11, gameName.length() - 6))) {
                    gameName = gameName.substring(0, gameName.length() - 12);
                }
            }
            gameNameField.setText(gameName);
        } else {
            File[] files = new File(selectedPath).listFiles();
            if (files != null) {
                for (File f : files) { if (f.isFile() && f.getName().length() > 4 && f.getName().substring(f.getName().length() - 3).equalsIgnoreCase("VCD")) { vcdFilesInDirectory.add(f); } }
                for (File f : files) { if (f.isFile() && f.getName().length() > 4 && f.getName().substring(f.getName().length() - 3).equalsIgnoreCase("CUE")) { cueFilesInDirectory.add(f); } }
            }
            int total = vcdFilesInDirectory.size() + cueFilesInDirectory.size();
            gameNameField.setText("         " + total + " games will be uploaded to the console!");
            gameNameField.setEditable(false);
            gameCounterField.setVisible(true);
            gameCounterField.setManaged(true);
            gameCounterField.setText("0/" + total);
        }

        includeElfCheck.setSelected(true);

        // Load the VCD path values into the GUI
        String[] splitVCDPath = PopsGameManager.getRemoteVCDPath().split("/");
        vcdDriveCombo.setValue(splitVCDPath[0] + "/");
        if (splitVCDPath.length > 1) { vcdFolderCombo.setValue(splitVCDPath[1]); }

        // Load the ELF path values into the GUI
        String[] splitElfPath = PopsGameManager.getRemoteELFPath().split("/");
        if (splitElfPath.length > 1) {
            elfFolderCombo.setValue(splitElfPath[1] + "/");
            StringBuilder sub = new StringBuilder();
            for (int i = 2; i < splitElfPath.length; i++) { sub.append(splitElfPath[i]); }
            elfSubfolderField.setText(sub.toString());
        }

        if (PopsGameManager.getPS2IP() != null) { ipField.setText(PopsGameManager.getPS2IP()); }
    }

    @FXML
    private void onUpload() {
        if (ipField.getText() == null || ipField.getText().isEmpty()) {
            warn("You need to enter the IP address of your PS2 console into the text field.", " No IP Address Entered!");
            return;
        }

        // Check POPSTARTER.ELF exists (handle a lower-case .elf left over from an extract)
        File popstarterFile = new File(PopsGameManager.getCurrentDirectory() + File.separator + "POPSTARTER" + File.separator + "POPSTARTER.elf");
        if (popstarterFile.exists() && !popstarterFile.isDirectory()) {
            popstarterFile.renameTo(new File(PopsGameManager.getCurrentDirectory() + File.separator + "POPSTARTER" + File.separator + "POPSTARTER.ELF"));
        }
        popstarterFile = new File(PopsGameManager.getCurrentDirectory() + File.separator + "POPSTARTER" + File.separator + "POPSTARTER.ELF");
        if (!(popstarterFile.exists() && popstarterFile.isFile())) {
            error("Could not locate POPSTARTER.ELF in the POPSTARTER directory.", " Missing POPSTARTER.ELF!");
            return;
        }

        Alert notice = new Alert(Alert.AlertType.WARNING, "Ensure that the FTP server is currently running on your console, then click \"OK\".");
        notice.setHeaderText(null);
        notice.setTitle(" PS1 Game Transfers Require an FTP Connection");
        if (stage != null) { notice.initOwner(stage); }
        notice.showAndWait();

        // Snapshot the form on the FX thread
        String vcd1 = vcdDriveCombo.getValue();
        String vcd2 = vcdFolderCombo.getValue();
        String elf1 = elfFolderCombo.getValue();
        String elf2 = elfSubfolderField.getText();
        String ip = ipField.getText();
        boolean includeElf = includeElfCheck.isSelected();
        String newGameFileName = gameNameField.getText();

        // Persist the remote paths + IP (as the Swing screen did before connecting)
        PopsGameManager.setRemoteVCDPath(vcd1 + vcd2);
        String elfPath = null;
        if (elf1 != null && elf1.startsWith("+")) {
            String[] splitOPLPath = PopsGameManager.getRemoteOPLPath().split("/");
            elfPath = splitOPLPath[0] + "/" + elf1 + elf2;
        } else if (elf1 != null && elf1.startsWith("_")) {
            String[] splitVCDPath = PopsGameManager.getRemoteVCDPath().split("/");
            elfPath = splitVCDPath[0] + "/" + elf1 + elf2;
        }
        PopsGameManager.setRemoteELFPath(elfPath);
        PopsGameManager.setPS2IP(ip);
        try {
            XMLFileManager.writeSettingsXML();
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug("Error saving the settings!\n\n" + ex.toString());
        }

        setFormDisabled(true);
        uploadInProgress = true;

        new Thread(() -> {
            MyFTPClient myFTP = new MyFTPClient();
            if (myFTP.connectToConsole(ip)) {
                if (!batchMode) { uploadSingleMode(myFTP, newGameFileName, includeElf); }
                else { uploadBatchMode(myFTP, includeElf); }
            } else {
                Platform.runLater(() -> { setFormDisabled(false); uploadInProgress = false; });
            }
        }, "fx-add-game-hdd-ps1").start();
    }

    // Faithful port of the Swing uploadSingleMode(): cue2pops (if needed) -> ELF -> rename VCD -> FTP upload.
    private void uploadSingleMode(MyFTPClient myFTP, String newGameFileName, boolean includeElf) {
        String filePath = gamePathField.getText();
        String originalGameFileName = gameFile.getName().substring(0, gameFile.getName().length() - 4);
        String fileExtension = filePath.substring(filePath.lastIndexOf("."));
        String dir = filePath.substring(1, filePath.lastIndexOf(File.separator) + 1);

        if (fileExtension.equals(".cue")) {
            File binFile = new File(dir + originalGameFileName + ".bin");
            if (!(binFile.exists() && binFile.isFile())) {
                Platform.runLater(() -> { error("Unable to locate the .bin file that is associated with this .cue file!", " File Not Found"); setFormDisabled(false); uploadInProgress = false; });
                return;
            }
            boolean vcdGenerated = false;
            try { vcdGenerated = AddGameManager.launchCueToPops(gameFile); } catch (IOException | InterruptedException ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
            if (!vcdGenerated) { Platform.runLater(() -> { setFormDisabled(false); uploadInProgress = false; }); return; }
        } else if (!(fileExtension.equals(".vcd") || fileExtension.equals(".VCD"))) {
            Platform.runLater(() -> { setFormDisabled(false); uploadInProgress = false; });
            return;
        }

        try {
            String gameID = AddGameManager.getPS1GameIDFromVCD(new File(dir + originalGameFileName + ".VCD"));
            if (gameID == null) { Platform.runLater(() -> { setFormDisabled(false); uploadInProgress = false; }); return; }

            AddGameManager.generateElf(newGameFileName + "-" + gameID + ".ELF", dir);
            File elf = new File(dir + newGameFileName + "-" + gameID + ".ELF");
            if (elf.exists() && elf.isFile()) {
                String originalVCDName = gameFile.getAbsolutePath().substring(0, gameFile.getAbsolutePath().lastIndexOf(".") + 1) + "VCD";
                String newVCDName = gameFile.getAbsolutePath().substring(0, gameFile.getAbsolutePath().lastIndexOf(File.separator) + 1) + newGameFileName + "-" + gameID + ".VCD";
                new File(originalVCDName).renameTo(new File(newVCDName));

                List<File> vcdFileList = new ArrayList<>();
                vcdFileList.add(new File(newVCDName));
                myFTP.addGameToPS2(this, vcdFileList, includeElf);
            } else {
                new File(gameFile.getAbsolutePath().substring(0, gameFile.getAbsolutePath().lastIndexOf(".") + 1) + "VCD").delete();
                Platform.runLater(() -> { setFormDisabled(false); uploadInProgress = false; });
            }
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
            Platform.runLater(() -> { setFormDisabled(false); uploadInProgress = false; });
        }
    }

    // Faithful port of the Swing uploadBatchMode().
    private void uploadBatchMode(MyFTPClient myFTP, boolean includeElf) {
        for (File cueFile : cueFilesInDirectory) {
            String originalGameFileName = cueFile.getName().substring(0, cueFile.getName().length() - 4);
            File binFile = new File(cueFile.getAbsolutePath().substring(0, cueFile.getAbsolutePath().lastIndexOf(File.separator) + 1) + originalGameFileName + ".bin");
            if (binFile.exists() && binFile.isFile()) {
                boolean vcdGenerated = false;
                try { vcdGenerated = AddGameManager.launchCueToPops(cueFile); } catch (IOException | InterruptedException ex) { PopsGameManager.displayErrorMessageDebug("Error launching cue2pops!\n\n" + ex.toString()); }
                if (vcdGenerated) {
                    File newVCDFile = new File(cueFile.getAbsolutePath().substring(0, cueFile.getAbsolutePath().lastIndexOf(File.separator) + 1) + originalGameFileName + ".VCD");
                    if (newVCDFile.exists() && newVCDFile.isFile()) { vcdFilesInDirectory.add(newVCDFile); }
                }
            } else {
                Platform.runLater(() -> error("Unable to locate the .bin file that is associated with this .cue file!", " File Not Found"));
            }
        }

        for (File vcdFile : vcdFilesInDirectory) {
            String originalGameFileName = vcdFile.getName().substring(0, vcdFile.getName().length() - 4);
            try {
                String gameID = AddGameManager.getPS1GameIDFromVCD(new File(vcdFile.getAbsolutePath().substring(0, vcdFile.getAbsolutePath().lastIndexOf(File.separator) + 1) + originalGameFileName + ".VCD"));
                if (gameID != null) {
                    AddGameManager.generateElf(originalGameFileName + "-" + gameID + ".ELF", vcdFile.getAbsolutePath().substring(0, vcdFile.getAbsolutePath().lastIndexOf(File.separator) + 1));
                    File elf = new File(vcdFile.getAbsolutePath().substring(0, vcdFile.getAbsolutePath().lastIndexOf(File.separator) + 1) + originalGameFileName + "-" + gameID + ".ELF");
                    if (elf.exists() && elf.isFile()) {
                        String noExt = vcdFile.getName().substring(0, vcdFile.getName().lastIndexOf("."));
                        if (!noExt.contains(gameID)) {
                            String newVCDName = vcdFile.getAbsolutePath().substring(0, vcdFile.getAbsolutePath().lastIndexOf(File.separator) + 1) + noExt + "-" + gameID + ".VCD";
                            vcdFile.renameTo(new File(newVCDName));
                        }
                    }
                }
            } catch (Exception ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
        }

        // Re-scan the directory now that some files may have been renamed
        vcdFilesInDirectory.clear();
        File[] files = new File(selectedPath).listFiles();
        if (files != null) {
            for (File f : files) { if (f.isFile() && f.getName().length() > 4 && f.getName().substring(f.getName().length() - 3).equalsIgnoreCase("VCD")) { vcdFilesInDirectory.add(f); } }
        }
        myFTP.addGameToPS2(this, new ArrayList<>(vcdFilesInDirectory), includeElf);
    }

    private void setFormDisabled(boolean disabled) {
        gameNameField.setDisable(disabled);
        ipField.setDisable(disabled);
        includeElfCheck.setDisable(disabled);
        uploadButton.setDisable(disabled);
        vcdDriveCombo.setDisable(disabled);
        vcdFolderCombo.setDisable(disabled);
        elfFolderCombo.setDisable(disabled);
        elfSubfolderField.setDisable(disabled);
    }

    // --- FtpTransferProgress (called from MyFTPClient's upload thread) --------

    @Override public void setProgressRange(int max) { this.progressMax = max <= 0 ? 100 : max; }
    @Override public void setProgress(int value) { Platform.runLater(() -> progressBar.setProgress(value / (double) progressMax)); }
    @Override public void setTimeRemaining(String text) { Platform.runLater(() -> timeRemainingLabel.setText(text)); }
    @Override public void setUploadSpeed(String text) { Platform.runLater(() -> uploadSpeedLabel.setText(text)); }
    @Override public void setGameName(String text) { Platform.runLater(() -> gameNameField.setText(text)); }
    @Override public void setGameCounter(String text) { Platform.runLater(() -> gameCounterField.setText(text)); }
    @Override public void setInProgress(boolean uploading) { this.uploadInProgress = uploading; }
    @Override public void closeWindow() { Platform.runLater(() -> { if (stage != null) stage.close(); }); }

    private void warn(String message, String title) { alert(Alert.AlertType.WARNING, message, title); }
    private void error(String message, String title) { alert(Alert.AlertType.ERROR, message, title); }

    private void alert(Alert.AlertType type, String message, String title) {
        Alert alert = new Alert(type, message);
        alert.setHeaderText(null);
        alert.setTitle(title);
        if (stage != null) { alert.initOwner(stage); }
        alert.showAndWait();
    }
}

package ps2gm.game.manager.fx;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ps2gm.game.manager.BackgroundTasks;
import ps2gm.game.manager.Console;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.Mode;
import ps2gm.game.manager.MyFTPClient;
import ps2gm.game.manager.PopsGameManager;
import ps2gm.game.manager.XMLFileManager;

/**
 * Controller for {@code SyncFileScreen.fxml} - a two-pane local/remote file browser
 * for copying ART / CFG / CHT / VMC files (and PS1 cheat folders) between the local
 * {@code hdd/} directories and the console over FTP. JavaFX replacement for the
 * Swing {@code SyncFileScreen}. Only directory listings + per-file get/put/delete
 * are used (no progress-bar upload path), so no backend decouple - the FTP calls
 * just move onto a daemon thread so the window doesn't freeze.
 */
public class SyncFileController implements FxScreens.StageAware {

    private static final String[] REGION_CODES = {"SCES_","SLES_","SCUS_","SLUS_","SLPS_","SCAJ_","SLKA_","SLPM_","SCPS_"};

    private static String hdd(String sub) {
        return PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator + sub + File.separator;
    }
    private final String LOCAL_DIRECTORY_ART = hdd("ART");
    private final String LOCAL_DIRECTORY_CONFIG = hdd("CFG");
    private final String LOCAL_DIRECTORY_CHEAT_PS2 = hdd("CHT");
    private final String LOCAL_DIRECTORY_VMC_PS2 = hdd("VMC");
    private final String LOCAL_DIRECTORY_PS1 = hdd("POPS");

    @FXML private ComboBox<String> partitionCombo;
    @FXML private TextField ipField;
    @FXML private Button connectButton, fromConsoleButton, toConsoleButton, deleteButton;
    @FXML private RadioButton artRadio, cfgRadio, chtRadio, vmcRadio;
    @FXML private ListView<String> localList, remoteList;
    @FXML private ProgressBar progressBar;

    private Stage stage;
    private MyFTPClient myFTP;
    private String currentLocalDirectory;
    private final String selectedPartition = "+OPL";
    private String remoteArt, remoteConfig, remoteCheat, remoteVmc;

    @FXML
    private void initialize() {
        partitionCombo.setItems(FXCollections.observableArrayList("hdd0:/+OPL/", "mass:/+OPL/", "mass1:/+OPL/", "mass2:/+OPL/"));
        currentLocalDirectory = LOCAL_DIRECTORY_ART;

        // Selecting in one list clears the other (mirrors the Swing mouse listeners).
        localList.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> { if (nv != null) remoteList.getSelectionModel().clearSelection(); });
        remoteList.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> { if (nv != null) localList.getSelectionModel().clearSelection(); });

        partitionCombo.valueProperty().addListener((o, ov, nv) -> {
            if (nv == null) { return; }
            PopsGameManager.setRemoteOPLPath(nv);
            try { XMLFileManager.writeSettingsXML(); }
            catch (Exception ex) { PopsGameManager.displayErrorMessageDebug("Error saving the settings!\n\n" + ex.toString()); }
        });
    }

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(" Transfer Files");
        if (PopsGameManager.getPS2IP() != null) { ipField.setText(PopsGameManager.getPS2IP()); }
        partitionCombo.setValue(PopsGameManager.getRemoteOPLPath());
        initialiseFilePaths();
        stage.setOnHidden(e -> {
            if (myFTP != null) { myFTP.disconnectFromConsole(); }
            PopsGameManager.callbackToUpdateGUIGameList(null, -1);
        });
    }

    void init() { /* nothing extra - stageReady does the setup */ }

    private void initialiseFilePaths() {
        String remotePath = null;
        if ("hdd".equals(GameListManager.getFormattedOPLDrive())) { remotePath = "/pfs/" + GameListManager.getFormattedOPLPartition() + "/"; }
        else if ("mass".equals(GameListManager.getFormattedOPLDrive())) { remotePath = "/mass/" + GameListManager.getFormattedOPLPartition() + "/"; }
        remoteArt = remotePath + "ART/";
        remoteConfig = remotePath + "CFG/";
        remoteCheat = remotePath + "CHT/";
        remoteVmc = remotePath + "VMC/";
    }

    // --- connect --------------------------------------------------------

    @FXML
    private void onConnect() {
        if (ipField.getText() == null || ipField.getText().trim().isEmpty()) { return; }
        if (!confirm("Ensure that the FTP server is currently running on your console, then click \"OK\".", " File Sync Requires FTP Connection")) { return; }
        String ip = ipField.getText();
        runFtp(() -> {
            myFTP = new MyFTPClient();
            if (myFTP.connectToConsole(ip)) {
                Platform.runLater(() -> artRadio.setSelected(true));
                composeLocalFileList(LOCAL_DIRECTORY_ART);
                composeRemoteFileList(LOCAL_DIRECTORY_ART);
            }
        });
    }

    // --- file type radios --------------------------------------------

    @FXML private void onArt() { fileFilterChanged("Art"); }
    @FXML private void onCfg() { fileFilterChanged("Config"); }
    @FXML private void onCht() { fileFilterChanged("Cheat"); }
    @FXML private void onVmc() {
        if (PopsGameManager.getCurrentConsole() != Console.PS1) { fileFilterChanged("VMC"); }
        else {
            vmcRadio.setSelected(false);
            if (myFTP != null && myFTP.isFTPConnected()) {
                alert(Alert.AlertType.ERROR, "This application cannot yet upload PS1 Virtual Memory Card files.", " Unable to Upload PS1 VMC File!");
            }
        }
    }

    private void fileFilterChanged(String fileType) {
        if (myFTP == null || !myFTP.isFTPConnected()) { return; }
        runFtp(() -> {
            switch (fileType) {
                case "Art":
                    composeLocalFileList(LOCAL_DIRECTORY_ART);
                    composeRemoteFileList(LOCAL_DIRECTORY_ART);
                    break;
                case "Config":
                    composeLocalFileList(LOCAL_DIRECTORY_CONFIG);
                    composeRemoteFileList(LOCAL_DIRECTORY_CONFIG);
                    break;
                case "Cheat":
                    if (PopsGameManager.getCurrentConsole() == Console.PS1) {
                        composeLocalCheatListPS1();
                        composeRemoteCheatListPS1();
                    } else {
                        composeLocalFileList(LOCAL_DIRECTORY_CHEAT_PS2);
                        composeRemoteFileList(LOCAL_DIRECTORY_CHEAT_PS2);
                    }
                    break;
                case "VMC":
                    composeLocalFileList(LOCAL_DIRECTORY_VMC_PS2);
                    composeRemoteFileList(LOCAL_DIRECTORY_VMC_PS2);
                    break;
                default:
                    break;
            }
        });
    }

    // --- list composition -------------------------------------------

    private void composeLocalFileList(String directory) {
        currentLocalDirectory = directory;
        List<String> out = new ArrayList<>();

        myFTP.disconnectFromConsole();
        myFTP.connectToConsole(PopsGameManager.getPS2IP());
        myFTP.changeDirectory("hdd/0/" + selectedPartition);

        File[] listOfFiles = new File(directory).listFiles();
        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (!file.isFile()) { continue; }
                if (directory.equals(LOCAL_DIRECTORY_ART)) {
                    if (PopsGameManager.getCurrentConsole() == Console.PS1) {
                        if (file.getName().length() >= 3) {
                            if (PopsGameManager.getCurrentMode() == Mode.SMB || PopsGameManager.getCurrentMode() == Mode.HDD_USB) {
                                if (file.getName().substring(0, 3).equals(PopsGameManager.getFilePrefix())) { out.add(file.getName()); }
                            } else {
                                out.add(file.getName());
                            }
                        }
                    } else {
                        if (file.getName().length() >= 3 && !file.getName().substring(0, 3).equals(PopsGameManager.getFilePrefix())) { out.add(file.getName()); }
                    }
                } else {
                    out.add(file.getName());
                }
            }
        }
        setList(localList, out);
    }

    private void composeRemoteFileList(String localDirectory) {
        List<String> out = new ArrayList<>();

        if (localDirectory.equals(LOCAL_DIRECTORY_ART)) {
            if (myFTP.isFTPConnected()) {
                List<String> temp = myFTP.listRemoteDirectory(remoteArt, selectedPartition, true);
                boolean wantRegion = PopsGameManager.getCurrentConsole() == Console.PS2;
                if (temp != null) {
                    for (String name : temp) {
                        boolean hasRegion = false;
                        for (String rc : REGION_CODES) { if (name.length() >= 5 && name.substring(0, 5).equals(rc)) { hasRegion = true; } }
                        if (hasRegion == wantRegion) { out.add(name); }
                    }
                }
            }
        } else if (localDirectory.equals(LOCAL_DIRECTORY_CONFIG)) {
            if (myFTP.isFTPConnected()) { out = orEmpty(myFTP.listRemoteDirectory(remoteConfig, selectedPartition, true)); }
        } else if (localDirectory.equals(LOCAL_DIRECTORY_CHEAT_PS2)) {
            if (myFTP.isFTPConnected()) { out = orEmpty(myFTP.listRemoteDirectory(remoteCheat, selectedPartition, true)); }
        } else if (localDirectory.equals(LOCAL_DIRECTORY_VMC_PS2)) {
            if (myFTP.isFTPConnected()) { out = orEmpty(myFTP.listRemoteDirectory(remoteVmc, selectedPartition, true)); }
        }
        setList(remoteList, out);
    }

    private void composeLocalCheatListPS1() {
        currentLocalDirectory = LOCAL_DIRECTORY_PS1;
        List<String> out = new ArrayList<>();
        File popsDir = new File(PopsGameManager.getOPLFolder() + "POPS");
        if (popsDir.isDirectory()) {
            File[] directories = popsDir.listFiles(File::isDirectory);
            if (directories != null) {
                for (File d : directories) {
                    if (new File(d + File.separator + "CHEATS.TXT").isFile()) { out.add(d.getName()); }
                }
            }
        }
        setList(localList, out);
    }

    private void composeRemoteCheatListPS1() {
        currentLocalDirectory = LOCAL_DIRECTORY_PS1;
        myFTP.disconnectFromConsole();
        myFTP.connectToConsole(PopsGameManager.getPS2IP());

        String remoteCheatFolder = remoteCheatFolder();
        myFTP.changeDirectory(GameListManager.getFormattedVCDDrive() + "/" + GameListManager.getFormattedVCDPartition() + "/__common");
        List<String> dirs = myFTP.listRemoteDirectory(remoteCheatFolder, "__common", false);

        List<String> out = new ArrayList<>();
        if (dirs != null) {
            for (String d : dirs) {
                List<String> inner = myFTP.listRemoteDirectory(remoteCheatFolder + d + "/", "__common", true);
                if (inner != null && inner.contains("CHEATS.TXT")) { out.add(d); }
            }
        }
        setList(remoteList, out);
    }

    private String remoteCheatFolder() {
        if ("hdd".equals(GameListManager.getFormattedVCDDrive())) { return "/pfs/" + GameListManager.getFormattedVCDPartition() + "/POPS/"; }
        if ("mass".equals(GameListManager.getFormattedVCDDrive())) { return "/mass/" + GameListManager.getFormattedVCDPartition() + "/POPS/"; }
        return "";
    }

    // --- transfer / delete ----------------------------------------

    @FXML
    private void onDelete() {
        String local = localList.getSelectionModel().getSelectedItem();
        String remote = remoteList.getSelectionModel().getSelectedItem();
        if (remote != null && local == null) {
            runFtp(() -> deleteRemote(remote));
        } else if (local != null && remote == null) {
            runFtp(() -> deleteLocal(local));
        }
    }

    private void deleteRemote(String remote) {
        if (currentLocalDirectory.equals(LOCAL_DIRECTORY_ART) && myFTP.isFTPConnected()) { myFTP.deleteRemoteFile(remoteArt + remote); }
        else if (currentLocalDirectory.equals(LOCAL_DIRECTORY_CONFIG) && myFTP.isFTPConnected()) { myFTP.deleteRemoteFile(remoteConfig + remote); }
        else if (currentLocalDirectory.equals(LOCAL_DIRECTORY_CHEAT_PS2) && myFTP.isFTPConnected()) { myFTP.deleteRemoteFile(remoteCheat + remote); }
        else if (currentLocalDirectory.equals(LOCAL_DIRECTORY_VMC_PS2) && myFTP.isFTPConnected()) { myFTP.deleteRemoteFile(remoteVmc + remote); }

        if (currentLocalDirectory.equals(LOCAL_DIRECTORY_PS1)) {
            if (myFTP.isFTPConnected()) {
                myFTP.deleteRemoteFile(remoteCheatFolder() + remote + "/CHEATS.TXT");
                composeRemoteCheatListPS1();
            }
        } else {
            composeRemoteFileList(currentLocalDirectory);
        }
    }

    private void deleteLocal(String local) {
        if (!currentLocalDirectory.equals(LOCAL_DIRECTORY_PS1)) {
            try { new File(currentLocalDirectory + local).delete(); } catch (Exception ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
            composeLocalFileList(currentLocalDirectory);
        } else {
            try { new File(currentLocalDirectory + local + File.separator + "CHEATS.TXT").delete(); } catch (Exception ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
            composeLocalCheatListPS1();
        }
    }

    @FXML
    private void onToConsole() {
        String local = localList.getSelectionModel().getSelectedItem();
        if (local == null || remoteList.getSelectionModel().getSelectedItem() != null) { return; }
        runFtp(() -> copyFileToConsole(local));
    }

    private void copyFileToConsole(String local) {
        boolean ps1 = PopsGameManager.getCurrentConsole() == Console.PS1;
        if (currentLocalDirectory.equals(LOCAL_DIRECTORY_ART) && myFTP.isFTPConnected()) { myFTP.addFileToPS2(LOCAL_DIRECTORY_ART, local, remoteArt, ps1); }
        else if (currentLocalDirectory.equals(LOCAL_DIRECTORY_CONFIG) && myFTP.isFTPConnected()) { myFTP.addFileToPS2(LOCAL_DIRECTORY_CONFIG, local, remoteConfig, ps1); }
        else if (currentLocalDirectory.equals(LOCAL_DIRECTORY_CHEAT_PS2) && myFTP.isFTPConnected()) { myFTP.addFileToPS2(LOCAL_DIRECTORY_CHEAT_PS2, local, remoteCheat, ps1); }
        else if (currentLocalDirectory.equals(LOCAL_DIRECTORY_VMC_PS2) && myFTP.isFTPConnected()) { myFTP.addFileToPS2(LOCAL_DIRECTORY_VMC_PS2, local, remoteVmc, ps1); }

        String remoteCheatFolder = remoteCheatFolder();
        if (currentLocalDirectory.equals(LOCAL_DIRECTORY_PS1)) {
            if (myFTP.remoteFileExists(GameListManager.getFormattedOPLDrive(), remoteCheatFolder + local + "/", "__common", "CHEATS.TXT", true)) {
                myFTP.deleteRemoteFile(remoteCheatFolder + local + "/CHEATS.TXT");
                myFTP.addFileToPS2(LOCAL_DIRECTORY_PS1 + local + File.separator, "CHEATS.TXT", remoteCheatFolder + local + "/", false);
            } else {
                if (!myFTP.remoteFileExists(GameListManager.getFormattedOPLDrive(), remoteCheatFolder, "__common", local, false)) { myFTP.createDirectory(remoteCheatFolder + local); }
                myFTP.addFileToPS2(LOCAL_DIRECTORY_PS1 + local + File.separator, "CHEATS.TXT", remoteCheatFolder + local + "/", false);
            }
            composeRemoteCheatListPS1();
        } else {
            composeRemoteFileList(currentLocalDirectory);
        }
    }

    @FXML
    private void onFromConsole() {
        String remote = remoteList.getSelectionModel().getSelectedItem();
        if (remote == null || localList.getSelectionModel().getSelectedItem() != null) { return; }
        runFtp(() -> copyFileFromConsole(remote));
    }

    private void copyFileFromConsole(String remote) {
        if (currentLocalDirectory.equals(LOCAL_DIRECTORY_ART)) {
            if (!listOfFilesInDirectory(LOCAL_DIRECTORY_ART).contains(PopsGameManager.getFilePrefix() + remote)) {
                myFTP.getFile(remoteArt, remote, currentLocalDirectory);
                File got = new File(currentLocalDirectory + remote);
                if (got.exists()) { got.renameTo(new File(currentLocalDirectory + PopsGameManager.getFilePrefix() + remote)); }
            } else {
                alert(Alert.AlertType.ERROR, "This file is already in your local ART directory!", " File Copy Error!");
            }
        } else if (currentLocalDirectory.equals(LOCAL_DIRECTORY_CONFIG)) {
            if (!listOfFilesInDirectory(LOCAL_DIRECTORY_CONFIG).contains(PopsGameManager.getFilePrefix() + remote)) {
                myFTP.getFile(remoteConfig, remote, currentLocalDirectory);
                File got = new File(currentLocalDirectory + remote);
                if (got.exists()) { got.renameTo(new File(currentLocalDirectory + PopsGameManager.getFilePrefix() + remote)); }
            } else {
                alert(Alert.AlertType.ERROR, "This file is already in your local CFG directory!", " File Copy Error!");
            }
        } else if (currentLocalDirectory.equals(LOCAL_DIRECTORY_CHEAT_PS2)) {
            if (!listOfFilesInDirectory(LOCAL_DIRECTORY_CHEAT_PS2).contains(remote)) { myFTP.getFile(remoteCheat, remote, currentLocalDirectory); }
            else { alert(Alert.AlertType.ERROR, "This file is already in your local CHT directory!", " File Copy Error!"); }
        } else if (currentLocalDirectory.equals(LOCAL_DIRECTORY_VMC_PS2)) {
            if (!listOfFilesInDirectory(LOCAL_DIRECTORY_VMC_PS2).contains(remote)) { myFTP.getFile(remoteVmc, remote, currentLocalDirectory); }
            else { alert(Alert.AlertType.ERROR, "This file is already in your local VMC directory!", " File Copy Error!"); }
        }

        if (currentLocalDirectory.equals(LOCAL_DIRECTORY_PS1)) {
            File localCheatFolder = new File(LOCAL_DIRECTORY_PS1 + File.separator + remote);
            if (!localCheatFolder.isDirectory()) { localCheatFolder.mkdir(); }
            myFTP.getFile("/pfs/0/POPS/" + remote + "/", "CHEATS.TXT", localCheatFolder.getAbsolutePath() + File.separator);
            composeLocalCheatListPS1();
        } else {
            composeLocalFileList(currentLocalDirectory);
        }
    }

    private List<String> listOfFilesInDirectory(String directoryPath) {
        List<String> out = new ArrayList<>();
        File[] files = new File(directoryPath).listFiles();
        if (files != null) { for (File f : files) { if (f.isFile()) { out.add(f.getName()); } } }
        return out;
    }

    // --- helpers --------------------------------------------------

    private void runFtp(Runnable r) {
        BackgroundTasks.runDaemon("fx-sync-file", () -> {
            try { r.run(); }
            catch (Exception ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
        });
    }

    private static List<String> orEmpty(List<String> list) { return list != null ? list : new ArrayList<>(); }

    private void setList(ListView<String> view, List<String> items) {
        Platform.runLater(() -> view.getItems().setAll(items));
    }

    private boolean confirm(String message, String title) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        alert.setTitle(title);
        if (stage != null) { alert.initOwner(stage); }
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void alert(Alert.AlertType type, String message, String title) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message);
            alert.setHeaderText(null);
            alert.setTitle(title);
            if (stage != null) { alert.initOwner(stage); }
            alert.showAndWait();
        });
    }
}

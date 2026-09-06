package ps2gm.game.manager.fx;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.HDLDumpManager;
import ps2gm.game.manager.PopsGameManager;
import ps2gm.game.manager.XMLFileManager;

/**
 * Controller for {@code SetModeScreen.fxml} - the OPL-location / mode picker. This
 * is the first-launch critical path: {@link ps2gm.game.manager.MainScreen} opens it
 * modally and the app cannot proceed until a mode + OPL directory are chosen (or
 * the user confirms exit). JavaFX replacement for the Swing {@code SetModeScreen}.
 *
 * Modes: {@code SMB} (OPL folder on this PC), {@code HDD_USB} (on a USB drive),
 * {@code HDD} (on the console's internal HDD, reached over hdl_dump + FTP). The
 * old "USB - HDD" panel was a dead stub and is dropped.
 */
public class SetModeController implements FxScreens.StageAware {

    @FXML private RadioButton smbRadio, hddRadio, usbRadio;
    @FXML private TextField smbPathField, usbPathField, ipField;
    @FXML private Label smbPathLabel, ipLabel, usbPathLabel;
    @FXML private Button smbBrowseButton, usbBrowseButton, connectButton, saveButton, cancelButton;

    private Stage stage;
    private String currentlySelectedMode;
    private String oplFolder;
    private String selectedConsole = "PS2";
    private List<Game> gameListPS1 = new ArrayList<>();
    private List<Game> gameListPS2 = new ArrayList<>();

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(" Select Mode");
        stage.setOnCloseRequest(e -> {
            e.consume();
            exitOrClose();
        });
    }

    /** Called from the façade before the window is shown. */
    void init() {
        if (PopsGameManager.getFisrtLaunch()) {
            currentlySelectedMode = "SMB";
            PopsGameManager.setCurrentConsole("PS2");
            PopsGameManager.setFisrtLaunch(false);
        } else if (currentlySelectedMode == null) {
            currentlySelectedMode = PopsGameManager.getCurrentMode();
        }

        smbRadio.setOnAction(e -> { currentlySelectedMode = "SMB"; applyMode(); });
        usbRadio.setOnAction(e -> { currentlySelectedMode = "HDD_USB"; applyMode(); });
        hddRadio.setOnAction(e -> { currentlySelectedMode = "HDD"; applyMode(); });

        applyMode();
    }

    // Radio-driven section enable/disable + path prefills (mirrors the Swing initialiseGUI switch).
    private void applyMode() {
        if (currentlySelectedMode == null) { currentlySelectedMode = "SMB"; }
        boolean smb = "SMB".equals(currentlySelectedMode);
        boolean usb = "HDD_USB".equals(currentlySelectedMode);
        boolean hdd = "HDD".equals(currentlySelectedMode);

        smbRadio.setSelected(smb);
        usbRadio.setSelected(usb);
        hddRadio.setSelected(hdd);

        smbBrowseButton.setDisable(!smb);
        smbPathField.setEditable(smb);
        smbPathField.setDisable(!smb);
        smbPathLabel.setDisable(!smb);

        connectButton.setDisable(!hdd);
        ipField.setEditable(hdd);
        ipField.setDisable(!hdd);
        ipLabel.setDisable(!hdd);

        usbBrowseButton.setDisable(!usb);
        usbPathField.setDisable(!usb);
        usbPathLabel.setDisable(!usb);

        boolean oplSet = PopsGameManager.isOPLFolderSet()
                && PopsGameManager.getCurrentMode() != null
                && PopsGameManager.getCurrentMode().equals(currentlySelectedMode);

        if (smb) {
            smbPathField.setText(oplSet ? PopsGameManager.getOPLFolder() : "");
        } else if (usb) {
            usbPathField.setText(oplSet ? PopsGameManager.getOPLFolder() : "");
        } else if (hdd && PopsGameManager.getPS2IP() != null) {
            ipField.setText(PopsGameManager.getPS2IP());
        }
    }

    @FXML
    private void onBrowseSmb() {
        File dir = chooseDirectory();
        if (dir != null) {
            oplFolder = dir.toString();
            smbPathField.setText(oplFolder);
        }
    }

    @FXML
    private void onBrowseUsb() {
        File dir = chooseDirectory();
        if (dir != null) {
            oplFolder = dir.getAbsolutePath();
            usbPathField.setText(oplFolder);
            saveButton.setDisable(false);
        }
    }

    private File chooseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Set OPL Directory");
        if (PopsGameManager.isOPLFolderSet()) {
            File current = new File(PopsGameManager.getOPLFolder());
            if (current.isDirectory()) { chooser.setInitialDirectory(current); }
        }
        return chooser.showDialog(stage);
    }

    @FXML
    private void onConnect() {
        if (ipField.getText() == null || ipField.getText().isEmpty()) {
            alert(Alert.AlertType.WARNING, "You need to enter the IP address of your PS2 console into the text field.", " No IP address entered!");
            return;
        }
        connectToPS2();
    }

    // Faithful port of the Swing connectToPS2(): ask which lists to fetch, then fetch them
    // (hdl_dump for PS2, FTP for PS1) off the FX thread so the window doesn't freeze.
    private void connectToPS2() {
        saveButton.setDisable(true);
        PopsGameManager.setPS2IP(ipField.getText());
        String ip = ipField.getText();

        boolean wantPs2 = confirm("Do you want to get the list of PS2 games on your console?\n\nHDL Server must be running on your console in order to perform this task!", " Connect to PlayStation 2");
        boolean wantPs1 = confirm("Do you want to get the list of PS1 games on your console?\n\nThe FTP Server must be running on your console in order to perform this task!", " Connect to PlayStation 2");

        new Thread(() -> {
            if (wantPs2) {
                selectedConsole = "PS2";
                try {
                    gameListPS2 = new HDLDumpManager().hdlDumpGetTOC(ip);
                    if (gameListPS2 != null) { GameListManager.writeGameListFilePS2(gameListPS2); }
                } catch (IOException | InterruptedException ex) {
                    Platform.runLater(() -> alert(Alert.AlertType.ERROR,
                            "There was a problem launching HDL_Dump!\nEnsure that you have admin privileges in order to run HDL_Dump.", " HDL_Dump Error!"));
                    PopsGameManager.displayErrorMessageDebug("Error launching hdl_dump!\n\n" + ex.toString());
                }
            }
            if (wantPs1) {
                selectedConsole = "PS1";
                gameListPS1 = GameListManager.getGameListFromConsolePS1();
                if (gameListPS1 != null) { GameListManager.writeGameListFilePS1(gameListPS1); }
            }

            // Ensure the local hdd folder exists and use it as the OPL directory
            File hdlLocalDirectory = new File(PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator);
            if (!(hdlLocalDirectory.exists() && hdlLocalDirectory.isDirectory())) { hdlLocalDirectory.mkdir(); }
            oplFolder = PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator;

            Platform.runLater(() -> saveButton.setDisable(false));
        }, "fx-setmode-connect").start();
    }

    @FXML
    private void onSave() {
        if (oplFolder == null || currentlySelectedMode == null) { return; }

        PopsGameManager.setOPLFolder(oplFolder);
        PopsGameManager.setCurrentMode(currentlySelectedMode);

        for (String sub : new String[] {"ART", "CFG", "CHT", "VMC", "POPS", "THM"}) {
            File f = new File(oplFolder + File.separator + sub);
            if (!f.exists()) { f.mkdir(); }
        }
        if (!"HDD_USB".equals(currentlySelectedMode)) {
            for (String sub : new String[] {"CD", "DVD"}) {
                File f = new File(oplFolder + File.separator + sub);
                if (!f.exists()) { f.mkdir(); }
            }
        }

        if ("HDD".equals(currentlySelectedMode)) { PopsGameManager.setPS2IP(ipField.getText()); }

        switch (currentlySelectedMode) {
            case "HDD":
                try { GameListManager.createGameListFromFile("PS1", new File(PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator + "gameListPS1")); } catch (IOException ex) { PopsGameManager.displayErrorMessageDebug("Error creating the PS1 game list from file!\n\n" + ex.toString()); }
                try { GameListManager.createGameListFromFile("PS2", new File(PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator + "gameListPS2")); } catch (IOException ex) { PopsGameManager.displayErrorMessageDebug("Error creating the PS2 game list from file!\n\n" + ex.toString()); }
                break;
            case "HDD_USB":
            case "SMB":
                try {
                    GameListManager.createGameListsPS1();
                    GameListManager.createGameListsPS2(true);
                } catch (NullPointerException ex) {
                    PopsGameManager.displayErrorMessageDebug(ex.toString());
                }
                break;
            default:
                break;
        }

        if ("HDD".equals(currentlySelectedMode)) {
            PopsGameManager.setCurrentConsole(selectedConsole);
            if ("PS1".equals(selectedConsole) && gameListPS1 != null) { GameListManager.setGameListPS1(gameListPS1); }
            if ("PS2".equals(selectedConsole) && gameListPS2 != null) { GameListManager.setGameListPS2(gameListPS2); }
        }

        try {
            XMLFileManager.writeSettingsXML();
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug("Error saving the settings!\n\n" + ex.toString());
        }

        PopsGameManager.callbackToUpdateGUIGameList(null, 0);
        if (stage != null) { stage.close(); }
    }

    @FXML
    private void onCancel() {
        exitOrClose();
    }

    // Close if the OPL directory is set; otherwise offer to exit the whole app (the app can't
    // run without one). Matches the Swing screen's Cancel / window-close behaviour.
    private void exitOrClose() {
        if (!PopsGameManager.isOPLFolderSet()) {
            if (confirm("You have not set the OPL directory. \n\nDo you want to exit the application?", " OPL Directory Not Set!")) {
                Runtime.getRuntime().exit(0);
            }
        } else if (stage != null) {
            stage.close();
        }
    }

    private boolean confirm(String message, String title) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle(title);
        if (stage != null) { alert.initOwner(stage); }
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void alert(Alert.AlertType type, String message, String title) {
        Alert alert = new Alert(type, message);
        alert.setHeaderText(null);
        alert.setTitle(title);
        if (stage != null) { alert.initOwner(stage); }
        alert.showAndWait();
    }
}

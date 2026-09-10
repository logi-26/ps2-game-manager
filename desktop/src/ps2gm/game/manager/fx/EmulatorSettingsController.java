package ps2gm.game.manager.fx;

import java.io.File;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ps2gm.game.manager.Console;
import ps2gm.game.manager.PopsGameManager;
import ps2gm.game.manager.XMLFileManager;

/**
 * Controller for {@code EmulatorSettingsScreen.fxml} - the per-console (PS1 = PCSXR,
 * PS2 = PCSX2) emulator settings. Loads the stored "use emulator / path / fullscreen"
 * from {@link PopsGameManager} and, on Save, writes them back and persists settings.xml.
 * The PS1 form hides the Fullscreen option (unchanged from the Swing version).
 *
 * Behaviour note: the old Swing dialog saved a shadow {@code emulatorPath} variable and
 * ignored text typed directly into the field. Here the path field is the source of
 * truth, so a manually entered path is now honoured.
 */
public class EmulatorSettingsController implements FxScreens.StageAware {

    @FXML private CheckBox useEmulatorCheck;
    @FXML private CheckBox fullscreenCheck;
    @FXML private TextField pathField;
    @FXML private Button browseButton;

    private Stage stage;
    private Console console;
    private String emulatorName; // "PCSXR" | "PCSX2"

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(" " + emulatorName + " Settings");
    }

    /** Called from the façade before the window is shown. */
    void setConsole(Console console) {
        this.console = console;
        this.emulatorName = console == Console.PS1 ? "PCSXR" : "PCSX2";

        useEmulatorCheck.setText("Use " + emulatorName);
        fullscreenCheck.setVisible(console != Console.PS1);

        boolean inUse = console == Console.PS1
                ? PopsGameManager.getEmulatorInUsePS1()
                : PopsGameManager.getEmulatorInUsePS2();
        useEmulatorCheck.setSelected(inUse);

        if (inUse) {
            String path = console == Console.PS1
                    ? PopsGameManager.getEmulatorPathPS1()
                    : PopsGameManager.getEmulatorPathPS2();
            if (path != null) {
                pathField.setText(path);
            }
            boolean full = console == Console.PS1
                    ? PopsGameManager.getEmulatorFullScreenPS1()
                    : PopsGameManager.getEmulatorFullScreenPS2();
            fullscreenCheck.setSelected(full);
        }
        updateEnabledState();
    }

    @FXML
    private void onUseEmulatorToggled() {
        updateEnabledState();
    }

    private void updateEnabledState() {
        boolean on = useEmulatorCheck.isSelected();
        fullscreenCheck.setDisable(!on);
        pathField.setDisable(!on);
        browseButton.setDisable(!on);
    }

    @FXML
    private void onBrowse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Set " + emulatorName + " Path");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(emulatorName + " Executable", "*.exe"));
        File start = PopsGameManager.isOPLFolderSet() ? new File(PopsGameManager.getOPLFolder()) : new File(".");
        if (start.isDirectory()) {
            chooser.setInitialDirectory(start);
        }
        File chosen = chooser.showOpenDialog(stage);
        if (chosen != null) {
            pathField.setText(chosen.toString());
        }
    }

    @FXML
    private void onSave() {
        boolean ps1 = console == Console.PS1;

        if (useEmulatorCheck.isSelected()) {
            String path = pathField.getText() == null ? "" : pathField.getText().trim();
            if (path.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "You need to set the " + emulatorName + " path.");
                WindowsDarkTitleBar.apply(alert);
                alert.showAndWait();
                return;
            }
            if (ps1) {
                PopsGameManager.setEmulatorInUsePS1(true);
                PopsGameManager.setEmulatorPathPS1(path);
                PopsGameManager.setEmulatorFullScreenPS1(fullscreenCheck.isSelected());
            } else {
                PopsGameManager.setEmulatorInUsePS2(true);
                PopsGameManager.setEmulatorPathPS2(path);
                PopsGameManager.setEmulatorFullScreenPS2(fullscreenCheck.isSelected());
            }
        } else {
            if (ps1) {
                PopsGameManager.setEmulatorInUsePS1(false);
                PopsGameManager.setEmulatorPathPS1("");
                PopsGameManager.setEmulatorFullScreenPS1(false);
            } else {
                PopsGameManager.setEmulatorInUsePS2(false);
                PopsGameManager.setEmulatorPathPS2("");
                PopsGameManager.setEmulatorFullScreenPS2(false);
            }
        }

        try {
            XMLFileManager.writeSettingsXML();
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
        close();
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void close() {
        if (stage != null) {
            stage.close();
        }
    }
}

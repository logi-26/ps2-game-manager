package ps2gm.game.manager.fx;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import ps2gm.game.manager.PopsGameManager;
import ps2gm.game.manager.XMLFileManager;

/**
 * Controller for {@code SetPartitionScreen.fxml} - "Set Remote File Locations", the
 * HDD-mode partition/path form. Loads the current values from {@link PopsGameManager}
 * (or sensible defaults), and on "Set" writes them back and persists settings.xml.
 * Choosing a {@code mass:} VCD drive collapses the VCD/ELF folder choices to "POPS"
 * only, matching the old Swing combo-model swap.
 */
public class SetPartitionController implements FxScreens.StageAware {

    private static final String[] OPL_FOLDERS  = { "hdd0:/+OPL", "mass:/+OPL", "mass1:/+OPL", "mass2:/+OPL" };
    private static final String[] VCD1_FOLDERS = { "hdd0:/", "mass:/", "mass1:/", "mass2:/" };
    private static final String[] VCD2_FULL    = { "__.POPS", "__.POPS0", "__.POPS1", "__.POPS2", "__.POPS3", "__.POPS4", "__.POPS5", "__.POPS6", "__.POPS7", "__.POPS8", "__.POPS9" };
    private static final String[] VCD2_MASS    = { "POPS" };
    private static final String[] ELF1_FULL    = { "+OPL/", "__.POPS/", "__.POPS0/", "__.POPS1/", "__.POPS2/", "__.POPS3/", "__.POPS4/", "__.POPS5/", "__.POPS6/", "__.POPS7/", "__.POPS8/", "__.POPS9/" };
    private static final String[] ELF1_MASS    = { "POPS/" };

    @FXML private ComboBox<String> oplCombo;
    @FXML private ComboBox<String> vcd1Combo;
    @FXML private ComboBox<String> vcd2Combo;
    @FXML private ComboBox<String> elf1Combo;
    @FXML private TextField elf2Field;
    @FXML private TextField ipField;

    private Stage stage;

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void initialize() {
        oplCombo.setItems(FXCollections.observableArrayList(OPL_FOLDERS));
        vcd1Combo.setItems(FXCollections.observableArrayList(VCD1_FOLDERS));
        vcd2Combo.setItems(FXCollections.observableArrayList(VCD2_FULL));
        elf1Combo.setItems(FXCollections.observableArrayList(ELF1_FULL));

        // The old MaskFormatter enforced ###.###.###.###; here just restrict to digits/dots.
        ipField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("[0-9.]{0,15}") ? change : null));

        // mass: VCD drive -> only "POPS" is valid for the VCD/ELF sub-folders
        vcd1Combo.valueProperty().addListener((obs, old, val) -> {
            boolean mass = val != null && val.startsWith("m");
            vcd2Combo.setItems(FXCollections.observableArrayList(mass ? VCD2_MASS : VCD2_FULL));
            elf1Combo.setItems(FXCollections.observableArrayList(mass ? ELF1_MASS : ELF1_FULL));
            vcd2Combo.getSelectionModel().selectFirst();
            elf1Combo.getSelectionModel().selectFirst();
        });
    }

    /** Populate the controls from the stored settings, or defaults. Called before show. */
    void loadCurrent() {
        String opl = PopsGameManager.getRemoteOPLPath();
        if (opl != null && !opl.isEmpty()) {
            select(oplCombo, opl);

            String[] vcd = PopsGameManager.getRemoteVCDPath().split("/");
            if (vcd.length >= 2) {
                select(vcd1Combo, vcd[0] + "/");
                select(vcd2Combo, vcd[1]);
            }

            String[] elf = PopsGameManager.getRemoteELFPath().split("/");
            if (elf.length > 1) {
                select(elf1Combo, elf[1] + "/");
                StringBuilder tail = new StringBuilder();
                for (int i = 2; i < elf.length; i++) {
                    tail.append(elf[i]);
                }
                elf2Field.setText(tail.toString());
            }
        } else {
            select(oplCombo, "hdd0:/+OPL");
            select(vcd1Combo, "hdd0:/");
            select(vcd2Combo, "__.POPS");
            elf1Combo.getSelectionModel().selectFirst();   // "+OPL/"
            elf2Field.setText("APPS");
        }
        ipField.setText(PopsGameManager.getPS2IP());
    }

    @FXML
    private void onSet() {
        PopsGameManager.setRemoteOPLPath(String.valueOf(oplCombo.getValue()));
        PopsGameManager.setRemoteVCDPath(String.valueOf(vcd1Combo.getValue()) + vcd2Combo.getValue());

        String elf1 = String.valueOf(elf1Combo.getValue());
        String elfPath = null;
        if (elf1.startsWith("+")) {
            elfPath = PopsGameManager.getRemoteOPLPath().split("/")[0] + "/" + elf1 + elf2Field.getText();
        } else if (elf1.startsWith("_")) {
            elfPath = PopsGameManager.getRemoteVCDPath().split("/")[0] + "/" + elf1 + elf2Field.getText();
        }
        PopsGameManager.setRemoteELFPath(elfPath);
        PopsGameManager.setPS2IP(ipField.getText());

        try {
            XMLFileManager.writeSettingsXML();
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug("Error saving the settings!\n\n" + ex);
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

    private static void select(ComboBox<String> combo, String value) {
        if (combo.getItems().contains(value)) {
            combo.setValue(value);
        }
    }
}

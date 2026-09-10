package ps2gm.game.manager.fx;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ps2gm.game.manager.AddGameManager;
import ps2gm.game.manager.Console;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code GameRenamingScreen.fxml} - fix VCD/ISO files whose name does
 * not carry the game ID. Handles PS1 (VCD, plus its ELF) and PS2 (ISO/ZSO) via a
 * {@code console} flag. Game-ID detection reuses
 * {@code GameListManager.get{PS1,PS2}GameID…}; on a successful rename the game is
 * added back to the list with {@code GameListManager.addToGameLists…}.
 */
public class GameRenamingController implements FxScreens.StageAware {

    @FXML private ListView<String> gameListView;
    @FXML private TextField titleField;
    @FXML private TextField numberField;
    @FXML private TextField idField;
    @FXML private TextField sizeField;
    @FXML private TextField oldTitleField;
    @FXML private TextField newTitleField;
    @FXML private Label elfLabel;
    @FXML private TextField elfNameField;
    @FXML private Button renameButton;
    @FXML private Button renameAllButton;

    private Stage stage;
    private Console console;
    private final List<File> invalid = new ArrayList<>();
    private final List<Game> games = new ArrayList<>();

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(" Rename Games");
    }

    @FXML
    private void initialize() {
        gameListView.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> displayDetails());
    }

    /** Called from the façade before the window is shown. */
    void init(Console console, List<File> invalidFiles) {
        this.console = console;
        boolean ps1 = console == Console.PS1;
        elfLabel.setVisible(ps1);
        elfLabel.setManaged(ps1);
        elfNameField.setVisible(ps1);
        elfNameField.setManaged(ps1);

        this.invalid.clear();
        this.invalid.addAll(invalidFiles);
        rebuild();
    }

    private void rebuild() {
        games.clear();
        for (File f : invalid) {
            String id = null;
            try {
                id = console == Console.PS1
                        ? GameListManager.getPS1GameIDFromVCD(f)
                        : GameListManager.getPS2GameIDFromArchive(f.getAbsolutePath());
            } catch (Exception ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
            String fn = f.getName();
            String name = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
            games.add(new Game(name, id, f.toString(), PopsGameManager.bytesToHuman(f.length()), f.length()));
        }
        List<String> names = new ArrayList<>();
        for (Game g : games) {
            names.add(g.getGameName());
        }
        gameListView.getItems().setAll(names);
        if (!names.isEmpty()) {
            gameListView.getSelectionModel().select(0);
        } else {
            clearDetails();
        }
    }

    private void displayDetails() {
        int idx = gameListView.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= games.size()) {
            return;
        }
        Game g = games.get(idx);
        File orig = invalid.get(idx);
        titleField.setText(" " + g.getGameName());
        numberField.setText((idx + 1) + "/" + games.size());
        idField.setText(g.getGameID());
        sizeField.setText(g.getGameReadableSize());
        oldTitleField.setText(orig.getName());

        if (console == Console.PS1) {
            newTitleField.setText(g.getGameName() + "-" + g.getGameID() + ".VCD");
            elfNameField.setText(PopsGameManager.getFilePrefix() + g.getGameName() + "-" + g.getGameID() + ".ELF");
        } else {
            String ext = orig.getName().substring(orig.getName().length() - 4);
            newTitleField.setText(g.getGameID() + "." + g.getGameName() + ext);
        }
    }

    private void clearDetails() {
        titleField.clear();
        numberField.clear();
        idField.clear();
        sizeField.clear();
        oldTitleField.clear();
        newTitleField.clear();
        elfNameField.clear();
    }

    @FXML
    private void onRename() {
        int idx = gameListView.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= invalid.size()) {
            return;
        }
        String newName = newTitleField.getText();
        if (newName.isEmpty()) {
            return;
        }
        if (newName.length() <= 4) {
            error("The new name appears to be too short!");
            return;
        }
        String ext = newName.substring(newName.length() - 4);
        boolean ps1 = console == Console.PS1;
        boolean extOk = ps1 ? (ext.equals(".vcd") || ext.equals(".VCD"))
                            : (ext.equalsIgnoreCase(".iso") || ext.equalsIgnoreCase(".zso"));
        if (!extOk) {
            error("You cannot change the games file extension!");
            return;
        }

        File orig = invalid.get(idx);
        String origPath = orig.getAbsolutePath().replace(orig.getName(), "");
        File newFile = new File(origPath + newName);
        if (newFile.exists()) {
            error("There is already a game with that name in the directory!");
            return;
        }

        if (!orig.renameTo(newFile)) {
            error("The file could not be renamed!");
        } else {
            invalid.remove(orig);
            if (ps1) {
                GameListManager.addToGameListsPS1(newFile);
            } else {
                GameListManager.addToGameListsPS2(newFile);
            }
            rebuild();
        }

        if (ps1) {
            // ELF cleanup + regeneration (runs whether or not the rename itself succeeded, as in the Swing version)
            String prefix = PopsGameManager.getFilePrefix();
            String dir = orig.getAbsolutePath().substring(0, orig.getAbsolutePath().lastIndexOf(File.separator) + 1);
            String origStem = orig.getName().substring(0, orig.getName().length() - 4);
            String newStem = newName.substring(0, newName.length() - 4);
            for (String elf : new String[] {
                    dir + prefix + origStem + ".ELF",
                    orig.getAbsolutePath().substring(0, orig.getAbsolutePath().length() - 4) + ".ELF",
                    dir + prefix + newStem + ".ELF",
                    dir + newStem + ".ELF" }) {
                File f = new File(elf);
                if (f.isFile()) {
                    f.delete();
                }
            }
            AddGameManager.generateElf(elfNameField.getText(), dir);
        }
    }

    @FXML
    private void onRenameAll() {
        boolean ps1 = console == Console.PS1;
        List<File> stillInvalid = new ArrayList<>();

        for (File f : new ArrayList<>(invalid)) {
            String id = null;
            try {
                id = ps1 ? GameListManager.getPS1GameIDFromVCD(f)
                         : GameListManager.getPS2GameIDFromArchive(f.getAbsolutePath());
            } catch (Exception ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }

            String origPath = f.getAbsolutePath().replace(f.getName(), "");
            String stem = f.getName().substring(0, f.getName().length() - 4);
            File newFile = ps1
                    ? new File(origPath + stem + "-" + id + ".VCD")
                    : new File(origPath + id + "." + f.getName());

            if (!newFile.exists()) {
                if (f.renameTo(newFile)) {
                    if (ps1) {
                        GameListManager.addToGameListsPS1(newFile);
                    } else {
                        GameListManager.addToGameListsPS2(newFile);
                    }
                } else {
                    stillInvalid.add(f);
                }
            }

            if (ps1) {
                String prefix = PopsGameManager.getFilePrefix();
                for (String elf : new String[] {
                        newFile.getAbsolutePath().substring(0, newFile.getAbsolutePath().lastIndexOf(File.separator) + 1) + prefix + newFile.getName().substring(0, newFile.getName().length() - 4) + ".ELF",
                        newFile.getAbsolutePath().substring(0, newFile.getAbsolutePath().length() - 4) + ".ELF" }) {
                    File e = new File(elf);
                    if (e.isFile()) {
                        e.delete();
                    }
                }
                AddGameManager.generateElf(prefix + stem + "-" + id + ".ELF", newFile.getParent() + File.separator);
            }
        }

        invalid.clear();
        invalid.addAll(stillInvalid);
        rebuild();
        clearDetails();

        if (!invalid.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "There was a problem whilst attempting to rename some of the games!");
            WindowsDarkTitleBar.apply(alert);
            alert.showAndWait();
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "All of the files have been succesfully renamed!");
            WindowsDarkTitleBar.apply(alert);
            alert.showAndWait();
            renameButton.setDisable(true);
            renameAllButton.setDisable(true);
            newTitleField.setDisable(true);
        }
    }

    @FXML
    private void onExit() {
        if (stage != null) {
            stage.close();
        }
    }

    private static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        WindowsDarkTitleBar.apply(alert);
        alert.showAndWait();
    }
}

package ps2gm.game.manager.fx;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ps2gm.game.manager.BackendClient;
import ps2gm.game.manager.BackgroundTasks;
import ps2gm.game.manager.Console;
import ps2gm.game.manager.GameIdExtractor;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.GameSuggestion;
import ps2gm.game.manager.MultiDiscGameCatalog;
import ps2gm.game.manager.MultiDiscGameCatalog.SuggestionRow;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code BadGameListScreen.fxml} - games {@code GameListManager}
 * couldn't detect a unique ID for at all, unlike {@link GameRenamingController}'s
 * case where the ID *is* known but the filename just doesn't carry it.
 *
 * There's no ID here to auto-suggest a corrected name from, but the Suggestions
 * panel offers name-match candidates from a reference catalogue (see {@code
 * BackendClient.suggestGameIds}) - never applied automatically, always shown as
 * a list to pick from, since a name match alone can't tell a multi-disc game's
 * discs apart (see {@link MultiDiscGameCatalog#expandForDisplay}). Picking one
 * only fills in the New Name field; the user still presses Rename themselves,
 * which is what actually renames the file and re-checks the ID.
 */
public class BadGameListController implements FxScreens.StageAware {

    @FXML private Label messageLabel;
    @FXML private ListView<String> gameListView;
    @FXML private ListView<String> suggestionListView;
    @FXML private Button useSuggestionButton;
    @FXML private TextField oldNameField;
    @FXML private TextField newNameField;
    @FXML private Button renameButton;

    private Stage stage;
    private Console console;
    private final List<File> files = new ArrayList<>();
    private List<SuggestionRow> currentSuggestions = List.of();

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        // Games renamed successfully here were added straight into GameListManager's
        // in-memory list (see onRename) - refresh the main window once this closes.
        stage.setOnHidden(e -> PopsGameManager.callbackToUpdateGUIGameList(null, -1));
    }

    @FXML
    private void initialize() {
        gameListView.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> displaySelected());
    }

    /** Called from the façade before the window is shown. */
    void init(Console console, String message, List<File> badFiles) {
        this.console = console;
        messageLabel.setText(message);
        files.clear();
        files.addAll(badFiles);
        rebuild();
    }

    private void rebuild() {
        List<String> names = new ArrayList<>();
        for (File f : files) {
            names.add(f.getName());
        }
        gameListView.getItems().setAll(names);
        if (!names.isEmpty()) {
            gameListView.getSelectionModel().select(0);
        } else {
            clearDetails();
        }
        renameButton.setDisable(names.isEmpty());
        newNameField.setDisable(names.isEmpty());
    }

    private void displaySelected() {
        int idx = gameListView.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= files.size()) {
            return;
        }
        File file = files.get(idx);
        oldNameField.setText(file.getName());
        newNameField.setText(file.getName());
        fetchSuggestions(file);
    }

    private void clearDetails() {
        oldNameField.clear();
        newNameField.clear();
        currentSuggestions = List.of();
        suggestionListView.getItems().clear();
        useSuggestionButton.setDisable(true);
    }

    private void fetchSuggestions(File file) {
        currentSuggestions = List.of();
        suggestionListView.getItems().setAll("Searching...");
        useSuggestionButton.setDisable(true);

        String query = GameIdExtractor.deriveDescriptiveName(file.getName(), null);
        BackgroundTasks.runDaemon("fx-suggest-game-id", () -> {
            BackendClient api = PopsGameManager.newBackendClient();
            List<GameSuggestion> raw = api.suggestGameIds(console, query);
            List<SuggestionRow> rows = MultiDiscGameCatalog.expandForDisplay(raw);
            Platform.runLater(() -> {
                // The user may have picked a different game while this was in flight.
                int idx = gameListView.getSelectionModel().getSelectedIndex();
                if (idx < 0 || idx >= files.size() || files.get(idx) != file) {
                    return;
                }
                currentSuggestions = rows;
                if (rows.isEmpty()) {
                    suggestionListView.getItems().setAll("No matches found for \"" + query + "\"");
                } else {
                    List<String> labels = new ArrayList<>();
                    for (SuggestionRow row : rows) {
                        labels.add(row.label());
                    }
                    suggestionListView.getItems().setAll(labels);
                }
                useSuggestionButton.setDisable(rows.isEmpty());
            });
        });
    }

    @FXML
    private void onUseSuggestion() {
        int idx = suggestionListView.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= currentSuggestions.size()) {
            return;
        }
        int fileIdx = gameListView.getSelectionModel().getSelectedIndex();
        if (fileIdx < 0 || fileIdx >= files.size()) {
            return;
        }

        String gameId = currentSuggestions.get(idx).gameId();
        String origName = files.get(fileIdx).getName();
        String descriptiveName = GameIdExtractor.deriveDescriptiveName(origName, null);
        boolean ps1 = console == Console.PS1;
        String proposedName;
        if (ps1) {
            proposedName = descriptiveName + "-" + gameId + ".VCD";
        } else {
            // Keep the file's own extension (.iso / .zso, as typed) and respect the
            // user's chosen PS2 ID position (Tools menu / GameIdPositionSwitcher)
            // instead of always putting the ID first.
            String ext = origName.length() >= 4 ? origName.substring(origName.length() - 4) : ".ISO";
            boolean idFirst = !"end".equals(PopsGameManager.getGameIDPositionPS2());
            proposedName = idFirst
                    ? gameId + "." + descriptiveName + ext
                    : descriptiveName + "." + gameId + ext;
        }
        newNameField.setText(proposedName);
    }

    @FXML
    private void onRename() {
        int idx = gameListView.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= files.size()) {
            return;
        }

        String newName = newNameField.getText();
        if (newName == null || newName.isEmpty()) {
            return;
        }
        if (newName.length() <= 4) {
            error("The new name appears to be too short!");
            return;
        }

        boolean ps1 = console == Console.PS1;
        String ext = newName.substring(newName.length() - 4);
        boolean extOk = ps1 ? ext.equalsIgnoreCase(".vcd") : (ext.equalsIgnoreCase(".iso") || ext.equalsIgnoreCase(".zso"));
        if (!extOk) {
            error("The new name must keep a " + (ps1 ? "\".VCD\"" : "\".ISO\"/\".ZSO\"") + " extension!");
            return;
        }

        File orig = files.get(idx);
        File newFile = new File(orig.getParentFile(), newName);
        if (newFile.exists()) {
            error("There is already a file with that name in the directory!");
            return;
        }
        if (!orig.renameTo(newFile)) {
            error("The file could not be renamed!");
            return;
        }

        String detectedId = null;
        try {
            detectedId = ps1 ? GameListManager.getPS1GameIDFromVCD(newFile) : GameListManager.getPS2GameIDFromArchive(newFile.getAbsolutePath());
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }

        if (detectedId != null) {
            if (ps1) {
                GameListManager.addToGameListsPS1(newFile);
            } else {
                GameListManager.addToGameListsPS2(newFile);
            }
            files.remove(idx);
            info("Game ID detected (" + detectedId + ") - added to your " + console + " game list.");
        } else {
            files.set(idx, newFile);
            info("Renamed, but the game ID still could not be detected.\n\nMake sure the new name includes a valid " + console + " serial (e.g. SLES_546.22) and try again.");
        }

        rebuild();
    }

    @FXML
    private void onClose() {
        stage.close();
    }

    private static void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        WindowsDarkTitleBar.apply(alert);
        alert.showAndWait();
    }

    private static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        WindowsDarkTitleBar.apply(alert);
        alert.showAndWait();
    }
}

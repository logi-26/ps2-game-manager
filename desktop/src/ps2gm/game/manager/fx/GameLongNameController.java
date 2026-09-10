package ps2gm.game.manager.fx;

import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import ps2gm.game.manager.Console;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.GameLongNameRenamer;
import ps2gm.game.manager.Mode;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code GameLongNameScreen.fxml} - rename games whose title is over
 * 32 characters. UI only: pick a game, edit the (32-capped) new title, Rename. The
 * file/FTP work is {@link GameLongNameRenamer}; on success the affected game list is
 * regenerated and the main screen refreshed via
 * {@code PopsGameManager.callbackToUpdateGUIGameList}.
 */
public class GameLongNameController {

    @FXML private TextField oldTitleField;
    @FXML private TextField newTitleField;
    @FXML private ListView<String> gameListView;

    private Console console;
    private final List<Game> longNameList = new ArrayList<>();

    @FXML
    private void initialize() {
        newTitleField.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().length() <= 32 ? change : null));
        gameListView.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> displayDetails());
    }

    /** Called from the façade before the window is shown. */
    void init(Console console, List<Game> longNameGames) {
        this.console = console;
        this.longNameList.clear();
        this.longNameList.addAll(longNameGames);
        refreshList();
    }

    private void refreshList() {
        List<String> names = new ArrayList<>();
        for (Game g : longNameList) {
            names.add(g.getGameName());
        }
        gameListView.getItems().setAll(names);
        if (!names.isEmpty()) {
            gameListView.getSelectionModel().select(0);
        } else {
            oldTitleField.clear();
            newTitleField.clear();
        }
    }

    private void displayDetails() {
        int idx = gameListView.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= longNameList.size()) {
            return;
        }
        String name = longNameList.get(idx).getGameName();
        oldTitleField.setText(name);
        newTitleField.setText(name.substring(0, 32));
    }

    @FXML
    private void onRename() {
        int idx = gameListView.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= longNameList.size()) {
            return;
        }
        String newTitle = newTitleField.getText();
        if (newTitle.isEmpty()) {
            return;
        }

        Game game = longNameList.get(idx);
        GameLongNameRenamer renamer = new GameLongNameRenamer(game, oldTitleField.getText(), newTitle);
        Mode mode = PopsGameManager.getCurrentMode();

        if (console == Console.PS1) {
            if (nameAlreadyUsed(GameListManager.getGameListPS1(), newTitle)) {
                return;
            }
            switch (mode) {
                case SMB:     if (renamer.renameLocalPS1("SB.")) { onRenamed(idx); } break;
                case HDD_USB: if (renamer.renameLocalPS1("XX.")) { onRenamed(idx); } break;
                case HDD:     renamer.ftpRenamePS1(); break;
                default: break;
            }
        } else if (console == Console.PS2) {
            if (nameAlreadyUsed(GameListManager.getGameListPS2(), newTitle)) {
                return;
            }
            switch (mode) {
                case SMB:
                case HDD_USB:
                    if (renamer.renameLocalPS2()) { onRenamed(idx); }
                    break;
                case HDD: {
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                            "The application cannot currently rename a PS2 game in HDD mode!");
                    WindowsDarkTitleBar.apply(alert);
                    alert.showAndWait();
                    break;
                }
                default:
                    break;
            }
        }
    }

    private boolean nameAlreadyUsed(List<Game> list, String newTitle) {
        if (list != null) {
            for (Game g : list) {
                if (g.getGameName().equals(newTitle)) {
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                            "A game with the same name is already in the game list!");
                    WindowsDarkTitleBar.apply(alert);
                    alert.showAndWait();
                    return true;
                }
            }
        }
        return false;
    }

    private void onRenamed(int idx) {
        longNameList.remove(idx);

        if (console == Console.PS1) {
            GameListManager.createGameListsPS1();
            GameListManager.writeConfigELM();
        } else if (console == Console.PS2) {
            GameListManager.createGameListsPS2(false);
        }
        PopsGameManager.callbackToUpdateGUIGameList(null, -1);

        refreshList();
    }
}

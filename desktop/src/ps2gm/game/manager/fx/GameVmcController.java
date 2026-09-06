package ps2gm.game.manager.fx;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ps2gm.game.manager.BackendClient;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code GameVmcScreen.fxml} - "Download Game VMC Files".
 *
 * Left: the current console's game list. Right: the selected game's title/ID, the
 * VMC slots the server has for that game ID, a description, and a Download button.
 * The server list is fetched off the FX thread. Closing the window calls
 * {@code PopsGameManager.callbackToUpdateGUIGameList(...)} so the main screen picks
 * up any newly downloaded VMC.
 */
public class GameVmcController implements FxScreens.StageAware {

    @FXML private ListView<String> gameListView;
    @FXML private TextField titleField;
    @FXML private TextField idField;
    @FXML private ListView<String> vmcListView;
    @FXML private TextArea descriptionArea;
    @FXML private javafx.scene.control.Button downloadButton;

    private Stage stage;
    private final List<String> serverLabels = new ArrayList<>();
    private final List<String> serverDescriptions = new ArrayList<>();

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(e ->
                PopsGameManager.callbackToUpdateGUIGameList(null, gameListView.getSelectionModel().getSelectedIndex()));
    }

    @FXML
    private void initialize() {
        gameListView.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> onGameSelected());
        vmcListView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> onVmcSelected(n));
    }

    /** Called from the façade before the window is shown. */
    void initialiseGUI(int gameIndex) {
        List<String> names = new ArrayList<>();
        for (Game g : currentGames()) {
            names.add(g.getGameName());
        }
        gameListView.getItems().setAll(names);
        if (gameIndex >= 0 && gameIndex < names.size()) {
            gameListView.getSelectionModel().select(gameIndex);
            gameListView.scrollTo(gameIndex);
        }
        loadServerListInBackground();
    }

    private void loadServerListInBackground() {
        String console = PopsGameManager.getCurrentConsole();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                BackendClient api = PopsGameManager.newBackendClient();
                api.getListFromServer("VMC", console);
                File dat = new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator
                        + "data" + File.separator + "lists" + File.separator + console + "_ServerVMCList.dat");
                List<String> labels = new ArrayList<>();
                List<String> descs = new ArrayList<>();
                if (dat.isFile()) {
                    try {
                        for (String line : Files.readAllLines(dat.toPath(), StandardCharsets.UTF_8)) {
                            int sep = line.indexOf(' ');
                            if (sep > 0) {
                                labels.add(line.substring(0, sep));
                                descs.add(sep + 1 < line.length() ? line.substring(sep + 1) : "");
                            }
                        }
                    } catch (Exception ex) {
                        PopsGameManager.displayErrorMessageDebug(ex.toString());
                    }
                }
                Platform.runLater(() -> {
                    serverLabels.clear();
                    serverLabels.addAll(labels);
                    serverDescriptions.clear();
                    serverDescriptions.addAll(descs);
                    onGameSelected();
                });
                return null;
            }
        };
        Thread t = new Thread(task, "fx-vmc-list");
        t.setDaemon(true);
        t.start();
    }

    private void onGameSelected() {
        int idx = gameListView.getSelectionModel().getSelectedIndex();
        List<Game> games = currentGames();
        vmcListView.getItems().clear();
        descriptionArea.clear();
        downloadButton.setDisable(true);

        if (idx < 0 || idx >= games.size()) {
            titleField.clear();
            idField.clear();
            return;
        }
        Game game = games.get(idx);
        titleField.setText(" " + game.getGameName());
        idField.setText(game.getGameID());

        String gameId = game.getGameID();
        List<String> matches = new ArrayList<>();
        for (String label : serverLabels) {
            if (label.length() >= 11 && label.substring(0, 11).equals(gameId)) {
                matches.add(label);
            }
        }
        vmcListView.getItems().setAll(matches);
    }

    private void onVmcSelected(String label) {
        if (label == null) {
            descriptionArea.clear();
            downloadButton.setDisable(true);
            return;
        }
        int i = serverLabels.indexOf(label);
        descriptionArea.setText(i >= 0 ? serverDescriptions.get(i) : "");
        downloadButton.setDisable(false);
    }

    @FXML
    private void onDownload() {
        String label = vmcListView.getSelectionModel().getSelectedItem();
        if (label == null) {
            return;
        }
        String gameId = idField.getText();
        String gameName = titleField.getText() == null ? "" : titleField.getText().trim();
        String region = PopsGameManager.determineGameRegion(gameId.substring(0, 4));

        downloadButton.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                PopsGameManager.newBackendClient().getVMCFromServer(region, label, gameName, gameId);
                return null;
            }
        };
        task.setOnSucceeded(e -> downloadButton.setDisable(false));
        task.setOnFailed(e -> {
            PopsGameManager.displayErrorMessageDebug(String.valueOf(task.getException()));
            downloadButton.setDisable(false);
        });
        Thread t = new Thread(task, "fx-vmc-download");
        t.setDaemon(true);
        t.start();
    }

    private static List<Game> currentGames() {
        List<Game> games = "PS1".equals(PopsGameManager.getCurrentConsole())
                ? GameListManager.getGameListPS1()
                : GameListManager.getGameListPS2();
        return games != null ? games : new ArrayList<>();
    }
}

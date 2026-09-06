package ps2gm.game.manager.fx;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import ps2gm.game.manager.BackendClient;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.GameArtFileManager;
import ps2gm.game.manager.GameConfigFileManager;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code BatchDownloadScreen.fxml} - fetch every missing art / config
 * file for the current console's games. One instance per console (PS1/PS2 differ only
 * in the game list and the "no image" cover placeholder). Faithful port of the Swing
 * {@code BatchDownloadScreenPS1/PS2}: one background thread per missing file, previews
 * update as they land, progress bar tracks completion.
 */
public class BatchDownloadController implements FxScreens.StageAware {

    @FXML private ImageView frontCoverView, rearCoverView, spineView, logoView, discView, screenshot1View, screenshot2View, backgroundView;
    @FXML private CheckBox frontCoverCheck, rearCoverCheck, spineCheck, logoCheck, discCheck, screenshotCheck, backgroundCheck, configCheck;
    @FXML private Label missingLabel;
    @FXML private Button downloadButton;
    @FXML private ProgressBar progressBar;
    @FXML private TextField currentGameField;
    @FXML private ListView<String> processedListView;

    private Stage stage;
    private String console;
    private final List<Game> gameList = new ArrayList<>();
    private final List<String> artList = new ArrayList<>();
    private final List<String> configList = new ArrayList<>();
    private final List<String> processed = new ArrayList<>();

    private int missingFrontCovers, missingRearCovers, missingSpines, missingLogos,
                missingDiscs, missingScreenshots, missingBackgrounds, missingConfigs;

    private int totalToDownload;
    private final AtomicInteger totalProcessed = new AtomicInteger();
    private final AtomicInteger threadCount = new AtomicInteger();

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(" Batch Downloads");
        stage.setOnHidden(e -> PopsGameManager.callbackToUpdateGUIGameList(null, -1));
    }

    /** Called from the façade before the window is shown. */
    void init(String console) {
        this.console = console;
        List<Game> src = "PS1".equals(console) ? GameListManager.getGameListPS1() : GameListManager.getGameListPS2();
        if (src != null) {
            gameList.addAll(src);
        }
        artList.addAll(listNames("ART"));
        configList.addAll(listNames("CFG"));
        detectMissingFiles();
        missingLabel.setText(String.format("Missing: %d cov, %d cov2, %d spine, %d logo, %d disc, %d scr, %d bg, %d cfg",
                missingFrontCovers, missingRearCovers, missingSpines, missingLogos,
                missingDiscs, missingScreenshots, missingBackgrounds, missingConfigs));
    }

    private static List<String> listNames(String subDir) {
        List<String> out = new ArrayList<>();
        File[] files = new File(PopsGameManager.getOPLFolder() + File.separator + subDir + File.separator).listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    out.add(f.getName());
                }
            }
        }
        return out;
    }

    private void detectMissingFiles() {
        for (Game g : gameList) {
            if (GameArtFileManager.isMissing(artList, g, "_COV"))  { missingFrontCovers++; }
            if (GameArtFileManager.isMissing(artList, g, "_COV2")) { missingRearCovers++; }
            if (GameArtFileManager.isMissing(artList, g, "_ICO"))  { missingDiscs++; }
            if (GameArtFileManager.isMissing(artList, g, "_SCR"))  { missingScreenshots++; }
            if (GameArtFileManager.isMissing(artList, g, "_SCR2")) { missingScreenshots++; }
            if (GameArtFileManager.isMissing(artList, g, "_BG"))   { missingBackgrounds++; }
            if (GameArtFileManager.isMissing(artList, g, "_LAB"))  { missingSpines++; }
            if (GameArtFileManager.isMissing(artList, g, "_LGO"))  { missingLogos++; }
            if (!configList.contains(g.getGameID() + ".cfg"))      { missingConfigs++; }
        }
    }

    @FXML
    private void onDownload() {
        // snapshot the checkbox state on the FX thread, then run the batch off it
        boolean cov = frontCoverCheck.isSelected(), cov2 = rearCoverCheck.isSelected(),
                spine = spineCheck.isSelected(), logo = logoCheck.isSelected(),
                disc = discCheck.isSelected(), scr = screenshotCheck.isSelected(),
                bg = backgroundCheck.isSelected(), cfg = configCheck.isSelected();
        downloadButton.setDisable(true);
        processed.clear();
        processedListView.getItems().clear();
        Thread t = new Thread(() -> runBatch(cov, cov2, spine, logo, disc, scr, bg, cfg), "fx-batch-download");
        t.setDaemon(true);
        t.start();
    }

    private void runBatch(boolean cov, boolean cov2, boolean spine, boolean logo, boolean disc, boolean scr, boolean bg, boolean cfg) {
        BackendClient api = PopsGameManager.newBackendClient();
        if (!"RESPONSE".equals(api.sendMessageToServer("RESPOND"))) {
            Platform.runLater(() -> {
                new Alert(Alert.AlertType.ERROR, "The server is not responding at the moment!").showAndWait();
                downloadButton.setDisable(false);
            });
            return;
        }

        totalToDownload = (cov ? missingFrontCovers : 0) + (cov2 ? missingRearCovers : 0)
                + (disc ? missingDiscs : 0) + (scr ? missingScreenshots : 0)
                + (bg ? missingBackgrounds : 0) + (spine ? missingSpines : 0)
                + (logo ? missingLogos : 0) + (cfg ? missingConfigs : 0);
        totalProcessed.set(0);
        threadCount.set(0);
        Platform.runLater(() -> progressBar.setProgress(0));

        if (totalToDownload == 0) {
            Platform.runLater(() -> downloadButton.setDisable(false));
            return;
        }

        if (cov)   { spawnFor("_COV"); }
        if (cov2)  { spawnFor("_COV2"); }
        if (disc)  { spawnFor("_ICO"); }
        if (scr)   { spawnFor("_SCR"); spawnFor("_SCR2"); }
        if (bg)    { spawnFor("_BG"); }
        if (spine) { spawnFor("_LAB"); }
        if (logo)  { spawnFor("_LGO"); }
        if (cfg) {
            for (Game g : gameList) {
                if (!configList.contains(g.getGameID() + ".cfg")) {
                    spawn(g, "CONFIG");
                }
            }
        }
    }

    private void spawnFor(String suffix) {
        for (Game g : gameList) {
            if (GameArtFileManager.isMissing(artList, g, suffix)) {
                spawn(g, suffix);
            }
        }
    }

    private void spawn(Game game, String fileType) {
        Thread t = new Thread(() -> downloadOne(game, fileType), "fx-batch-" + fileType);
        t.setDaemon(true);
        t.start();
    }

    private void downloadOne(Game game, String fileType) {
        try {
            int done = totalProcessed.incrementAndGet();
            Platform.runLater(() -> progressBar.setProgress((double) done / totalToDownload));

            if (game.getGameRawSize() <= 5_000_000L) {
                return; // guards against dummy files, same as the Swing version
            }

            String region = PopsGameManager.determineGameRegion(game.getGameID().split("_")[0]);
            BackendClient api = PopsGameManager.newBackendClient();
            switch (fileType) {
                case "CONFIG":
                    api.getConfigFromServer(region, game.getGameID(), game.getGameName(), true);
                    break;
                case "_SCR2":
                    api.getImageFromServer(game, region, game.getGameID(), game.getGameName(), fileType, fileType, 1, true);
                    break;
                default:
                    api.getImageFromServer(game, region, game.getGameID(), game.getGameName(), fileType, fileType, 0, true);
                    break;
            }

            Platform.runLater(() -> {
                currentGameField.setText(" " + game.getGameName() + " : " + game.getGameID());
                if (!processed.contains(game.getGameName())) {
                    processed.add(game.getGameName());
                    processedListView.getItems().setAll(processed);
                }
                updatePreview(game, fileType);
            });
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        } finally {
            if (threadCount.incrementAndGet() == totalToDownload) {
                GameConfigFileManager.renameConfigTitlesToMatch(gameList);
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    downloadButton.setDisable(false);
                });
            }
        }
    }

    private void updatePreview(Game game, String fileType) {
        switch (fileType) {
            case "_COV":  setImage(frontCoverView, GameArtFileManager.resolve(game, "_COV"), coverW(), coverH()); break;
            case "_COV2": setImage(rearCoverView, GameArtFileManager.resolve(game, "_COV2"), coverW(), coverH()); break;
            case "_BG":   setImage(backgroundView, GameArtFileManager.resolve(game, "_BG"), 300, 170); break;
            case "_ICO":  setImage(discView, GameArtFileManager.resolve(game, "_ICO"), 70, 70); break;
            case "_SCR":  setImage(screenshot1View, GameArtFileManager.resolve(game, "_SCR"), 225, 170); break;
            case "_SCR2": setImage(screenshot2View, GameArtFileManager.resolve(game, "_SCR2"), 225, 170); break;
            case "_LAB":  setImage(spineView, GameArtFileManager.resolve(game, "_LAB"), 20, 260); break;
            case "_LGO":  setImage(logoView, GameArtFileManager.resolve(game, "_LGO"), 195, 93); break;
            default: break;
        }
    }

    private int coverW() { return 160; }
    private int coverH() { return "PS1".equals(console) ? 160 : 210; }

    private static void setImage(ImageView view, File file, int w, int h) {
        if (file != null && file.isFile()) {
            view.setFitWidth(w);
            view.setFitHeight(h);
            view.setImage(new Image(file.toURI().toString(), w, h, false, true));
        }
    }
}

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
import ps2gm.game.manager.BackgroundTasks;
import ps2gm.game.manager.Console;
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

    // Preview sizes, matching GameImageController's (the per-game ART screen this mirrors).
    private static final int[] BG_PREVIEW    = {380, 170};
    private static final int[] DISC_PREVIEW  = {70, 70};
    private static final int[] SCR_PREVIEW   = {300, 170};
    private static final int[] SPINE_PREVIEW = {20, 260};
    private static final int[] LOGO_PREVIEW  = {195, 93};

    @FXML private ImageView covView, cov2View, spineView1, logoView1, discView1, scr1View, scr2View, bgView;
    @FXML private CheckBox frontCoverCheck, rearCoverCheck, spineCheck, logoCheck, discCheck, screenshotCheck, backgroundCheck, configCheck;
    @FXML private Label missingLabel;
    @FXML private Button downloadButton;
    @FXML private ProgressBar progressBar;
    @FXML private TextField currentGameField;
    @FXML private ListView<String> processedListView;

    private Stage stage;
    private Console console;
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
    void init(Console console) {
        this.console = console;
        List<Game> src = console == Console.PS1 ? GameListManager.getGameListPS1() : GameListManager.getGameListPS2();
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
        BackgroundTasks.runDaemon("fx-batch-download", () -> runBatch(cov, cov2, spine, logo, disc, scr, bg, cfg));
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
        BackgroundTasks.runDaemon("fx-batch-" + fileType, () -> downloadOne(game, fileType));
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

            // The API calls above report no success/failure of their own (a missing server-side
            // file is a silent no-op, not an error) - check disk directly so a game that had
            // nothing to download doesn't show up as "processed".
            boolean downloaded = fileType.equals("CONFIG")
                    ? GameConfigFileManager.exists(game.getGameID(), game.getGameName())
                    : GameArtFileManager.resolve(game, fileType) != null;

            Platform.runLater(() -> {
                currentGameField.setText(" " + game.getGameName() + " : " + game.getGameID());
                if (downloaded) {
                    if (!processed.contains(game.getGameName())) {
                        processed.add(game.getGameName());
                        processedListView.getItems().setAll(processed);
                    }
                    updatePreview(game, fileType);
                }
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
        int[] coverPreview = console == Console.PS1 ? new int[] {130, 160} : new int[] {130, 210};
        switch (fileType) {
            case "_COV":  setImage(covView, GameArtFileManager.resolve(game, "_COV"), coverPreview); break;
            case "_COV2": setImage(cov2View, GameArtFileManager.resolve(game, "_COV2"), coverPreview); break;
            case "_BG":   setImage(bgView, GameArtFileManager.resolve(game, "_BG"), BG_PREVIEW); break;
            case "_ICO":  setImage(discView1, GameArtFileManager.resolve(game, "_ICO"), DISC_PREVIEW); break;
            case "_SCR":  setImage(scr1View, GameArtFileManager.resolve(game, "_SCR"), SCR_PREVIEW); break;
            case "_SCR2": setImage(scr2View, GameArtFileManager.resolve(game, "_SCR2"), SCR_PREVIEW); break;
            case "_LAB":  setImage(spineView1, GameArtFileManager.resolve(game, "_LAB"), new int[] {SPINE_PREVIEW[0], coverPreview[1]}); break;
            case "_LGO":  setImage(logoView1, GameArtFileManager.resolve(game, "_LGO"), LOGO_PREVIEW); break;
            default: break;
        }
    }

    // The ImageViews' own fitWidth/fitHeight are bound to their containing StackPane in FXML
    // (matching GameImageScreen.fxml) - the size passed here is just ImageIO's decode-size hint.
    private static void setImage(ImageView view, File file, int[] size) {
        if (file != null && file.isFile()) {
            view.setImage(new Image(file.toURI().toString(), size[0], size[1], true, true));
        }
    }
}

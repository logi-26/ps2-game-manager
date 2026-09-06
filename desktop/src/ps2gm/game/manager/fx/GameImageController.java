package ps2gm.game.manager.fx;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import ps2gm.game.manager.BackendClient;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.GameArtFileManager;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.ImageChangedListener;
import ps2gm.game.manager.ImageSelectListener;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code GameImageScreen.fxml} - the per-game ART manager (8 preview
 * panes, each with File / Auto / Del). One instance per console; PS1 and PS2 differ
 * only in the game list, the front/rear cover aspect ratio and the "no image" cover
 * placeholder, so they share this class (the Swing {@code GameImageScreenPS1} and
 * {@code GameImageScreenPS2} were near-identical copies).
 *
 * <ul>
 *   <li><b>File</b> - {@link PopsGameManager#manualImageSelection} (a Swing
 *       {@code JFileChooser}, run on the EDT) picks + rescales + copies a local
 *       image into the OPL {@code ART/} directory; we then re-read that directory.</li>
 *   <li><b>Auto</b> - queries the backend for how many alternatives exist, downloads
 *       the first, and opens {@link GameImageSelectorScreen} to browse them.</li>
 *   <li><b>Del</b> - {@link GameArtFileManager#deleteAll}.</li>
 * </ul>
 *
 * Carries the Swing bug-fixes forward: fixed preview dimensions (never the live
 * node size), so stepping through images can't make the window creep.
 */
public class GameImageController implements FxScreens.StageAware, ImageSelectListener, ImageChangedListener {

    private static final int[] BG_PREVIEW    = {380, 170};
    private static final int[] DISC_PREVIEW  = {70, 70};
    private static final int[] SCR_PREVIEW   = {300, 170};
    private static final int[] SPINE_PREVIEW = {20, 260};
    private static final int[] LOGO_PREVIEW  = {195, 93};

    private static String imagesDir() {
        return PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator
                + "data" + File.separator + "images" + File.separator;
    }
    private static final String NO_IMAGE_BACKGROUND = imagesDir() + "No Image Background.png";
    private static final String NO_IMAGE_SCREENSHOT = imagesDir() + "No Image Screenshot.png";
    private static final String NO_IMAGE_DISC       = imagesDir() + "No Image Disc.png";

    @FXML private ImageView covView, cov2View, spineView, logoView, discView, scr1View, scr2View, bgView;
    @FXML private TextField gameNameField, gameNumberField;

    private Stage stage;
    private String console;
    private int[] coverPreview;
    private int[] spinePreview;
    private String noImageCover;
    private final List<Game> gameList = new ArrayList<>();
    private int currentListIndex;
    private GameImageSelectorController selectorController;

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setOnHidden(e -> PopsGameManager.callbackToUpdateGUIGameList(null, currentListIndex));
    }

    /** Called from the façade before the window is shown. {@code console} is "PS1" or "PS2". */
    void init(String console, int gameIndex) {
        this.console = console;
        boolean ps1 = "PS1".equals(console);
        this.coverPreview = ps1 ? new int[] {130, 160} : new int[] {130, 210};
        // Decode hint only - the on-screen size of every preview comes from the
        // fixed pane box in the FXML (the ImageView fit is bound to it there).
        this.spinePreview = new int[] {SPINE_PREVIEW[0], coverPreview[1]};
        this.noImageCover = imagesDir() + (ps1 ? "No Image Cover.png" : "No Image Cover PS2.png");
        this.currentListIndex = gameIndex;

        List<Game> src = ps1 ? GameListManager.getGameListPS1() : GameListManager.getGameListPS2();
        if (src != null) {
            gameList.addAll(src);
        }

        refresh();
    }

    private void refresh() {
        Game game = gameList.get(currentListIndex);
        gameNameField.setText(game.getGameName() + "  :  " + game.getGameID());
        gameNumberField.setText("[" + (currentListIndex + 1) + "/" + gameList.size() + "]");
        displayGameImages();
    }

    private void displayGameImages() {
        Game game = gameList.get(currentListIndex);
        setView(covView,  GameArtFileManager.resolve(game, "_COV"),  noImageCover,        coverPreview);
        setView(cov2View, GameArtFileManager.resolve(game, "_COV2"), noImageCover,        coverPreview);
        setView(bgView,   GameArtFileManager.resolve(game, "_BG"),   NO_IMAGE_BACKGROUND, BG_PREVIEW);
        setView(discView, GameArtFileManager.resolve(game, "_ICO"),  NO_IMAGE_DISC,       DISC_PREVIEW);
        setView(scr1View, GameArtFileManager.resolve(game, "_SCR"),  NO_IMAGE_SCREENSHOT, SCR_PREVIEW);
        setView(scr2View, GameArtFileManager.resolve(game, "_SCR2"), NO_IMAGE_SCREENSHOT, SCR_PREVIEW);
        // Spine (LAB) / Logo (LGO): no placeholder art, so clear the view when absent.
        setView(spineView, GameArtFileManager.resolve(game, "_LAB"), null, spinePreview);
        setView(logoView,  GameArtFileManager.resolve(game, "_LGO"), null, LOGO_PREVIEW);
    }

    private static void setView(ImageView view, File file, String fallbackPath, int[] size) {
        String uri = null;
        if (file != null && file.isFile()) {
            uri = file.toURI().toString();
        } else if (fallbackPath != null && new File(fallbackPath).isFile()) {
            uri = new File(fallbackPath).toURI().toString();
        }
        view.setImage(uri == null ? null : new Image(uri, size[0], size[1], true, true));
    }

    // --- game navigation ---------------------------------------------------

    @FXML
    private void onPrev() {
        if (currentListIndex > 0) {
            currentListIndex--;
            refresh();
        }
    }

    @FXML
    private void onNext() {
        if (currentListIndex < gameList.size() - 1) {
            currentListIndex++;
            refresh();
        }
    }

    // --- per-pane buttons (userData on the button = the cover type) --------

    @FXML
    private void onFile(javafx.event.ActionEvent e) {
        String coverType = (String) ((Node) e.getSource()).getUserData();
        Game game = gameList.get(currentListIndex);
        Thread t = new Thread(() -> {
            try {
                // manualImageSelection() opens a Swing JFileChooser and writes the
                // rescaled result into ART/ - keep it on the EDT, ignore its AWT
                // return value, then re-read the file it just wrote.
                javax.swing.SwingUtilities.invokeAndWait(() ->
                        PopsGameManager.manualImageSelection(coverType, game.getGameName(), game.getGameID()));
            } catch (Exception ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
            Platform.runLater(this::displayGameImages);
        }, "fx-game-art-file");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onAuto(javafx.event.ActionEvent e) {
        String coverType = (String) ((Node) e.getSource()).getUserData();
        // Screenshot 2's "Auto" reuses the _SCR listing, second entry - as in the Swing version.
        if ("_SCR2".equals(coverType)) {
            getImageFromServer("_SCR", "_SCR2", 1);
        } else {
            getImageFromServer(coverType, coverType, 0);
        }
    }

    @FXML
    private void onDelete(javafx.event.ActionEvent e) {
        String coverType = (String) ((Node) e.getSource()).getUserData();
        GameArtFileManager.deleteAll(gameList.get(currentListIndex), coverType);
        displayGameImages();
    }

    // --- backend image fetch --------------------------------------------------

    private void getImageFromServer(String coverType, String coverPath, int startIndex) {
        Game game = gameList.get(currentListIndex);
        String region = PopsGameManager.determineGameRegion(game.getGameID().split("_")[0]);
        Thread t = new Thread(() -> {
            BackendClient api = PopsGameManager.newBackendClient();
            int count = api.getImagesAvailableOnServer(game, region, game.getGameID(), game.getGameName(), coverType, false);
            if (count <= 0) {
                Platform.runLater(() -> warn("There is no image file available in the database for this game.", " No ART Available!"));
                return;
            }
            api.getImageFromServer(game, region, game.getGameID(), game.getGameName(), coverType, coverPath, startIndex, false);
            File image = GameArtFileManager.resolve(game, coverPath);
            if (image != null) {
                Platform.runLater(() -> openSelector(coverPath, image, count, startIndex + 1));
            }
        }, "fx-game-art-auto");
        t.setDaemon(true);
        t.start();
    }

    private void getNextImageFromServer(String coverType, int currentImageNumber) {
        Game game = gameList.get(currentListIndex);
        String region = PopsGameManager.determineGameRegion(game.getGameID().split("_")[0]);
        Thread t = new Thread(() -> {
            BackendClient api = PopsGameManager.newBackendClient();
            api.getImageFromServer(game, region, game.getGameID(), game.getGameName(),
                    coverType, coverType, currentImageNumber - 1, false);
            File image = GameArtFileManager.resolve(game, coverType);
            if (image != null) {
                Platform.runLater(() -> {
                    if (selectorController != null) {
                        selectorController.updateImage(image);
                    }
                });
            }
        }, "fx-game-art-next");
        t.setDaemon(true);
        t.start();
    }

    private void openSelector(String coverPath, File image, int count, int currentImageNumber) {
        GameImageSelectorScreen.open(this, this, coverPath, console, image, count, currentImageNumber,
                c -> this.selectorController = c);
    }

    // --- ImageSelectListener / ImageChangedListener (from the selector) -------

    @Override
    public void imageSelected(String coverType, File image) {
        // The selector has either kept the downloaded file or deleted it; either way
        // just re-read the ART directory so the preview reflects what's on disk.
        if (Platform.isFxApplicationThread()) {
            displayGameImages();
        } else {
            Platform.runLater(this::displayGameImages);
        }
    }

    @Override
    public void imageChanged(String coverType, int currentImageNumber) {
        getNextImageFromServer(coverType, currentImageNumber);
    }

    private void warn(String message, String title) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle(title);
        if (stage != null) {
            alert.initOwner(stage);
        }
        alert.showAndWait();
    }
}

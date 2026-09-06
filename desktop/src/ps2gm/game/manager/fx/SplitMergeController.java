package ps2gm.game.manager.fx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.PopsGameManager;
import ps2gm.game.manager.SplitMergeProgress;
import ps2gm.game.manager.USBUtil;

/**
 * Controller for {@code SplitMergeScreen.fxml} - split a PS2 ISO into 1 GB UL parts
 * or merge them back. The actual work runs on a {@link USBUtil} background thread
 * and reports here through {@link SplitMergeProgress}; this marshals every callback
 * onto the FX thread. The window can't be closed while a task is running.
 */
public class SplitMergeController implements FxScreens.StageAware, SplitMergeProgress {

    @FXML private Label gameNameLabel;
    @FXML private Label partsLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Button processButton;

    private Stage stage;
    private Game game;
    private String mode;               // "Split" | "Merge"
    private volatile boolean working = false;
    private long rangeMin = 0, rangeMax = 0;

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(mode + " PS2 Game");
        stage.setOnCloseRequest(e -> {
            if (working) {
                e.consume();
                new Alert(Alert.AlertType.WARNING,
                        "This window will automatically close once the current operation has completed.").showAndWait();
            }
        });
    }

    /** Called from the façade before the window is shown. */
    void setGame(Game game, String mode) {
        this.game = game;
        this.mode = mode;
        gameNameLabel.setText(game.getGameName());
        processButton.setText(mode);
    }

    @FXML
    private void onProcess() {
        if (working) {
            return;
        }
        working = true;
        processButton.setDisable(true);
        try {
            if ("Split".equals(mode)) {
                USBUtil.splitFile(this, game);
            } else {
                USBUtil.joinFiles(this, game.getGameID());
            }
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
            working = false;
            processButton.setDisable(false);
        }
    }

    // ---- SplitMergeProgress: USBUtil reports from its worker thread ----
    @Override
    public void setPartsText(String text) {
        Platform.runLater(() -> partsLabel.setText(text));
    }

    @Override
    public void setProgressRange(long min, long max) {
        Platform.runLater(() -> {
            rangeMin = min;
            rangeMax = max;
        });
    }

    @Override
    public void setProgress(long value) {
        Platform.runLater(() -> {
            long span = rangeMax - rangeMin;
            progressBar.setProgress(span > 0 ? (double) (value - rangeMin) / span : ProgressBar.INDETERMINATE_PROGRESS);
        });
    }

    @Override
    public void finished() {
        Platform.runLater(() -> {
            working = false;
            if (stage != null) {
                stage.close();
            }
        });
    }
}

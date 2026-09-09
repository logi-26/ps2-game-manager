package ps2gm.game.manager.fx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import ps2gm.game.manager.ConversionProgress;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.IsoZsoConverter;

/**
 * Controller for {@code ConvertIsoZsoScreen.fxml} - converts a PS2 game file between
 * .ISO and .ZSO. The conversion starts as soon as the window opens (see {@link #init})
 * and reports back through {@link ConversionProgress} from {@link IsoZsoConverter}'s
 * background thread; every callback is marshalled onto the FX thread here. The window
 * can't be closed while the conversion is still running.
 */
public class ConvertIsoZsoController implements FxScreens.StageAware, ConversionProgress {

    @FXML private Label gameNameLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Button closeButton;

    private Stage stage;
    private boolean toZso;
    private volatile boolean working = true;
    private long rangeMin = 0, rangeMax = 0;

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(e -> {
            if (working) {
                e.consume();
                new Alert(Alert.AlertType.WARNING,
                        "This window will automatically close once the conversion has completed.").showAndWait();
            }
        });
    }

    /** Called from the façade before the window is shown; starts the conversion immediately. */
    void init(Game game, boolean toZso) {
        this.toZso = toZso;
        gameNameLabel.setText(game.getGameName());
        statusLabel.setText((toZso ? "Converting to ZSO" : "Converting to ISO") + "...");
        closeButton.setDisable(true);
        IsoZsoConverter.start(this, game, toZso);
    }

    @FXML
    private void onClose() {
        if (!working && stage != null) {
            stage.close();
        }
    }

    // ---- ConversionProgress: IsoZsoConverter reports from its worker thread ----

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
            double fraction = span > 0 ? (double) (value - rangeMin) / span : 0;
            progressBar.setProgress(fraction);
            statusLabel.setText(String.format("%s... %d%%",
                    toZso ? "Converting to ZSO" : "Converting to ISO", Math.round(fraction * 100)));
        });
    }

    @Override
    public void finished(boolean success, String errorMessage) {
        Platform.runLater(() -> {
            working = false;
            closeButton.setDisable(false);
            if (success) {
                progressBar.setProgress(1.0);
                statusLabel.setText("Done - converted to " + (toZso ? "ZSO" : "ISO") + ".");
            } else {
                statusLabel.setText("Conversion failed" + (errorMessage != null ? ": " + errorMessage : "."));
            }
        });
    }
}

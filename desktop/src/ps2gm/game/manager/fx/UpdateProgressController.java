package ps2gm.game.manager.fx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import ps2gm.game.manager.AppUpdateProgress;

/** Controller for {@code UpdateProgressScreen.fxml} - a small window showing app-update download/verify/apply progress. */
public class UpdateProgressController implements AppUpdateProgress, FxScreens.StageAware {

    @FXML private Label phaseLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;

    private Stage stage;

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(" Updating PS2GM");
    }

    @Override
    public void setPhase(String phase) {
        Platform.runLater(() -> phaseLabel.setText(phase));
    }

    @Override
    public void setProgress(double fraction) {
        Platform.runLater(() -> progressBar.setProgress(fraction < 0 ? ProgressBar.INDETERMINATE_PROGRESS : fraction));
    }

    @Override
    public void setStatusText(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    void close() {
        Platform.runLater(() -> stage.close());
    }
}

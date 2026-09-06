package ps2gm.game.manager.fx;

import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ps2gm.game.manager.PopsGameManager;

/**
 * Opens an FXML-backed screen as its own {@code Stage}, from the Swing app.
 *
 * Call from the Swing EDT where the old {@code JDialog} was constructed. This boots
 * the FX toolkit if needed, loads {@code <fxmlResource>} (relative to this package),
 * hands the controller to {@code init} for any runtime wiring, and shows the window
 * on the FX thread. Not modal against the Swing window - fine for the informational
 * dialogs ported first.
 */
final class FxScreens {

    private FxScreens() {}

    /** Implemented by a controller that needs its own {@link Stage} (to veto close, set size, etc.). */
    interface StageAware {
        void stageReady(Stage stage);
    }

    static <C> void open(String fxmlResource, String title, boolean resizable, Consumer<C> init) {
        FxRuntime.ensureStarted();
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(FxScreens.class.getResource(fxmlResource));
                Parent root = loader.load();
                Object controller = loader.getController();
                if (init != null) {
                    @SuppressWarnings("unchecked")
                    C typed = (C) controller;
                    init.accept(typed);
                }
                Stage stage = new Stage();
                stage.setTitle(title);
                stage.setResizable(resizable);
                stage.setScene(new Scene(root));
                stage.centerOnScreen();
                if (controller instanceof StageAware sa) {
                    sa.stageReady(stage);
                }
                stage.show();
            } catch (Exception ex) {
                PopsGameManager.displayErrorMessageDebug("FX screen '" + fxmlResource + "' failed to open: " + ex);
            }
        });
    }
}

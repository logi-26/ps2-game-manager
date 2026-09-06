package ps2gm.game.manager.fx;

import java.util.concurrent.CountDownLatch;
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
        Platform.runLater(() -> build(fxmlResource, title, resizable, init, null));
    }

    /**
     * Like {@link #open} but blocks the calling thread until the window is closed -
     * for the few flows that were modal against the whole app (a manager pausing a
     * scan to let the user fix things). Must NOT be called from the FX thread.
     */
    static <C> void openModal(String fxmlResource, String title, boolean resizable, Consumer<C> init) {
        if (Platform.isFxApplicationThread()) {
            throw new IllegalStateException("openModal must not be called from the FX thread");
        }
        FxRuntime.ensureStarted();
        CountDownLatch closed = new CountDownLatch(1);
        Platform.runLater(() -> build(fxmlResource, title, resizable, init, closed));
        try {
            closed.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static <C> void build(String fxmlResource, String title, boolean resizable, Consumer<C> init, CountDownLatch closed) {
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
            if (closed != null) {
                stage.setOnHidden(e -> closed.countDown());
            }
            if (controller instanceof StageAware sa) {
                sa.stageReady(stage);
            }
            stage.show();
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug("FX screen '" + fxmlResource + "' failed to open: " + ex);
            if (closed != null) {
                closed.countDown();
            }
        }
    }
}

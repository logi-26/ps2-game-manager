package ps2gm.game.manager.fx;

import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import ps2gm.game.manager.PopsGameManager;

/**
 * A themed, dark-title-bar-aware error dialog usable from anywhere in the app -
 * including the earliest startup code (e.g. {@code XMLFileManager}'s settings
 * load), which runs before {@code MainApp}/{@code MainController} exist and so
 * can't go through {@code PopsGameManager}'s {@code DialogCallback} (not
 * registered yet) or assume the FX toolkit or the saved theme are already up.
 * Blocks the calling thread until dismissed, matching the {@code
 * JOptionPane.showMessageDialog} call this replaces.
 */
public final class FxAlerts {

    private FxAlerts() {}

    public static void showErrorBlocking(String message, String title) {
        FxRuntime.ensureStarted();
        if (Platform.isFxApplicationThread()) {
            show(message, title);
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            show(message, title);
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void show(String message, String title) {
        // Applying the theme here (not just relying on MainApp.start() to have done
        // it) covers the one real early-failure case this exists for: settings
        // haven't loaded yet, so PopsGameManager.getThemeName() is still whatever
        // default it starts with - safe to call again later, it's idempotent.
        Themes.apply(PopsGameManager.getThemeName());
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.setTitle(title);
        WindowsDarkTitleBar.apply(alert);
        alert.showAndWait();
    }
}

package ps2gm.game.manager.fx;

import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;

/**
 * Boots the JavaFX toolkit once, on demand, from the still-Swing app.
 *
 * The UI is being migrated Swing -> JavaFX one screen at a time, so for now both
 * toolkits run in the same process: the Swing MainScreen stays the entry point and
 * opens JavaFX windows as separate {@code Stage}s. This class owns that one-time
 * {@code Platform.startup()} and keeps the FX runtime alive after its last window
 * closes.
 */
final class FxRuntime {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private FxRuntime() {}

    /** Ensure the FX toolkit is running. Safe to call repeatedly, from any thread. */
    static void ensureStarted() {
        if (STARTED.compareAndSet(false, true)) {
            Platform.startup(() -> { });
            // Without this the FX runtime shuts down when the last Stage closes, and
            // the next screen we open would fail.
            Platform.setImplicitExit(false);
        }
    }
}

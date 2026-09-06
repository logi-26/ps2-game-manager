package ps2gm.game.manager.fx;

import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;

/**
 * Owns the one-time JavaFX toolkit bootstrap.
 *
 * The app now boots through {@link MainApp} ({@code Application.launch}), which
 * starts the toolkit itself and calls {@link #markStarted()} - after that
 * {@link #ensureStarted()} is a no-op. The lazy {@code Platform.startup()} path is
 * only still here as a fallback for the throwaway {@code scratchpad/FxSmoke*}
 * harnesses that drive a screen façade without an {@code Application}.
 */
final class FxRuntime {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private FxRuntime() {}

    /** Called by {@link MainApp}: the toolkit is already up, don't start it again. */
    static void markStarted() {
        STARTED.set(true);
    }

    /** Ensure the FX toolkit is running. Safe to call repeatedly, from any thread. */
    static void ensureStarted() {
        if (STARTED.compareAndSet(false, true)) {
            try {
                Platform.startup(() -> { });
            } catch (IllegalStateException alreadyRunning) {
                // Already started elsewhere (e.g. an Application.launch we didn't see) - fine.
            }
            // Keep the runtime alive between windows when there's no always-open main
            // Stage (the smoke harnesses). MainApp leaves implicitExit at its default
            // so closing the real main window exits the app.
            Platform.setImplicitExit(false);
        }
    }
}

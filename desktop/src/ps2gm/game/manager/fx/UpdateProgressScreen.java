package ps2gm.game.manager.fx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opens the update progress window and hands back its controller
 * synchronously, so the background thread driving the download/verify/apply
 * flow (see {@code MainController.checkForUpdate}) can feed it progress
 * directly - {@link FxScreens#open} itself is fire-and-forget from a
 * background thread, so a latch (the same pattern {@link
 * FxScreens#openModal} already uses) bridges the two.
 */
final class UpdateProgressScreen {

    private UpdateProgressScreen() {}

    static UpdateProgressController open() {
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<UpdateProgressController> controllerRef = new AtomicReference<>();
        FxScreens.open("UpdateProgressScreen.fxml", " Updating PS2GM", false, (UpdateProgressController c) -> {
            controllerRef.set(c);
            ready.countDown();
        });
        try {
            ready.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return controllerRef.get();
    }
}

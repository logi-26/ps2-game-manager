package ps2gm.game.manager.fx;

import ps2gm.game.manager.Console;

/**
 * JavaFX replacement for the Swing {@code BatchDownloadScreenPS1 / PS2}
 * (fetch all missing art / config files).
 */
public final class BatchDownloadScreen {

    private BatchDownloadScreen() {}

    public static void open(Console console) {
        FxScreens.open("BatchDownloadScreen.fxml", " Batch Downloads", false,
                (BatchDownloadController c) -> c.init(console));
    }
}

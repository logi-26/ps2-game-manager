package ps2gm.game.manager.fx;

/**
 * JavaFX replacement for the Swing {@code BatchDownloadScreenPS1 / PS2}
 * (fetch all missing art / config files). {@code console} is "PS1" or "PS2".
 */
public final class BatchDownloadScreen {

    private BatchDownloadScreen() {}

    public static void open(String console) {
        FxScreens.open("BatchDownloadScreen.fxml", " Batch Downloads", true,
                (BatchDownloadController c) -> c.init(console));
    }
}

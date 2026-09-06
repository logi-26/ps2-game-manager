package ps2gm.game.manager.fx;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.SyncFileScreen} - a
 * two-pane local/remote file browser for moving ART / CFG / CHT / VMC files (and
 * PS1 cheat folders) between the local {@code hdd/} directories and the console
 * over FTP.
 */
public final class SyncFileScreen {

    private SyncFileScreen() {}

    public static void open() {
        FxScreens.open("SyncFileScreen.fxml", " Transfer Files", false, (SyncFileController c) -> c.init());
    }
}

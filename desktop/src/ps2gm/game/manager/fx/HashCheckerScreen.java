package ps2gm.game.manager.fx;

import java.io.File;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.HashCheckerScreen}
 * (the "MD5 Checker"). Call {@link #open} where the old dialog was constructed.
 */
public final class HashCheckerScreen {

    private HashCheckerScreen() {}

    public static void open(File gameFile) {
        FxScreens.open("HashCheckerScreen.fxml", " MD5 Checker", false,
                (HashCheckerController c) -> c.setGameFile(gameFile));
    }
}

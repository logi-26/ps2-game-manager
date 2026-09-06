package ps2gm.game.manager.fx;

import java.io.File;
import java.util.List;

/**
 * JavaFX replacement for the Swing {@code GameRenamingScreenPS1 / PS2} (fix
 * incorrectly-named VCD/ISO files). Opened modally from {@code GameListManager}
 * mid-scan, so these block the caller until the window closes.
 */
public final class GameRenamingScreen {

    private GameRenamingScreen() {}

    public static void openPS1(List<File> invalidGames) {
        open("PS1", invalidGames);
    }

    public static void openPS2(List<File> invalidGames) {
        open("PS2", invalidGames);
    }

    private static void open(String console, List<File> invalidGames) {
        FxScreens.openModal("GameRenamingScreen.fxml", " Rename Games", false,
                (GameRenamingController c) -> c.init(console, invalidGames));
    }
}

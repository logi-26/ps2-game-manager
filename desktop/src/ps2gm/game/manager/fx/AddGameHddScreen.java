package ps2gm.game.manager.fx;

import java.io.File;
import ps2gm.game.manager.Console;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.AddGameHDDScreenPS1} /
 * {@code AddGameHDDScreenPS2} (upload a game, or a folder of games in batch mode,
 * to the console's internal HDD).
 */
public final class AddGameHddScreen {

    private AddGameHddScreen() {}

    public static void open(Console console, boolean batchMode, String selectedPath, File selectedFile) {
        if (console == Console.PS1) {
            FxScreens.open("AddGameHddPs1Screen.fxml",
                    batchMode ? "Add PS1 Game to HDD - Batch Mode" : "Add PS1 Game to HDD", false,
                    (AddGameHddPs1Controller c) -> c.init(batchMode, selectedPath, selectedFile));
        } else {
            FxScreens.open("AddGameHddPs2Screen.fxml",
                    batchMode ? "Add PS2 Game to HDD - Batch Mode" : "Add PS2 Game to HDD", false,
                    (AddGameHddPs2Controller c) -> c.init(batchMode, selectedPath, selectedFile));
        }
    }
}

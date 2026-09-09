package ps2gm.game.manager.fx;

import java.io.File;
import java.util.List;
import ps2gm.game.manager.Console;

/** Opens {@code BadGameListScreen.fxml} - see {@link BadGameListController}. */
public final class BadGameListScreen {

    private BadGameListScreen() {}

    public static void open(Console console, String message, List<File> badFiles) {
        FxScreens.open("BadGameListScreen.fxml", " Unable to Detect Game ID - " + console, true,
                (BadGameListController c) -> c.init(console, message, badFiles));
    }
}

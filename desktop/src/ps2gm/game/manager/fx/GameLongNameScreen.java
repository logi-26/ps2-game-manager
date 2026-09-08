package ps2gm.game.manager.fx;

import java.util.List;
import ps2gm.game.manager.Console;
import ps2gm.game.manager.Game;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.GameLongNameScreen}
 * (rename games with a title over 32 characters).
 */
public final class GameLongNameScreen {

    private GameLongNameScreen() {}

    public static void open(Console console, List<Game> longNameGames) {
        FxScreens.open("GameLongNameScreen.fxml", " Long " + console + " Game Names", false,
                (GameLongNameController c) -> c.init(console, longNameGames));
    }
}

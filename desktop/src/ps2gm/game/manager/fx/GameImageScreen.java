package ps2gm.game.manager.fx;

import ps2gm.game.manager.Console;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.GameImageScreenPS1} /
 * {@code GameImageScreenPS2} (the per-game ART manager).
 */
public final class GameImageScreen {

    private GameImageScreen() {}

    public static void open(Console console, int gameIndex) {
        String title = console == Console.PS1
                ? " Manage PlayStation 1 Game ART"
                : " Manage PlayStation 2 Game ART";
        FxScreens.open("GameImageScreen.fxml", title, false,
                (GameImageController c) -> c.init(console, gameIndex));
    }
}

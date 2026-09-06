package ps2gm.game.manager.fx;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.GameConfigScreen}
 * (the per-game OPL {@code .cfg} editor). Works for the console the main screen
 * is currently in ("PS1" or "PS2"); the controller checks for itself whether a
 * config file already exists.
 */
public final class GameConfigScreen {

    private GameConfigScreen() {}

    public static void open(int gameIndex) {
        FxScreens.open("GameConfigScreen.fxml", " Manage Game Config", true,
                (GameConfigController c) -> c.init(gameIndex));
    }
}

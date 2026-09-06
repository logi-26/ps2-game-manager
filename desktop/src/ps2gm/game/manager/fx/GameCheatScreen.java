package ps2gm.game.manager.fx;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.GameCheatScreen}
 * (the per-game cheat-file editor). Works for the console the main screen is
 * currently in ("PS1" or "PS2").
 */
public final class GameCheatScreen {

    private GameCheatScreen() {}

    public static void open(int gameIndex) {
        FxScreens.open("GameCheatScreen.fxml", " Manage Game Cheats", false,
                (GameCheatController c) -> c.init(gameIndex));
    }
}

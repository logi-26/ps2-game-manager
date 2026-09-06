package ps2gm.game.manager.fx;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.GameImageScreenPS1} /
 * {@code GameImageScreenPS2} (the per-game ART manager). {@code console} is
 * "PS1" or "PS2".
 */
public final class GameImageScreen {

    private GameImageScreen() {}

    public static void open(String console, int gameIndex) {
        String title = "PS1".equals(console)
                ? " Manage PlayStation 1 Game ART"
                : " Manage PlayStation 2 Game ART";
        FxScreens.open("GameImageScreen.fxml", title, false,
                (GameImageController c) -> c.init(console, gameIndex));
    }
}

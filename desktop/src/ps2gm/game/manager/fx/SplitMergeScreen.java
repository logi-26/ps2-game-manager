package ps2gm.game.manager.fx;

import ps2gm.game.manager.Game;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.SplitMergeScreen}.
 * {@code mode} is "Split" or "Merge".
 */
public final class SplitMergeScreen {

    private SplitMergeScreen() {}

    public static void open(Game game, String mode) {
        FxScreens.open("SplitMergeScreen.fxml", mode + " PS2 Game", false,
                (SplitMergeController c) -> c.setGame(game, mode));
    }
}

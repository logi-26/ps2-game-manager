package ps2gm.game.manager.fx;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.GameVMCScreen}
 * ("Download Game VMC Files").
 */
public final class GameVmcScreen {

    private GameVmcScreen() {}

    public static void open(int gameIndex) {
        FxScreens.open("GameVmcScreen.fxml", " Download Game VMC Files", true,
                (GameVmcController c) -> c.initialiseGUI(gameIndex));
    }
}

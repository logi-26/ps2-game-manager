package ps2gm.game.manager.fx;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.SetModeScreen}
 * (the OPL-location / mode picker). Opened modally: {@link #open()} blocks the
 * caller until the window closes, as the Swing modal {@code JDialog} did - so
 * first-launch still can't proceed until a mode is chosen or the user exits.
 * Must be called from the Swing EDT, not the FX thread.
 */
public final class SetModeScreen {

    private SetModeScreen() {}

    public static void open() {
        FxScreens.openModal("SetModeScreen.fxml", " Select Mode", false, (SetModeController c) -> c.init());
    }
}

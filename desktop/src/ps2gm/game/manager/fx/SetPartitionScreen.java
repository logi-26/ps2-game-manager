package ps2gm.game.manager.fx;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.SetPartitionScreen}
 * ("Set Remote File Locations"). Call {@link #open} where the old dialog was
 * constructed; it persists to settings.xml on "Set", nothing on "Cancel".
 */
public final class SetPartitionScreen {

    private SetPartitionScreen() {}

    public static void open() {
        FxScreens.open("SetPartitionScreen.fxml", " Set Remote File Locations", false,
                (SetPartitionController c) -> c.loadCurrent());
    }
}

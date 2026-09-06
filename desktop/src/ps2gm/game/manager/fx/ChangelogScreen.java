package ps2gm.game.manager.fx;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.ChangelogScreen}.
 * Content lives in {@code changelog.txt} next to this class.
 */
public final class ChangelogScreen {

    private ChangelogScreen() {}

    public static void show() {
        FxScreens.open("ChangelogScreen.fxml", "Changelog", true, (ChangelogController c) -> { });
    }
}

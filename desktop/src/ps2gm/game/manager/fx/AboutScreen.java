package ps2gm.game.manager.fx;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.AboutScreen}.
 *
 * First screen ported in the Swing -> JavaFX migration. Call {@link #show} from the
 * Swing EDT exactly where the old dialog was constructed.
 */
public final class AboutScreen {

    private AboutScreen() {}

    public static void show(String title, String compiledDate) {
        FxScreens.open("AboutScreen.fxml", "About", false,
                (AboutController c) -> c.setContent(title, compiledDate));
    }
}

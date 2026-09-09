package ps2gm.game.manager.fx;

import java.util.List;
import javafx.scene.image.Image;

/**
 * The app's window/taskbar icon, at the sizes Windows actually picks between
 * (title bar, taskbar, Alt-Tab). Shared by every {@code Stage} - see
 * {@code MainApp} (primary window) and {@code FxScreens} (every secondary
 * screen) - so no window is left showing the default Java icon.
 *
 * Source: desktop/icons/ps2gm.ico (also used as-is for the packaged .exe's
 * own icon via package.ps1 --icon) - these PNGs are the same artwork, just
 * the sizes JavaFX's image-only API needs.
 */
final class AppIcons {

    private AppIcons() {}

    static final List<Image> ALL = List.of(
            new Image(AppIcons.class.getResource("icons/app-icon-16.png").toExternalForm()),
            new Image(AppIcons.class.getResource("icons/app-icon-32.png").toExternalForm()),
            new Image(AppIcons.class.getResource("icons/app-icon-48.png").toExternalForm()),
            new Image(AppIcons.class.getResource("icons/app-icon-256.png").toExternalForm()));
}

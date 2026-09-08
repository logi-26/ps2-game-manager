package ps2gm.game.manager.fx;

import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javafx.application.Application;
import javafx.scene.Scene;

/**
 * The app's AtlantaFX themes, keyed by display name. One is the global JavaFX
 * user-agent stylesheet at a time ({@link #apply}); a small {@code app.css} rides
 * on top of it (via {@link #decorate}) for tweaks that aren't theme-specific.
 *
 * Replaces the old light/dark boolean - the choice is now a name persisted in
 * settings.xml ({@code <theme>}).
 */
public final class Themes {

    /** Used when settings.xml has no theme yet, or names one we don't ship. */
    public static final String DEFAULT = "Primer Light";

    private static final Map<String, Theme> REGISTRY = new LinkedHashMap<>();
    static {
        REGISTRY.put("Primer Light",    new PrimerLight());
        REGISTRY.put("Primer Dark",     new PrimerDark());
        REGISTRY.put("Nord Light",      new NordLight());
        REGISTRY.put("Nord Dark",       new NordDark());
        REGISTRY.put("Dracula",         new Dracula());
    }

    private static final String APP_CSS =
            Themes.class.getResource("app.css").toExternalForm();

    private Themes() {}

    /** Theme display names, in menu order. */
    public static Set<String> names() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /** A known theme name, or {@link #DEFAULT} if {@code name} is null/unknown. */
    public static String normalise(String name) {
        return name != null && REGISTRY.containsKey(name) ? name : DEFAULT;
    }

    /** True for the dark variants (kept in sync with the legacy darkmode flag). */
    public static boolean isDark(String name) {
        String n = normalise(name);
        return n.endsWith("Dark") || n.equals("Dracula");
    }

    /** Set {@code name} as the global JavaFX theme - re-styles every open window. */
    public static void apply(String name) {
        Application.setUserAgentStylesheet(REGISTRY.get(normalise(name)).getUserAgentStylesheet());
    }

    /** Add the shared {@code app.css} to a scene (idempotent). */
    public static void decorate(Scene scene) {
        if (scene != null && !scene.getStylesheets().contains(APP_CSS)) {
            scene.getStylesheets().add(APP_CSS);
        }
    }
}

package ps2gm.game.manager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

/**
 * Central place for colors that used to be hardcoded RGB literals repeated across every
 * screen's initComponents(). The structural ones (control backgrounds, the empty-image
 * "well" behind cover/screenshot previews) are read from the active Swing LookAndFeel
 * (FlatLaf) via UIManager, so a theme switch - light/dark, or any future FlatLaf theme -
 * is respected automatically instead of needing every screen's colors picked by hand.
 *
 * The "traffic light" status colors (compatibility list, cheat code categories) keep their
 * fixed, recognizable hues in both themes - they're bright/saturated enough to read on a
 * light or dark background already - but are routed through here too so they're declared
 * once instead of re-typed as a literal at every use site.
 */
final class AppTheme {

    private AppTheme() {}

    // Background for read-only "display" text fields (game title/ID/size etc.) - matches
    // what a real (editable) text field looks like in the active theme.
    static Color controlBackground() {
        return orFallback(UIManager.getColor("TextField.background"), Color.WHITE);
    }

    // Background for the empty-image "well" behind cover/screenshot previews before an
    // image loads - deliberately a shade off the surrounding panel so it still reads as
    // a distinct box in both light and dark themes.
    static Color imagePreviewBackground() {
        Color panel = orFallback(UIManager.getColor("Panel.background"), new Color(238, 238, 238));
        return isDark(panel) ? panel.brighter() : panel.darker();
    }

    // Background for a text field that's tinted gray to show its mode isn't currently selected
    // (SetModeScreen's SMB/USB/HDD path fields) - not disabled exactly, just de-emphasized.
    static Color disabledControlBackground() {
        return orFallback(UIManager.getColor("TextField.disabledBackground"), new Color(240, 240, 240));
    }

    // Status colors - same hue in every theme, deliberately not theme-derived.
    static Color statusGreen()     { return new Color(55, 170, 20); }
    static Color statusRed()       { return new Color(190, 35, 25); }
    static Color statusOrange()    { return new Color(218, 145, 30); }
    static Color statusOrangeAlt() { return new Color(226, 149, 15); } // GameCheatScreen's second orange shade
    static Color statusBlue()      { return new Color(38, 120, 190); }

    // The toolbar button glyphs (Delete/Download/Folder/Left/Right/Save/New Folder) are baked
    // as dark #333 PNGs by the NetBeans form and are near-invisible on a dark panel. For each
    // one there is a light-grey "<name> Dark.png" sibling. Walk a screen's component tree after
    // initComponents() and, when dark mode is active, swap every button whose icon resolves to
    // .../images/buttons/<name>.png over to its " Dark.png" variant. Done here (not in the
    // generated initComponents) so a future NetBeans form save can't drop it.
    private static final String BUTTONS_DIR = "/ps2gm/game/manager/images/buttons/";

    static void applyDarkButtonIcons(Container root) {
        if (!Boolean.TRUE.equals(PopsGameManager.getDarkMode()) || root == null) { return; }
        swapButtonIconsRecursively(root);
    }

    private static void swapButtonIconsRecursively(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton) { swapButtonIcon((AbstractButton) child); }
            if (child instanceof Container) { swapButtonIconsRecursively((Container) child); }
        }
    }

    private static void swapButtonIcon(AbstractButton button) {
        Icon icon = button.getIcon();
        if (!(icon instanceof ImageIcon)) { return; }

        String description = ((ImageIcon) icon).getDescription(); // set to the resource URL by new ImageIcon(URL)
        if (description == null || !description.contains(BUTTONS_DIR) || !description.endsWith(".png")
                || description.endsWith(" Dark.png") || description.endsWith("%20Dark.png")) {
            return;
        }

        String fileName;
        try {
            fileName = URLDecoder.decode(description.substring(description.lastIndexOf('/') + 1), StandardCharsets.UTF_8.name());
        } catch (Exception ex) {
            return; // unexpected encoding - leave the light icon in place
        }

        URL darkUrl = AppTheme.class.getResource(BUTTONS_DIR + fileName.substring(0, fileName.length() - 4) + " Dark.png");
        if (darkUrl != null) { button.setIcon(new ImageIcon(darkUrl)); }
    }

    private static Color orFallback(Color color, Color fallback) { return color != null ? color : fallback; }

    private static boolean isDark(Color color) {
        double luminance = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
        return luminance < 0.5;
    }
}

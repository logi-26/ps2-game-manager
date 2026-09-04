package oplpops.game.manager;

import java.awt.Color;
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

    private static Color orFallback(Color color, Color fallback) { return color != null ? color : fallback; }

    private static boolean isDark(Color color) {
        double luminance = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
        return luminance < 0.5;
    }
}

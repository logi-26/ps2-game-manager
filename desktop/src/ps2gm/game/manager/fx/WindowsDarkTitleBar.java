package ps2gm.game.manager.fx;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import ps2gm.game.manager.PopsGameManager;

/**
 * Colours a Stage's native title bar to match the app's theme, on Windows only.
 *
 * JavaFX has no cross-platform API for this - the title bar is drawn by the OS
 * (DWM), not by JavaFX, so it never follows a dark AtlantaFX theme the way the
 * window's own content does. Windows 10 (2004+) / 11 expose
 * {@code DwmSetWindowAttribute(DWMWA_USE_IMMERSIVE_DARK_MODE)} for exactly this;
 * there's no JDK or JavaFX wrapper for it, so this calls it directly via JNA.
 *
 * The window is found by its exact title text via {@code User32.FindWindow} -
 * simpler and more future-proof than reaching into JavaFX's unsupported/internal
 * {@code com.sun.glass.ui.Window} to get a native handle directly.
 */
final class WindowsDarkTitleBar {

    private WindowsDarkTitleBar() {}

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

    // DWMWA_USE_IMMERSIVE_DARK_MODE: 20 on the documented (Windows 10 2004+/11) API;
    // 19 on the earlier, undocumented Windows 10 1809-1909 builds that already had the
    // underlying support before Microsoft assigned the attribute its final ID.
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY = 19;

    // SetWindowPos flags: keep everything about the window as-is except force DWM to
    // recompute the non-client frame (title bar) - without this, the attribute change
    // below has no visible effect at all on an already-created window.
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_FRAMECHANGED = 0x0020;

    // ShowWindow codes for the hide/show cycle below.
    private static final int SW_HIDE = 0;
    private static final int SW_SHOW = 5;

    private interface Dwmapi extends StdCallLibrary {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class);
        int DwmSetWindowAttribute(HWND hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);
    }

    /** No-op off Windows. Call once the stage is showing and its title is final. */
    static void apply(Stage stage, boolean dark) {
        if (!WINDOWS) { return; }
        try {
            String title = stage.getTitle();
            HWND hwnd = User32.INSTANCE.FindWindow(null, title);
            if (hwnd == null && title != null) {
                hwnd = User32.INSTANCE.FindWindow(null, title.trim());
            }
            if (hwnd == null) { return; }
            IntByReference value = new IntByReference(dark ? 1 : 0);
            int result = Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, value, 4);
            if (result != 0) {
                result = Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY, value, 4);
            }
            if (result == 0) {
                User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0,
                        SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
                // SWP_FRAMECHANGED alone leaves the *active*-state caption still painted
                // light until the window loses and regains focus - a known DWM quirk.
                // A hide/show cycle forces the same full repaint immediately.
                User32.INSTANCE.ShowWindow(hwnd, SW_HIDE);
                User32.INSTANCE.ShowWindow(hwnd, SW_SHOW);
            }
        } catch (Throwable ex) {
            // Best-effort cosmetic only - never worth failing window creation over.
            PopsGameManager.displayErrorMessageDebug("Dark title bar not applied: " + ex);
        }
    }

    /**
     * Convenience for an {@code Alert}/{@code TextInputDialog}/etc: unlike a
     * {@code Stage} built through {@link FxScreens}, a {@code Dialog}'s own Stage
     * doesn't exist until it's shown, so there's no point after construction to
     * call {@link #apply(Stage, boolean)} directly - this hooks the same call onto
     * {@code setOnShown} instead. Call once, any time before {@code show()}/
     * {@code showAndWait()}.
     */
    static void apply(Dialog<?> dialog) {
        boolean dark = Themes.isDark(PopsGameManager.getThemeName());
        // A Dialog's WINDOW_SHOWN event fires from inside showAndWait()'s own nested
        // event loop as it's spinning up - running the hide/show repaint trick
        // synchronously right there (reentrantly, before that loop has settled) doesn't
        // reliably repaint the caption, even though the DWM attribute call itself
        // succeeds. Deferring one pulse lets it run on a clean iteration instead.
        dialog.setOnShown(e -> javafx.application.Platform.runLater(() ->
                apply((Stage) dialog.getDialogPane().getScene().getWindow(), dark)));
    }
}

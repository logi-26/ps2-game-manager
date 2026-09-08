package ps2gm.game.manager;

/**
 * Lets business-logic classes ask the UI layer to show a message or ask for
 * confirmation, without depending on a UI toolkit (JavaFX or Swing)
 * directly - the MyListener callback precedent, extended to dialogs.
 */
public interface DialogCallback {
    void info(String message, String title);
    void warn(String message, String title);
    void error(String message, String title);
    boolean confirm(String message, String title);

    /** Shows an informational message for about {@code seconds}, then auto-closes. Blocks the caller until it closes. */
    void infoTimed(String message, String title, int seconds);
}

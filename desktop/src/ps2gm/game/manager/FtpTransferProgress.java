package ps2gm.game.manager;

/**
 * UI-toolkit-agnostic progress sink for a game upload to the console - either over
 * FTP or via hdl_dump.
 *
 * The manager runs the transfer on a background thread and reports through this
 * interface; a JavaFX dialog implements it and is responsible for marshalling the
 * calls onto its own UI thread.
 */
public interface FtpTransferProgress {

    // Progress-bar upper bound for the current run (bytes for FTP, 100 for hdl_dump).
    void setProgressRange(int max);

    // Current progress value.
    void setProgress(int value);

    // Estimated time remaining (pre-formatted)
    void setTimeRemaining(String text);

    // Transfer speed (pre-formatted)
    void setUploadSpeed(String text);

    // Name of the game currently transferring.
    void setGameName(String text);

    // Batch position (e.g. 3/12)
    void setGameCounter(String text);

    // Mark whether a transfer is running (the window vetoes its own close while true).
    void setInProgress(boolean uploading);

    // All transfers are done - close the window.
    void closeWindow();
}

package ps2gm.game.manager;

/**
 * UI-toolkit-agnostic progress sink for a game upload to the console - either over
 * FTP ({@link MyFTPClient}, PS1 games) or via {@code hdl_dump} ({@link HDLDumpManager},
 * PS2 games).
 *
 * The manager runs the transfer on a background thread and reports through this
 * interface; a Swing dialog or a JavaFX dialog implements it and is responsible for
 * marshalling the calls onto its own UI thread. Mirrors {@link SplitMergeProgress}
 * but with the richer label surface these two managers drive (time remaining,
 * transfer speed, current game name, batch counter). Introduced so
 * {@code AddGameHDDScreenPS1/PS2} can move to JavaFX without the managers depending
 * on Swing.
 *
 * The {@code includeElfFile} flag the FTP path used to pull back off the Swing
 * screen is now passed to {@link MyFTPClient#addGameToPS2} as a method argument.
 */
public interface FtpTransferProgress {

    /** Progress-bar upper bound for the current run (bytes for FTP, 100 for hdl_dump). */
    void setProgressRange(int max);

    /** Current progress value, within {@code 0..}{@link #setProgressRange}. */
    void setProgress(int value);

    /** Estimated time remaining, pre-formatted (e.g. "02:15"). */
    void setTimeRemaining(String text);

    /** Transfer speed, pre-formatted (e.g. "812KB/sec"). */
    void setUploadSpeed(String text);

    /** Name of the game currently transferring. */
    void setGameName(String text);

    /** Batch position (e.g. "3/12"); a no-op in single-game mode. */
    void setGameCounter(String text);

    /** Mark whether a transfer is running (the window vetoes its own close while true). */
    void setInProgress(boolean uploading);

    /** All transfers are done - close the window. */
    void closeWindow();
}

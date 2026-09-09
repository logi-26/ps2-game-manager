package ps2gm.game.manager;

/**
 * UI-implemented progress callback for the download/verify/apply flow - see
 * {@code fx.UpdateProgressController}. Deliberately smaller than {@link
 * FtpTransferProgress}: that interface's shape (per-game name/counter,
 * upload speed) is built for multi-file batch transfers and would leave
 * most of it permanently unused for this single-zip flow.
 */
public interface AppUpdateProgress {
    /** One of "Downloading", "Verifying", "Preparing" - shown as a short status label. */
    void setPhase(String phase);
    /** 0.0-1.0, or a negative value for an indeterminate phase (e.g. verifying). */
    void setProgress(double fraction);
    void setStatusText(String text);
}

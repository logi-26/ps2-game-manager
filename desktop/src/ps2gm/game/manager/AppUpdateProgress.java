package ps2gm.game.manager;

/**
 * UI-implemented progress callback for the download/verify/apply flow - see
 */
public interface AppUpdateProgress {
    void setPhase(String phase);
    void setProgress(double fraction);
    void setStatusText(String text);
}

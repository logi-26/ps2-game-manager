package ps2gm.game.manager;

/* UI-toolkit-agnostic progress/completion callback for a background file conversion. */
public interface ConversionProgress {
    void setProgressRange(long min, long max);
    void setProgress(long value);
    void finished(boolean success, String errorMessage);
}

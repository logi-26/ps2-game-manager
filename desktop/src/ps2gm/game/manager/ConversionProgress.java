package ps2gm.game.manager;

/**
 * UI-toolkit-agnostic progress/completion callback for a background file
 * conversion - see {@link IsoZsoConverter}. Mirrors the shape of {@link
 * SplitMergeProgress}, but {@link #finished} carries an explicit success
 * flag and error message rather than being a bare "done" signal, since a
 * failed conversion must never be treated as if it had replaced the game
 * file.
 */
public interface ConversionProgress {
    void setProgressRange(long min, long max);
    void setProgress(long value);
    void finished(boolean success, String errorMessage);
}

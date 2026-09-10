package ps2gm.game.manager;

/**
 * UI-toolkit-agnostic progress sink for USBUtil's PS2 ISO split/merge tasks
 */
public interface SplitMergeProgress {

    void setPartsText(String text);

    // Set the progress-bar bounds for the current run
    void setProgressRange(long min, long max);

    // Set the current progress value
    void setProgress(long value);

    // The task has finished (or failed) - close the dialog
    void finished();
}

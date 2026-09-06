package ps2gm.game.manager;

/**
 * UI-toolkit-agnostic progress sink for {@link USBUtil}'s PS2 ISO split/merge tasks.
 *
 * {@code USBUtil} runs the work on a background thread and reports through this
 * interface; a Swing dialog or a JavaFX dialog implements it and is responsible for
 * marshalling the calls onto its own UI thread. Introduced so the split/merge screen
 * can be ported to JavaFX without {@code USBUtil} depending on Swing.
 */
public interface SplitMergeProgress {

    /** e.g. "2/5" - which part is being processed. */
    void setPartsText(String text);

    /** Set the progress-bar bounds for the current run. */
    void setProgressRange(long min, long max);

    /** Set the current progress value (within the last {@link #setProgressRange} bounds). */
    void setProgress(long value);

    /** The task has finished (or failed) - close the dialog. */
    void finished();
}

package ps2gm.game.manager;

import java.util.List;

/**
 * Facade for OPL's USB-Advance ("UL") game format: ul.cfg read/write, the
 * encrypted backup/regenerate flow, and background split/merge of PS2 ISOs
 * into/from 1GB fragments. See {@link UlCfgFileFormat}, {@link UlBackupStore},
 * {@link IsoSplitter} and {@link IsoMerger} for the actual implementations.
 */
public final class USBUtil {

    private USBUtil() {}

    /** Reads ul.cfg from the OPL directory into a list of UL games. */
    public static List<Game> readULCFG() {
        return UlCfgFileFormat.read();
    }

    /** Tries to regenerate ul.cfg from the ul-backup file + the fragments present in the OPL directory. */
    public static void regenerateULCFG() {
        UlBackupStore.regenerateULCFG();
    }

    /** Splits a PS2 ISO into 1GB USB-Advance fragments on a background thread. */
    public static void splitFile(SplitMergeProgress ui, Game game) {
        IsoSplitter.start(ui, game);
    }

    /** Joins a PS2 game's split fragments back into a single ISO on a background thread. */
    public static void joinFiles(SplitMergeProgress ui, String gameID) {
        IsoMerger.start(ui, gameID);
    }
}

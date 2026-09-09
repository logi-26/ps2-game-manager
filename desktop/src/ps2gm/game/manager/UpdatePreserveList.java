package ps2gm.game.manager;

import java.nio.file.Path;
import java.util.Set;

/**
 * Paths, relative to {@code PopsGameManager.getCurrentDirectory()}, that an
 * app update must never touch - user data and runtime state, not app code.
 *
 * This is the single point of truth for that guarantee: {@link
 * AppUpdateStager} enforces it directly in its copy logic (both stripping
 * these from the downloaded package as defense in depth, and positively
 * copying them forward from the live install), rather than trusting every
 * future release zip to have been built correctly by hand. Getting this
 * list wrong is the single most dangerous failure mode in the whole update
 * feature - {@code hdd/} in particular can be the user's entire game
 * library (not just a small cache file) when running in HDD_USB local mode.
 *
 * Kept in one file so a review of "what can an update ever delete" is one
 * short read, not a hunt across the updater's several stages.
 */
public final class UpdatePreserveList {

    private UpdatePreserveList() {}

    // Single-file paths, matched exactly (one path segment).
    private static final Set<String> PRESERVED_FILES = Set.of(
            "ps2gm-settings",
            "oplpops-settings", // legacy name, auto-migrated by XMLFileManager if still present
            "invalidGameList.txt",
            "invalidGameList",
            "ul-backup.txt",
            "ul-backup"
    );

    // Whole subtrees, matched by their first path segment - everything under them is preserved.
    private static final Set<String> PRESERVED_TREES = Set.of(
            "hdd" // the user's OPL folder in HDD_USB local mode - can be their entire game library
    );

    private static final String[] CUE2POPS_TOOL_DIR = {"lib", "data", "tools", "windows"};

    /** True if {@code relativeToCurrentDir} (or anything under it) must never be overwritten/deleted by an update. */
    public static boolean isPreserved(Path relativeToCurrentDir) {
        Path normalised = relativeToCurrentDir.normalize();
        if (normalised.getNameCount() == 0) {
            return false;
        }

        String first = normalised.getName(0).toString();
        if (normalised.getNameCount() == 1 && PRESERVED_FILES.contains(first)) {
            return true;
        }
        if (PRESERVED_TREES.contains(first)) {
            return true;
        }
        return isCue2PopsSelfUpdateArtifact(normalised);
    }

    // lib/data/tools/windows/cue2pops.exe (the file) and lib/data/tools/windows/backup/ (the
    // whole tree) - AppBootstrap.checkCue2Pops()/MyApiClient.getCue2PopsFromServer() already
    // self-update this one binary independently of the app itself.
    private static boolean isCue2PopsSelfUpdateArtifact(Path normalised) {
        if (normalised.getNameCount() < CUE2POPS_TOOL_DIR.length + 1) {
            return false;
        }
        for (int i = 0; i < CUE2POPS_TOOL_DIR.length; i++) {
            if (!normalised.getName(i).toString().equals(CUE2POPS_TOOL_DIR[i])) {
                return false;
            }
        }
        String next = normalised.getName(CUE2POPS_TOOL_DIR.length).toString();
        boolean isCue2PopsExe = next.equals("cue2pops.exe") && normalised.getNameCount() == CUE2POPS_TOOL_DIR.length + 1;
        boolean isBackupTree = next.equals("backup");
        return isCue2PopsExe || isBackupTree;
    }
}

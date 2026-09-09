package ps2gm.game.manager;

import java.io.File;
import java.nio.file.Path;

/**
 * Resolves the directory an update actually swaps ({@code installRoot}) and
 * where {@code getCurrentDirectory()} sits relative to it - the two differ
 * for the jpackage app-image, where the running jar lives at
 * {@code PS2GM/app/} but the thing an update replaces is the whole
 * {@code PS2GM/} folder (exe + app/ + the bundled runtime/ JRE).
 *
 * {@link UpdatePreserveList}'s paths (settings, hdd/, ...) are all expressed
 * relative to {@code getCurrentDirectory()}, since that's where the running
 * app actually reads/writes them - {@link #currentDirPath(String)} resolves
 * one of those back to an absolute path under {@code installRoot} regardless
 * of which distribution this is.
 */
public final class InstallLayout {

    private final DistributionType distributionType;
    private final File installRoot;
    private final String currentDirRelativeToInstallRoot;

    private InstallLayout(DistributionType distributionType, File installRoot, String currentDirRelativeToInstallRoot) {
        this.distributionType = distributionType;
        this.installRoot = installRoot;
        this.currentDirRelativeToInstallRoot = currentDirRelativeToInstallRoot;
    }

    public static InstallLayout resolve() {
        return resolve(DistributionType.detect(), PopsGameManager.getCurrentDirectory());
    }

    /** As {@link #resolve()}, but against an explicit type/directory - the testable form. */
    public static InstallLayout resolve(DistributionType type, String currentDir) {
        File currentDirFile = new File(currentDir == null ? "." : currentDir);

        if (type == DistributionType.WINDOWS_APP_IMAGE) {
            File parent = currentDirFile.getParentFile();
            return new InstallLayout(type, parent != null ? parent : currentDirFile, "app");
        }
        // JAR_BUNDLE and UNKNOWN both treat the current directory as the install root -
        // UNKNOWN never gets to apply an update anyway (see DistributionType), this is
        // just what "the current directory" means if anyone asks.
        return new InstallLayout(type, currentDirFile, "");
    }

    public DistributionType distributionType() {
        return distributionType;
    }

    public File installRoot() {
        return installRoot;
    }

    /** Absolute path for a location expressed relative to {@code getCurrentDirectory()} (e.g. "hdd" or "ps2gm-settings"). */
    public File currentDirPath(String relativeToCurrentDir) {
        File currentDir = currentDirRelativeToInstallRoot.isEmpty()
                ? installRoot
                : new File(installRoot, currentDirRelativeToInstallRoot);
        return relativeToCurrentDir.isEmpty() ? currentDir : new File(currentDir, relativeToCurrentDir);
    }

    /** Same mapping as {@link #currentDirPath(String)} but rooted at a different tree (e.g. the extracted update package). */
    public File currentDirPathUnder(File root, String relativeToCurrentDir) {
        File currentDir = currentDirRelativeToInstallRoot.isEmpty()
                ? root
                : new File(root, currentDirRelativeToInstallRoot);
        return relativeToCurrentDir.isEmpty() ? currentDir : new File(currentDir, relativeToCurrentDir);
    }

    /**
     * Converts a path expressed relative to {@code installRoot} into one relative to
     * {@code getCurrentDirectory()} - the direction {@link AppUpdateStager} needs to check an
     * entry from the (installRoot-shaped) extracted update package against {@link
     * UpdatePreserveList}. Returns null if the path doesn't fall under the current directory at
     * all (e.g. {@code PS2GM.exe} or {@code runtime/} on the app-image, which sit outside
     * {@code app/} and so can never match a preserve-list entry).
     */
    public Path toCurrentDirRelative(Path relativeToInstallRoot) {
        if (currentDirRelativeToInstallRoot.isEmpty()) {
            return relativeToInstallRoot;
        }
        Path prefix = Path.of(currentDirRelativeToInstallRoot);
        return relativeToInstallRoot.startsWith(prefix) ? prefix.relativize(relativeToInstallRoot) : null;
    }
}

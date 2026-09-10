package ps2gm.game.manager;

import java.io.File;
import java.nio.file.Path;

/**
 * Resolves the directory an update actually swaps and where getCurrentDirectory() sits relative to it
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

    public static InstallLayout resolve(DistributionType type, String currentDir) {
        File currentDirFile = new File(currentDir == null ? "." : currentDir);

        if (type == DistributionType.WINDOWS_APP_IMAGE) {
            File parent = currentDirFile.getParentFile();
            return new InstallLayout(type, parent != null ? parent : currentDirFile, "app");
        }
        // JAR_BUNDLE and UNKNOWN both treat the current directory as the install root
        return new InstallLayout(type, currentDirFile, "");
    }

    public DistributionType distributionType() {
        return distributionType;
    }

    public File installRoot() {
        return installRoot;
    }

    // Absolute path for a location expressed relative to getCurrentDirectory()
    public File currentDirPath(String relativeToCurrentDir) {
        File currentDir = currentDirRelativeToInstallRoot.isEmpty()
                ? installRoot
                : new File(installRoot, currentDirRelativeToInstallRoot);
        return relativeToCurrentDir.isEmpty() ? currentDir : new File(currentDir, relativeToCurrentDir);
    }

    public File currentDirPathUnder(File root, String relativeToCurrentDir) {
        File currentDir = currentDirRelativeToInstallRoot.isEmpty()
                ? root
                : new File(root, currentDirRelativeToInstallRoot);
        return relativeToCurrentDir.isEmpty() ? currentDir : new File(currentDir, relativeToCurrentDir);
    }

    // Converts a path expressed relative to installRoot into one relative to getCurrentDirectory()
    public Path toCurrentDirRelative(Path relativeToInstallRoot) {
        if (currentDirRelativeToInstallRoot.isEmpty()) {
            return relativeToInstallRoot;
        }
        Path prefix = Path.of(currentDirRelativeToInstallRoot);
        return relativeToInstallRoot.startsWith(prefix) ? prefix.relativize(relativeToInstallRoot) : null;
    }
}

package ps2gm.game.manager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unzips a verified update package and assembles the final, ready-to-swap
 * install tree - entirely in-process, writing nothing under the live
 * install's root directory until the app updater actually swaps it in.
 */
public final class AppUpdateStager {

    private AppUpdateStager() {}

    public static final class StagingException extends Exception {
        public StagingException(String message) {
            super(message);
        }
        public StagingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static File stage(File verifiedZip, File stagingDir, InstallLayout layout) throws StagingException {
        File extracted = new File(stagingDir, "extracted");
        try {
            unzip(verifiedZip, extracted);
        } catch (IOException ex) {
            throw new StagingException("Failed to unzip the update package: " + ex, ex);
        }
        return assembleFrom(extracted, new File(stagingDir, "final"), layout);
    }

    static File assembleFrom(File extracted, File finalDir, InstallLayout layout) throws StagingException {
        try {
            checkShape(extracted, layout.distributionType());
            copyExtractedMinusPreserved(extracted.toPath(), finalDir.toPath(), layout);
            copyForwardPreservedPaths(layout, finalDir);
        } catch (IOException ex) {
            throw new StagingException("Failed to assemble the update package: " + ex, ex);
        }
        return finalDir;
    }

    private static void checkShape(File extracted, DistributionType type) throws StagingException {
        boolean ok = switch (type) {
            case WINDOWS_APP_IMAGE -> new File(extracted, "PS2GM.exe").isFile() && new File(extracted, "app").isDirectory();
            case JAR_BUNDLE -> new File(extracted, "lib").isDirectory();
            case UNKNOWN -> false;
        };
        if (!ok) {
            throw new StagingException("Downloaded update package doesn't look like a " + type + " install");
        }
    }

    private static void copyExtractedMinusPreserved(Path extractedRoot, Path finalRoot, InstallLayout layout) throws IOException {
        try (var walk = Files.walk(extractedRoot)) {
            for (Path src : (Iterable<Path>) walk::iterator) {
                Path relativeToInstallRoot = extractedRoot.relativize(src);
                if (relativeToInstallRoot.getNameCount() == 0) {
                    continue; // extractedRoot itself
                }
                Path relativeToCurrentDir = layout.toCurrentDirRelative(relativeToInstallRoot);
                if (relativeToCurrentDir != null && UpdatePreserveList.isPreserved(relativeToCurrentDir)) {
                    continue;
                }
                Path dest = finalRoot.resolve(relativeToInstallRoot);
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyForwardPreservedPaths(InstallLayout layout, File finalRoot) throws IOException {
        File liveCurrentDir = layout.currentDirPath("");
        if (!liveCurrentDir.isDirectory()) {
            return;
        }
        try (var walk = Files.walk(liveCurrentDir.toPath())) {
            for (Path src : (Iterable<Path>) walk::iterator) {
                if (Files.isDirectory(src)) {
                    continue; // Directories are created implicitly by the file copies below
                }
                Path relativeToCurrentDir = liveCurrentDir.toPath().relativize(src);
                if (!UpdatePreserveList.isPreserved(relativeToCurrentDir)) {
                    continue;
                }
                File dest = layout.currentDirPathUnder(finalRoot, relativeToCurrentDir.toString());
                Files.createDirectories(dest.getParentFile().toPath());
                Files.copy(src, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void unzip(File zipFile, File destDir) throws IOException {
        Path destRoot = destDir.toPath().normalize();
        Files.createDirectories(destRoot);
        try (InputStream fileIn = Files.newInputStream(zipFile.toPath());
             ZipInputStream zipIn = new ZipInputStream(fileIn)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                Path entryPath = destRoot.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(destRoot)) {
                    // Zip-slip guard - shouldn't happen given the checksum verification
                    // upstream, but never trust archive contents to stay inside their own box.
                    throw new IOException("Zip entry escapes its destination: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zipIn, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipIn.closeEntry();
            }
        }
    }
}

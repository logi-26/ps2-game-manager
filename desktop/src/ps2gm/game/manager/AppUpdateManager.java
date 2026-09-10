package ps2gm.game.manager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Coordinates the update flow end to end
 */
public final class AppUpdateManager {

    private AppUpdateManager() {}

    public record StagedUpdate(File finalDir, File stagingDir) {}

    public static AppUpdateChecker.Result checkForUpdate() {
        InstallLayout layout = InstallLayout.resolve();
        return AppUpdateChecker.check(PopsGameManager.newBackendClient(), PopsGameManager.getApplicationVersionNumber(), layout.distributionType());
    }

    /** Downloads, verifies and assembles the update. Null on any failure - nothing under the live install is touched either way. */
    public static StagedUpdate downloadAndStage(AppReleaseInfo release, AppPlatformAsset asset, AppUpdateProgress progress) {
        InstallLayout layout = InstallLayout.resolve();
        if (layout.distributionType() == DistributionType.UNKNOWN) {
            return null; // self-update apply is never offered for an unrecognised install - see DistributionType
        }

        File installRoot = layout.installRoot();
        File parent = installRoot.getParentFile();
        if (parent == null) {
            return null;
        }
        File stagingDir = new File(parent, ".ps2gm-update-" + release.version() + "-" + ProcessHandle.current().pid());
        if (!stagingDir.mkdirs()) {
            return null;
        }

        BackendClient api = PopsGameManager.newBackendClient();
        File verifiedZip = AppUpdateDownloader.downloadAndVerify(api, release.version(), asset, stagingDir, progress);
        if (verifiedZip == null) {
            deleteRecursively(stagingDir.toPath());
            return null;
        }

        if (progress != null) {
            progress.setPhase("Preparing");
            progress.setProgress(-1);
        }
        try {
            File finalDir = AppUpdateStager.stage(verifiedZip, stagingDir, layout);
            return new StagedUpdate(finalDir, stagingDir);
        } catch (AppUpdateStager.StagingException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
            deleteRecursively(stagingDir.toPath());
            return null;
        }
    }

    // Hands off to the apply helper and exits this JVM. Returns false (staging left in place, retryable) only if the helper couldn't even be started.
    public static boolean applyAndExit(StagedUpdate staged) {
        InstallLayout layout = InstallLayout.resolve();
        boolean started = AppUpdateApplier.apply(staged.finalDir(), staged.stagingDir(), layout);
        if (started) {
            System.exit(0);
        }
        return started;
    }

    /**
     * Called once at startup - clears any old/staging directories an interrupted or crashed update apply left behind, and surfaces a
     * failure log if the helper recorded one, so a failed update is never silently invisible.
     */
    public static void cleanupOrphanedArtifacts() {
        InstallLayout layout = InstallLayout.resolve();
        File installRoot = layout.installRoot();
        File parent = installRoot.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            return;
        }
        File[] siblings = parent.listFiles();
        if (siblings == null) {
            return;
        }

        String oldPrefix = installRoot.getName() + ".old-";
        for (File sibling : siblings) {
            String name = sibling.getName();
            if (name.startsWith(oldPrefix)) {
                deleteRecursively(sibling.toPath());
            } else if (name.startsWith(".ps2gm-update-")) {
                surfaceFailureLogIfPresent(sibling);
                deleteRecursively(sibling.toPath());
            }
        }
    }

    private static void surfaceFailureLogIfPresent(File stagingDir) {
        File log = new File(stagingDir, "ps2gm-update-failed.log");
        if (!log.isFile()) {
            return;
        }
        try {
            String message = Files.readString(log.toPath());
            PopsGameManager.showWarningDialog(
                    "The last app update did not complete successfully:\n\n" + message.strip()
                            + "\n\nThe previous version is still installed - you can try the update again from the menu.",
                    " Update Failed");
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ex) {
                    PopsGameManager.displayErrorMessageDebug(ex.toString());
                }
            });
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }
}

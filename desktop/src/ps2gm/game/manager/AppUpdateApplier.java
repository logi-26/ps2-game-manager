package ps2gm.game.manager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;

/**
 * Hands off to a small platform-native helper script that does the actual
 * install-directory swap once this JVM has fully exited (its own files -
 * the exe, its DLLs, the bundled JRE - are locked while it's running, so the
 * swap can never happen from inside this process). See the two bundled
 * templates for exactly what the helper does.
 *
 * Java's only remaining job after calling {@link #apply} is to exit -
 * {@code AppUpdateManager} does that immediately after this returns true.
 */
public final class AppUpdateApplier {

    private AppUpdateApplier() {}

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

    /** Renders and launches the helper script (detached), for the caller to then exit. False if it couldn't even be started. */
    public static boolean apply(File finalDir, File stagingDir, InstallLayout layout) {
        try {
            File installRoot = layout.installRoot();
            File oldDir = new File(installRoot.getParentFile(), installRoot.getName() + ".old-" + Instant.now().toEpochMilli());
            String relaunchCmd = relaunchCommand(installRoot, layout.distributionType());

            String templateName = WINDOWS ? "updater-apply-windows.cmd.template" : "updater-apply-posix.sh.template";
            String script = readTemplate(templateName)
                    .replace("@@PID@@", String.valueOf(ProcessHandle.current().pid()))
                    .replace("@@INSTALL_ROOT@@", installRoot.getAbsolutePath())
                    .replace("@@FINAL_DIR@@", finalDir.getAbsolutePath())
                    .replace("@@OLD_DIR@@", oldDir.getAbsolutePath())
                    .replace("@@STAGING_DIR@@", stagingDir.getAbsolutePath())
                    .replace("@@RELAUNCH_CMD@@", relaunchCmd);

            File scriptFile = new File(stagingDir, WINDOWS ? "apply-update.cmd" : "apply-update.sh");
            Files.writeString(scriptFile.toPath(), script, StandardCharsets.UTF_8);
            scriptFile.setExecutable(true);

            ProcessBuilder pb = WINDOWS
                    ? new ProcessBuilder("cmd", "/c", "start", "\"\"", scriptFile.getAbsolutePath())
                    : new ProcessBuilder("/bin/sh", scriptFile.getAbsolutePath());
            pb.directory(stagingDir);
            pb.start();
            return true;
        } catch (IOException | UncheckedIOException ex) {
            PopsGameManager.displayErrorMessageDebug("Could not launch the update-apply helper: " + ex);
            return false;
        }
    }

    private static String relaunchCommand(File installRoot, DistributionType type) {
        if (type == DistributionType.WINDOWS_APP_IMAGE) {
            return "\"" + new File(installRoot, "PS2GM.exe").getAbsolutePath() + "\"";
        }
        // JAR_BUNDLE - run.cmd on Windows, run.sh everywhere else (both regenerated fresh by
        // bundle-jar.ps1, pick the right JavaFX classifier themselves).
        String launcher = WINDOWS ? "run.cmd" : "run.sh";
        return "\"" + new File(installRoot, launcher).getAbsolutePath() + "\"";
    }

    private static String readTemplate(String resourceName) throws IOException {
        try (InputStream in = AppUpdateApplier.class.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IOException("Missing bundled resource: " + resourceName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

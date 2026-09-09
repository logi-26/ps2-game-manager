package ps2gm.game.manager;

import java.io.File;

/**
 * Which of the app's two distributables this install is - detected via an
 * on-disk marker file dropped next to the jar at package time
 * ({@code package.ps1}/{@code bundle-jar.ps1}), not a JVM launch flag.
 *
 * A launch-flag signal (e.g. a baked {@code -D} system property) is only
 * reliably present when started through {@code PS2GM.exe}/{@code run.sh}/
 * {@code run.cmd} - but both the update-apply relaunch and a user's own
 * manual {@code java -jar} invocation bypass those, which is exactly when a
 * reliable signal matters most. A file on disk is invocation-invariant.
 */
public enum DistributionType {

    /** jpackage app-image ({@code package.ps1}) - install root is one level above the jar. */
    WINDOWS_APP_IMAGE(".ps2gm-dist-windows-appimage"),
    /** Plain jar + lib/ bundle ({@code bundle-jar.ps1}) - install root is the jar's own folder. */
    JAR_BUNDLE(".ps2gm-dist-jar-bundle"),
    /** A dev tree ({@code build.ps1 -Run}) or a pre-update-feature install - self-update apply is disabled. */
    UNKNOWN(null);

    private final String markerFileName;

    DistributionType(String markerFileName) {
        this.markerFileName = markerFileName;
    }

    /** Never guesses - falls back to {@link #UNKNOWN} if neither marker file is present. */
    public static DistributionType detect() {
        return detect(PopsGameManager.getCurrentDirectory());
    }

    /** As {@link #detect()}, but against an explicit directory - the testable form. */
    public static DistributionType detect(String currentDir) {
        if (currentDir == null) {
            return UNKNOWN;
        }
        for (DistributionType type : values()) {
            if (type.markerFileName != null && new File(currentDir, type.markerFileName).isFile()) {
                return type;
            }
        }
        return UNKNOWN;
    }
}

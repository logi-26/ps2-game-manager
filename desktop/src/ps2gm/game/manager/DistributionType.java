package ps2gm.game.manager;

import java.io.File;

/**
 * Which of the app's two distributables this install is (Windows, Linux/Mac)
 * detected via an on-disk marker file dropped next to the jar at package time.
 */
public enum DistributionType {

    WINDOWS_APP_IMAGE(".ps2gm-dist-windows-appimage"),
    JAR_BUNDLE(".ps2gm-dist-jar-bundle"),
    UNKNOWN(null);

    private final String markerFileName;

    DistributionType(String markerFileName) {
        this.markerFileName = markerFileName;
    }

    public static DistributionType detect() {
        return detect(PopsGameManager.getCurrentDirectory());
    }

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

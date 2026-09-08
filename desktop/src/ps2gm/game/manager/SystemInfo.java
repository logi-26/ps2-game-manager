package ps2gm.game.manager;

/**
 * Detects the host operating system's type, version and architecture from
 * JVM system properties.
 *
 * Extracted from PopsGameManager.determineOSVersion, which mixed this pure
 * parsing with writing the results into PopsGameManager's own fields.
 */
public final class SystemInfo {

    private SystemInfo() {}

    public record Detection(String osType, String osVersion, String osArchitecture) {}

    public static Detection detect() {
        return detect(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    static Detection detect(String operatingSystemName, String operatingSystemArchitecture) {

        String osType = operatingSystemName;
        String osVersion = null;

        if (operatingSystemName.contains("Windows")) {
            osVersion = operatingSystemName.substring(8);
            osType = "Windows";
        } else if (operatingSystemName.contains("Linux")) {
            osVersion = operatingSystemName.substring(5);
            osType = "Linux";
        } else if (operatingSystemName.contains("Mac")) {
            osVersion = operatingSystemName.substring(5);
            osType = "Mac";
        }

        String architecture = operatingSystemArchitecture;
        if (architecture.length() >= 2) {
            String suffix = architecture.substring(architecture.length() - 2);
            if (suffix.equals("64")) {architecture = "64bit";}
            else if (suffix.equals("86")) {architecture = "32bit";}
        }

        return new Detection(osType, osVersion, architecture);
    }
}

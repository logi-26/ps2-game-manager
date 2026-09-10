package ps2gm.game.manager;

/**
 * Decodes a config.elm device-compatibility code (as stored in field 13 of a
 * game's config data) into its human-readable label.
 */
public final class DeviceCompatFormatter {

    private DeviceCompatFormatter() {}

    public static String format(String code) {
        if (code == null) {
            return "";
        }
        switch (code) {
            case "1": return "USB";
            case "5": return "ETH";
            case "6": return "HDD";
            case "2": return "USB, ETH";
            case "3": return "USB, HDD";
            case "4": return "HDD, ETH";
            case "all": return "USB, HDD, ETH";
            default: return "";
        }
    }
}

package ps2gm.game.manager;

public record AppPlatformAsset(String platform, String assetName, long bytes, String checksumAssetName) {

    /** The {@link DistributionType}'s matching platform key, or null for {@link DistributionType#UNKNOWN}. */
    static String platformKeyFor(DistributionType type) {
        return switch (type) {
            case WINDOWS_APP_IMAGE -> "windows-appimage";
            case JAR_BUNDLE -> "jar-bundle";
            case UNKNOWN -> null;
        };
    }
}

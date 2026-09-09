package ps2gm.game.manager;

/**
 * One platform's release asset, as returned by {@code GET /app/latest}'s
 * {@code platforms[]} - matches {@code AppPlatformAssetOut} in the API
 * (`api/app/schemas.py`). No download URLs here by design - the API keeps
 * the asset-naming convention and redirect logic server-side
 * (`GET /app/releases/{version}/download|checksum?platform=...`).
 */
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

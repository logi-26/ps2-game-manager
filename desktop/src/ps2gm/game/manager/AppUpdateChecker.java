package ps2gm.game.manager;

/**
 * Compares the running app's version against the latest published release
 * and picks out the asset (if any) that matches this install's distribution type.
 */
public final class AppUpdateChecker {

    private AppUpdateChecker() {}

    public enum Status {
        NO_RESPONSE,
        // The API was reachable, but no release has been published on this channel yet.
        NO_RELEASE_PUBLISHED,
        UP_TO_DATE,
        // A newer version exists, but this install's distribution type has no build for it yet.
        UPDATE_AVAILABLE_NO_PLATFORM_BUILD,
        UPDATE_AVAILABLE
    }

    public record Result(Status status, AppReleaseInfo release, AppPlatformAsset asset) {}

    public static Result check(BackendClient api, String currentVersion, DistributionType distributionType) {
        AppReleaseInfo release;
        try {
            release = api.getLatestAppRelease("stable");
        } catch (BackendUnreachableException ex) {
            return new Result(Status.NO_RESPONSE, null, null);
        }
        if (release == null) {
            return new Result(Status.NO_RELEASE_PUBLISHED, null, null);
        }

        AppVersion current = AppVersion.parse(currentVersion);
        AppVersion latest = AppVersion.parse(release.version());
        if (!current.isOlderThan(latest)) {
            return new Result(Status.UP_TO_DATE, release, null);
        }

        AppPlatformAsset asset = release.assetFor(distributionType);
        if (asset == null) {
            return new Result(Status.UPDATE_AVAILABLE_NO_PLATFORM_BUILD, release, null);
        }
        return new Result(Status.UPDATE_AVAILABLE, release, asset);
    }
}

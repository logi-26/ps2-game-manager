package ps2gm.game.manager;

import java.io.File;

/**
 * Downloads an update package and verifies it against its published SHA-256
 * checksum before anything else touches it. Any mismatch (or a missing/
 * unparsable checksum) deletes the download and reports failure - the live
 * install is never at risk from a corrupt or incomplete download.
 */
public final class AppUpdateDownloader {

    private AppUpdateDownloader() {}

    /** The verified zip file under {@code stagingDir}, or null on any download/verification failure. */
    public static File downloadAndVerify(BackendClient api, String version, AppPlatformAsset asset, File stagingDir, AppUpdateProgress progress) {
        if (progress != null) {
            progress.setPhase("Downloading");
            progress.setStatusText(asset.assetName());
            progress.setProgress(0);
        }

        File destZip = new File(stagingDir, asset.assetName());
        if (!api.downloadAppUpdatePackage(version, asset, destZip, progress)) {
            return null;
        }

        if (progress != null) {
            progress.setPhase("Verifying");
            progress.setProgress(-1);
            progress.setStatusText(asset.assetName());
        }

        String checksumText = api.downloadChecksumText(version, asset);
        String expectedDigest = ChecksumSidecar.parseHexDigest(checksumText);
        if (expectedDigest == null) {
            destZip.delete();
            return null;
        }

        String actualDigest = PopsGameManager.performSha256Check(destZip);
        if (actualDigest == null || !actualDigest.equalsIgnoreCase(expectedDigest)) {
            destZip.delete();
            return null;
        }

        return destZip;
    }
}

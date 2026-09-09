package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DistributionTypeTest {

    @TempDir
    File tempDir;

    @Test
    void detectsWindowsAppImageMarker() throws IOException {
        Files.createFile(new File(tempDir, ".ps2gm-dist-windows-appimage").toPath());
        assertEquals(DistributionType.WINDOWS_APP_IMAGE, DistributionType.detect(tempDir.getPath()));
    }

    @Test
    void detectsJarBundleMarker() throws IOException {
        Files.createFile(new File(tempDir, ".ps2gm-dist-jar-bundle").toPath());
        assertEquals(DistributionType.JAR_BUNDLE, DistributionType.detect(tempDir.getPath()));
    }

    @Test
    void neitherMarkerIsUnknown() {
        assertEquals(DistributionType.UNKNOWN, DistributionType.detect(tempDir.getPath()));
    }

    @Test
    void nullDirectoryIsUnknown() {
        assertEquals(DistributionType.UNKNOWN, DistributionType.detect(null));
    }

    @Test
    void appImageInstallRootIsTheParentOfCurrentDirWithAnAppSuffix() {
        File appDir = new File(tempDir, "app");
        appDir.mkdir();
        InstallLayout layout = InstallLayout.resolve(DistributionType.WINDOWS_APP_IMAGE, appDir.getPath());

        assertEquals(tempDir, layout.installRoot());
        assertEquals(new File(appDir, "hdd"), layout.currentDirPath("hdd"));
    }

    @Test
    void jarBundleInstallRootIsCurrentDirItself() {
        InstallLayout layout = InstallLayout.resolve(DistributionType.JAR_BUNDLE, tempDir.getPath());

        assertEquals(tempDir, layout.installRoot());
        assertEquals(new File(tempDir, "hdd"), layout.currentDirPath("hdd"));
    }
}

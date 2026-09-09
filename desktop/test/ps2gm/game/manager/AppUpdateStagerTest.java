package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppUpdateStagerTest {

    @TempDir
    File tempDir;

    private static void write(File file, String content) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), content);
    }

    /** Every file under root, as paths relative to root (forward-slash, sorted). */
    private static Set<String> relativeFiles(File root) throws IOException {
        try (Stream<java.nio.file.Path> walk = Files.walk(root.toPath())) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> root.toPath().relativize(p).toString().replace('\\', '/'))
                    .collect(Collectors.toSet());
        }
    }

    @Test
    void jarBundleAssemblyReplacesAppFilesAndPreservesUserData() throws Exception {
        // Live install: an old jar, a populated hdd/ (the user's actual game list), settings.
        File liveInstall = new File(tempDir, "live");
        write(new File(liveInstall, "PS2GM_0.6.1.jar"), "old jar");
        write(new File(liveInstall, "lib/commons-net-3.5.jar"), "old dep");
        write(new File(liveInstall, "hdd/gameListPS1"), "USERS REAL GAME LIST - MUST SURVIVE");
        write(new File(liveInstall, "hdd/ART/cover.jpg"), "user art");
        write(new File(liveInstall, "ps2gm-settings"), "USER SETTINGS - MUST SURVIVE");

        // Downloaded release package: new jar/lib, but ALSO (as a defensive scenario) a
        // pristine/default hdd/ - which must NOT be allowed to clobber the user's real one.
        File extracted = new File(tempDir, "extracted");
        write(new File(extracted, "lib/foo.jar"), "new dep");
        write(new File(extracted, "run.sh"), "new launcher");
        write(new File(extracted, "hdd/gameListPS1"), "pristine default - must be discarded");

        InstallLayout layout = InstallLayout.resolve(DistributionType.JAR_BUNDLE, liveInstall.getPath());
        File finalDir = AppUpdateStager.assembleFrom(extracted, new File(tempDir, "final"), layout);

        assertEquals(Files.readString(new File(finalDir, "lib/foo.jar").toPath()), "new dep");
        assertEquals(Files.readString(new File(finalDir, "run.sh").toPath()), "new launcher");
        assertEquals("USERS REAL GAME LIST - MUST SURVIVE", Files.readString(new File(finalDir, "hdd/gameListPS1").toPath()));
        assertEquals("user art", Files.readString(new File(finalDir, "hdd/ART/cover.jpg").toPath()));
        assertEquals("USER SETTINGS - MUST SURVIVE", Files.readString(new File(finalDir, "ps2gm-settings").toPath()));
        // Old app files not present in the new package must not linger.
        assertFalse(new File(finalDir, "PS2GM_0.6.1.jar").isFile());
        assertFalse(new File(finalDir, "lib/commons-net-3.5.jar").isFile());
    }

    @Test
    void windowsAppImageAssemblyMapsPreservedPathsUnderApp() throws Exception {
        File liveInstall = new File(tempDir, "livewin"); // installRoot
        File liveApp = new File(liveInstall, "app");      // getCurrentDirectory()
        write(new File(liveApp, "hdd/gameListPS2"), "USER PS2 LIST");
        write(new File(liveInstall, "PS2GM.exe"), "old exe");

        File extracted = new File(tempDir, "extractedwin");
        write(new File(extracted, "PS2GM.exe"), "new exe");
        write(new File(extracted, "app/PS2GM-local.jar"), "new jar");
        write(new File(extracted, "runtime/release"), "jre marker");

        InstallLayout layout = InstallLayout.resolve(DistributionType.WINDOWS_APP_IMAGE, liveApp.getPath());
        File finalDir = AppUpdateStager.assembleFrom(extracted, new File(tempDir, "finalwin"), layout);

        assertEquals("new exe", Files.readString(new File(finalDir, "PS2GM.exe").toPath()));
        assertEquals("new jar", Files.readString(new File(finalDir, "app/PS2GM-local.jar").toPath()));
        assertEquals("jre marker", Files.readString(new File(finalDir, "runtime/release").toPath()));
        assertEquals("USER PS2 LIST", Files.readString(new File(finalDir, "app/hdd/gameListPS2").toPath()));
    }

    @Test
    void resultingTreeContainsExactlyTheExpectedFiles() throws Exception {
        File liveInstall = new File(tempDir, "live2");
        write(new File(liveInstall, "ps2gm-settings"), "settings");
        write(new File(liveInstall, "hdd/gameListPS1"), "list");

        File extracted = new File(tempDir, "extracted2");
        write(new File(extracted, "lib/foo.jar"), "dep");

        InstallLayout layout = InstallLayout.resolve(DistributionType.JAR_BUNDLE, liveInstall.getPath());
        File finalDir = AppUpdateStager.assembleFrom(extracted, new File(tempDir, "final2"), layout);

        assertEquals(Set.of("lib/foo.jar", "ps2gm-settings", "hdd/gameListPS1"), relativeFiles(finalDir));
    }

    @Test
    void rejectsAJarBundlePackageMissingLibFolder() {
        File extracted = new File(tempDir, "badpkg");
        extracted.mkdirs();
        InstallLayout layout = InstallLayout.resolve(DistributionType.JAR_BUNDLE, new File(tempDir, "live3").getPath());

        assertThrows(AppUpdateStager.StagingException.class,
                () -> AppUpdateStager.assembleFrom(extracted, new File(tempDir, "final3"), layout));
    }

    @Test
    void rejectsAWindowsAppImagePackageMissingTheExe() throws IOException {
        File extracted = new File(tempDir, "badpkgwin");
        write(new File(extracted, "app/PS2GM-local.jar"), "jar"); // no PS2GM.exe at the top
        InstallLayout layout = InstallLayout.resolve(
                DistributionType.WINDOWS_APP_IMAGE, new File(tempDir, "live4/app").getPath());

        assertThrows(AppUpdateStager.StagingException.class,
                () -> AppUpdateStager.assembleFrom(extracted, new File(tempDir, "final4"), layout));
    }
}

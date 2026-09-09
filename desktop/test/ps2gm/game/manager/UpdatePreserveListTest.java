package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UpdatePreserveListTest {

    private static boolean preserved(String relativePath) {
        return UpdatePreserveList.isPreserved(Path.of(relativePath));
    }

    @Test
    void preservesTheSettingsFileAndItsLegacyName() {
        assertTrue(preserved("ps2gm-settings"));
        assertTrue(preserved("oplpops-settings"));
    }

    @Test
    void preservesTheBadGameListAndUlBackupFiles() {
        assertTrue(preserved("invalidGameList.txt"));
        assertTrue(preserved("invalidGameList"));
        assertTrue(preserved("ul-backup.txt"));
        assertTrue(preserved("ul-backup"));
    }

    @Test
    void preservesTheWholeHddTreeRegardlessOfDepth() {
        assertTrue(preserved("hdd"));
        assertTrue(preserved("hdd/gameListPS1"));
        assertTrue(preserved("hdd/gameListPS2"));
        assertTrue(preserved("hdd/ART/SLUS_207.68_COV.jpg"));
        assertTrue(preserved("hdd/POPS/some-game.VCD"));
    }

    @Test
    void preservesCue2PopsExeAndItsBackupFolder() {
        assertTrue(preserved("lib/data/tools/windows/cue2pops.exe"));
        assertTrue(preserved("lib/data/tools/windows/backup"));
        assertTrue(preserved("lib/data/tools/windows/backup/cue2pops.exe"));
    }

    @Test
    void doesNotPreserveOrdinaryAppFiles() {
        assertFalse(preserved("lib/commons-net-3.5.jar"));
        assertFalse(preserved("lib/javafx/javafx-graphics-21.0.5-win.jar"));
        assertFalse(preserved("PS2GM.exe"));
        assertFalse(preserved("PS2GM-local.jar"));
        assertFalse(preserved("run.sh"));
        assertFalse(preserved("run.cmd"));
        assertFalse(preserved("POPSTARTER/POPSTARTER.ELF"));
        assertFalse(preserved("runtime/release"));
    }

    @Test
    void doesNotPreserveOtherFilesUnderTheToolsWindowsFolder() {
        assertFalse(preserved("lib/data/tools/windows/hdl_dump.exe"));
        assertFalse(preserved("lib/data/tools/linux/cue2pops"));
    }

    @Test
    void doesNotFalsePositiveOnNamesThatMerelyStartWithAPreservedName() {
        // "hddbackup.txt" is a sibling file, not inside hdd/ - must not match on prefix alone.
        assertFalse(preserved("hddbackup.txt"));
        assertFalse(preserved("ps2gm-settings.bak"));
    }
}

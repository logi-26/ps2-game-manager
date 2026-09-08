package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UlCfgFileFormatTest {

    @Test
    void padsShortNameTo32Bytes() {
        String padded = UlCfgFileFormat.padName("Final Fantasy X");
        assertEquals(32, padded.length());
        assertEquals("Final Fantasy X", padded.substring(0, 15));
        for (int i = 15; i < 32; i++) {
            assertEquals(0, padded.charAt(i));
        }
    }

    @Test
    void leavesAlreadyFullLengthNameUnchanged() {
        String name = "A".repeat(32);
        assertEquals(name, UlCfgFileFormat.padName(name));
    }

    @Test
    void leavesEmptyNamePaddedToFullLength() {
        assertEquals(32, UlCfgFileFormat.padName("").length());
    }
}

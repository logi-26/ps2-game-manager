package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UlHexNamingTest {

    @Test
    void producesAnEightCharacterHexString() {
        String hex = UlHexNaming.toHex("Final Fantasy X".getBytes(StandardCharsets.US_ASCII));
        assertEquals(8, hex.length());
        assertTrue(hex.matches("[0-9A-F]{8}"));
    }

    @Test
    void isDeterministic() {
        byte[] name = "Gran Turismo 3".getBytes(StandardCharsets.US_ASCII);
        assertEquals(UlHexNaming.toHex(name), UlHexNaming.toHex(name));
    }

    @Test
    void differentNamesProduceDifferentHex() {
        String a = UlHexNaming.toHex("Final Fantasy X".getBytes(StandardCharsets.US_ASCII));
        String b = UlHexNaming.toHex("Gran Turismo 3".getBytes(StandardCharsets.US_ASCII));
        assertNotEquals(a, b);
    }

    @Test
    void knownValueForEmptyName() {
        // Regression value for the fixed input "" (i.e. just the trailing zero byte
        // the algorithm always appends) - pins the CRC table/algorithm exactly.
        assertEquals("00000000", UlHexNaming.toHex(new byte[0]));
    }

    @Test
    void knownValueForFinalFantasyX() {
        assertEquals("30C76171", UlHexNaming.toHex("Final Fantasy X".getBytes(StandardCharsets.US_ASCII)));
    }
}

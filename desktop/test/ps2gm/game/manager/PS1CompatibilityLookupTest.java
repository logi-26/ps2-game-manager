package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class PS1CompatibilityLookupTest {

    @Test
    void findsAKnownGameFromTheBundledResource() {
        // First line of PS1CompatabilityList.txt: "SLPS_019.86-USB=0-HDD=0-SMB=0"
        var compat = PS1CompatibilityLookup.lookup("SLPS_019.86");
        assertEquals("0", compat.usb());
        assertEquals("0", compat.hdd());
        assertEquals("0", compat.smb());
    }

    @Test
    void findsAGameWithNonDefaultFlags() {
        // "SLPS_008.30-USB=0-HDD=1-SMB=0"
        var compat = PS1CompatibilityLookup.lookup("SLPS_008.30");
        assertEquals("0", compat.usb());
        assertEquals("1", compat.hdd());
        assertEquals("0", compat.smb());
    }

    @Test
    void unknownGameReturnsTheDefault() {
        assertSame(PS1CompatibilityLookup.Compatibility.UNKNOWN, PS1CompatibilityLookup.lookup("SLUS_999.99"));
    }
}

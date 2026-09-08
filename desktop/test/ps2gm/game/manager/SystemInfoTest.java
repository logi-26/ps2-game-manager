package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SystemInfoTest {

    @Test
    void parsesWindowsNameAndVersion() {
        SystemInfo.Detection detection = SystemInfo.detect("Windows 10", "amd64");
        assertEquals("Windows", detection.osType());
        assertEquals("10", detection.osVersion());
        assertEquals("64bit", detection.osArchitecture());
    }

    @Test
    void parsesLinuxNameAndVersion() {
        // The original fixed offset (5 = "Linux".length()) leaves the separating
        // space in the version - preserved as-is, not a new bug from this move.
        SystemInfo.Detection detection = SystemInfo.detect("Linux 6.1.0", "x86");
        assertEquals("Linux", detection.osType());
        assertEquals(" 6.1.0", detection.osVersion());
        assertEquals("32bit", detection.osArchitecture());
    }

    @Test
    void parsesMacNameAndVersion() {
        // Same fixed-offset quirk as Linux, worse here since "Mac ".length() is 4,
        // not 5 - substring(5) eats an extra character. Preserved as-is.
        SystemInfo.Detection detection = SystemInfo.detect("Mac OS X", "aarch64");
        assertEquals("Mac", detection.osType());
        assertEquals("S X", detection.osVersion());
        assertEquals("64bit", detection.osArchitecture());
    }

    @Test
    void leavesUnknownOsNameAndArchitectureUnchanged() {
        SystemInfo.Detection detection = SystemInfo.detect("SolarOS", "sparc");
        assertEquals("SolarOS", detection.osType());
        assertNull(detection.osVersion());
        assertEquals("sparc", detection.osArchitecture());
    }
}

package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RemotePathTest {

    @Test
    void parsesVcdPath() {
        RemotePath path = RemotePath.parse("hdd0:/__.POPS");
        assertEquals("hdd", path.drive());
        assertEquals("0", path.partition());
        assertEquals("__.POPS", path.folder());
    }

    @Test
    void parsesElfPathWithNestedFolder() {
        RemotePath path = RemotePath.parse("hdd0:/+OPL/APPS");
        assertEquals("hdd", path.drive());
        assertEquals("0", path.partition());
        assertEquals("+OPL/APPS", path.folder());
    }

    @Test
    void parsesMassDrive() {
        RemotePath path = RemotePath.parse("mass1:/+OPL");
        assertEquals("mass", path.drive());
        assertEquals("1", path.partition());
        assertEquals("+OPL", path.folder());
    }
}

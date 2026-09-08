package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MultiDiscGameCatalogTest {

    @Test
    void findsKnownMultiDiscGame() {
        String[] siblings = MultiDiscGameCatalog.siblingDiscsFor("SLUS_005.94");
        assertArrayEquals(new String[] {"SLUS_005.94", "SLUS_007.76"}, siblings);
    }

    @Test
    void findsSiblingRegardlessOfWhichDiscIdIsLookedUp() {
        String[] siblings = MultiDiscGameCatalog.siblingDiscsFor("SLUS_007.76");
        assertArrayEquals(new String[] {"SLUS_005.94", "SLUS_007.76"}, siblings);
    }

    @Test
    void returnsNullForANonMultiDiscGame() {
        assertNull(MultiDiscGameCatalog.siblingDiscsFor("SLUS_207.68"));
    }
}

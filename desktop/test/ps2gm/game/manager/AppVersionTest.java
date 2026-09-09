package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppVersionTest {

    @Test
    void equalVersionsAreNotOlder() {
        assertFalse(AppVersion.parse("1.0").isOlderThan(AppVersion.parse("1.0")));
        assertFalse(AppVersion.parse("0.6.1").isOlderThan(AppVersion.parse("0.6.1")));
    }

    @Test
    void comparesNumericSegmentsInOrder() {
        assertTrue(AppVersion.parse("0.6.1").isOlderThan(AppVersion.parse("1.0")));
        assertTrue(AppVersion.parse("1.0").isOlderThan(AppVersion.parse("1.0.1")));
        assertTrue(AppVersion.parse("1.9.0").isOlderThan(AppVersion.parse("1.10.0")));
        assertFalse(AppVersion.parse("1.10.0").isOlderThan(AppVersion.parse("1.9.0")));
    }

    @Test
    void treatsMissingTrailingSegmentsAsZero() {
        assertEquals(AppVersion.parse("1.0"), AppVersion.parse("1.0.0"));
        assertTrue(AppVersion.parse("1.0").isOlderThan(AppVersion.parse("1.0.1")));
    }

    @Test
    void aSuffixedPrereleaseSortsBelowTheSameNumericRelease() {
        assertTrue(AppVersion.parse("1.0-beta").isOlderThan(AppVersion.parse("1.0")));
        assertFalse(AppVersion.parse("1.0").isOlderThan(AppVersion.parse("1.0-beta")));
    }

    @Test
    void malformedSegmentsReadAsZeroRatherThanThrowing() {
        assertEquals(AppVersion.parse("1.x.0"), AppVersion.parse("1.0.0"));
        assertEquals(AppVersion.parse(""), AppVersion.parse("0"));
        assertEquals(AppVersion.parse(null), AppVersion.parse("0"));
    }
}

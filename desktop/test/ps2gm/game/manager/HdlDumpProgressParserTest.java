package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class HdlDumpProgressParserTest {

    @Test
    void parsesFourFieldLine() {
        var progress = HdlDumpProgressParser.parseLine("45,3,12,1.2MB/s", 0, "0:00");
        assertEquals(45, progress.percentDownloaded());
        assertEquals("3:12", progress.timeRemaining());
        assertEquals("1.2MB/s", progress.downloadSpeed());
    }

    @Test
    void parsesThreeFieldLineWithSecondsRemaining() {
        var progress = HdlDumpProgressParser.parseLine("7,45secremaining,900KB/s", 0, "0:00");
        assertEquals(7, progress.percentDownloaded());
        assertEquals("0:45", progress.timeRemaining());
        assertEquals("900KB/s", progress.downloadSpeed());
    }

    @Test
    void parsesThreeFieldLineWithMinutesRemaining() {
        var progress = HdlDumpProgressParser.parseLine("12,5minremaining,1MB/s", 0, "0:00");
        assertEquals("5:00", progress.timeRemaining());
    }

    @Test
    void bareLineTakesOnlyItsFirstCharacterUnlessItsExactlyFourLong() {
        // Matches the original code exactly: a bare (no-comma) line only reads 3 digits
        // when it's exactly 4 characters long; anything else (even "99") only reads the
        // first character. Quirky, but this pins the pre-existing behavior, not a new bug.
        var progress = HdlDumpProgressParser.parseLine("99", 0, "3:12");
        assertEquals(9, progress.percentDownloaded());
        assertEquals("3:12", progress.timeRemaining()); // untouched by this line
        assertNull(progress.downloadSpeed());
    }

    @Test
    void fourDigitBareLineUsesFirstThreeDigits() {
        var progress = HdlDumpProgressParser.parseLine("1000", 0, "0:00");
        assertEquals(100, progress.percentDownloaded());
    }

    @Test
    void stripsWhitespaceBeforeParsing() {
        var progress = HdlDumpProgressParser.parseLine(" 45 , 3 , 12 , 1.2MB/s ", 0, "0:00");
        assertEquals(45, progress.percentDownloaded());
        assertEquals("3:12", progress.timeRemaining());
    }

    @Test
    void emptyLineLeavesStateUnchanged() {
        var progress = HdlDumpProgressParser.parseLine("", 42, "1:00");
        assertEquals(42, progress.percentDownloaded());
        assertEquals("1:00", progress.timeRemaining());
        assertNull(progress.downloadSpeed());
    }
}

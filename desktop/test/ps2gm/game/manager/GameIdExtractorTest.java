package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GameIdExtractorTest {

    // ---- extractGameIDFromFilename -------------------------------------------------

    @Test
    void extractsIdFromZsoFilename() {
        assertEquals("SLUS_215.93", GameIdExtractor.extractGameIDFromFilename("SLUS_215.93.Some Game.zso"));
    }

    @Test
    void extractsIdRegardlessOfPositionInFilename() {
        assertEquals("SLES_500.12", GameIdExtractor.extractGameIDFromFilename("Some Game.SLES_500.12.iso"));
    }

    @Test
    void returnsNullWhenNoRegionCodeMatches() {
        assertNull(GameIdExtractor.extractGameIDFromFilename("Some Game With No ID.iso"));
    }

    // ---- stripGameId ------------------------------------------------------------

    @Test
    void stripsIdWhenPrefixed() {
        assertEquals("Final Fantasy X", GameIdExtractor.stripGameId("SLUS_207.68.Final Fantasy X", "SLUS_207.68"));
    }

    @Test
    void stripsIdWhenSuffixedWithDash() {
        assertEquals("Final Fantasy X", GameIdExtractor.stripGameId("Final Fantasy X-SLUS_207.68", "SLUS_207.68"));
    }

    @Test
    void stripsIdInParentheses() {
        assertEquals("Final Fantasy X", GameIdExtractor.stripGameId("Final Fantasy X (SLUS_207.68)", "SLUS_207.68"));
    }

    @Test
    void stripsIdInBrackets() {
        assertEquals("Final Fantasy X", GameIdExtractor.stripGameId("Final Fantasy X [SLUS_207.68]", "SLUS_207.68"));
    }

    @Test
    void leavesNameUnchangedWhenGameIdIsNull() {
        assertEquals("Final Fantasy X", GameIdExtractor.stripGameId("Final Fantasy X", null));
    }

    @Test
    void returnsNullBaseUnchanged() {
        assertNull(GameIdExtractor.stripGameId(null, "SLUS_207.68"));
    }

    // ---- deriveDescriptiveName ----------------------------------------------------

    @Test
    void derivesNameFromIdPrefixedFilename() {
        assertEquals("Final Fantasy X", GameIdExtractor.deriveDescriptiveName("SLUS_207.68.Final Fantasy X.iso", "SLUS_207.68"));
    }

    @Test
    void derivesNameFromIdSuffixedFilename() {
        assertEquals("Final Fantasy X", GameIdExtractor.deriveDescriptiveName("Final Fantasy X-SLUS_207.68.vcd", "SLUS_207.68"));
    }

    @Test
    void fallsBackToIdWhenFilenameIsJustTheId() {
        assertEquals("SLUS_207.68", GameIdExtractor.deriveDescriptiveName("SLUS_207.68.vcd", "SLUS_207.68"));
    }

    @Test
    void fallsBackToExtensionStrippedFilenameWhenGameIdIsNull() {
        assertEquals("Some Unrecognised Game", GameIdExtractor.deriveDescriptiveName("Some Unrecognised Game.vcd", null));
    }
}

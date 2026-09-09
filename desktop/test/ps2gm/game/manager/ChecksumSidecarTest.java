package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ChecksumSidecarTest {

    private static final String DIGEST = "a".repeat(64);

    @Test
    void parsesSha256sumFormat() {
        assertEquals(DIGEST, ChecksumSidecar.parseHexDigest(DIGEST + "  PS2GM-windows-1.0.zip"));
    }

    @Test
    void parsesJustTheDigestWithNoFilename() {
        assertEquals(DIGEST, ChecksumSidecar.parseHexDigest(DIGEST));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals(DIGEST, ChecksumSidecar.parseHexDigest(DIGEST.toUpperCase() + "  file.zip"));
    }

    @Test
    void ignoresTrailingNewlinesAndWhitespace() {
        assertEquals(DIGEST, ChecksumSidecar.parseHexDigest("  " + DIGEST + "  file.zip\r\n"));
    }

    @Test
    void returnsNullForTheWrongLength() {
        assertNull(ChecksumSidecar.parseHexDigest("abc123"));
    }

    @Test
    void returnsNullForNonHexCharacters() {
        assertNull(ChecksumSidecar.parseHexDigest("z".repeat(64)));
    }

    @Test
    void returnsNullForEmptyOrNullInput() {
        assertNull(ChecksumSidecar.parseHexDigest(""));
        assertNull(ChecksumSidecar.parseHexDigest(null));
    }
}

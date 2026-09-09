package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trips the ZISO/ZSO codec through {@link IsoZsoConverter}'s test-only
 * seams, bypassing the Game/GameListManager file-management {@code run()}
 * does around it. The codec itself is the highest-risk part of the feature -
 * a wrong byte here would silently corrupt a real game file - so these
 * assert exact byte-for-byte equality after a full compress+decompress
 * round trip, not just "it didn't throw".
 */
class IsoZsoConverterTest {

    private static final int BLOCK_SIZE = 0x800;

    private static ConversionProgress noopProgress() {
        return new ConversionProgress() {
            @Override public void setProgressRange(long min, long max) {}
            @Override public void setProgress(long value) {}
            @Override public void finished(boolean success, String errorMessage) {}
        };
    }

    @Test
    void roundTripsMixedCompressibleAndRandomContentExactly(@TempDir File dir) throws IOException {
        Random rnd = new Random(42);
        byte[] original = new byte[BLOCK_SIZE * 200];
        for (int block = 0; block < 200; block++) {
            int off = block * BLOCK_SIZE;
            if (block % 3 == 0) {
                // highly compressible: a repeated pattern
                for (int i = 0; i < BLOCK_SIZE; i++) { original[off + i] = (byte) (i % 4); }
            } else {
                // incompressible: forces some blocks through the "store plain" fallback
                rnd.nextBytes(new byte[0]);
                byte[] chunk = new byte[BLOCK_SIZE];
                rnd.nextBytes(chunk);
                System.arraycopy(chunk, 0, original, off, BLOCK_SIZE);
            }
        }
        File iso = new File(dir, "game.iso");
        Files.write(iso.toPath(), original);

        File zso = new File(dir, "game.zso");
        IsoZsoConverter.compressForTest(iso, zso, noopProgress());
        assertTrue(zso.length() > 0, "compressed file should be written");

        File roundTripped = new File(dir, "roundtrip.iso");
        IsoZsoConverter.decompressForTest(zso, roundTripped, noopProgress());

        byte[] result = Files.readAllBytes(roundTripped.toPath());
        assertArrayEquals(original, result, "decompressed bytes must exactly match the source ISO");
    }

    @Test
    void compressesHighlyCompressibleContentSmaller(@TempDir File dir) throws IOException {
        byte[] original = new byte[BLOCK_SIZE * 100];   // all-zero: maximally compressible
        File iso = new File(dir, "zeros.iso");
        Files.write(iso.toPath(), original);

        File zso = new File(dir, "zeros.zso");
        IsoZsoConverter.compressForTest(iso, zso, noopProgress());

        assertTrue(zso.length() < original.length / 2,
                "an all-zero ISO should compress to well under half its size");
    }

    @Test
    void roundTripsAllIncompressibleContentExactly(@TempDir File dir) throws IOException {
        Random rnd = new Random(7);
        byte[] original = new byte[BLOCK_SIZE * 50];
        rnd.nextBytes(original);   // every block should hit the "store plain" fallback
        File iso = new File(dir, "random.iso");
        Files.write(iso.toPath(), original);

        File zso = new File(dir, "random.zso");
        IsoZsoConverter.compressForTest(iso, zso, noopProgress());

        File roundTripped = new File(dir, "roundtrip.iso");
        IsoZsoConverter.decompressForTest(zso, roundTripped, noopProgress());

        assertArrayEquals(original, Files.readAllBytes(roundTripped.toPath()));
    }

    @Test
    void reportsProgressUpToTheFullBlockCount(@TempDir File dir) throws IOException {
        byte[] original = new byte[BLOCK_SIZE * 10];
        File iso = new File(dir, "game.iso");
        Files.write(iso.toPath(), original);
        File zso = new File(dir, "game.zso");

        long[] lastValue = {-1};
        long[] rangeMax = {-1};
        AtomicBoolean finishedSuccessfully = new AtomicBoolean(false);
        IsoZsoConverter.compressForTest(iso, zso, new ConversionProgress() {
            @Override public void setProgressRange(long min, long max) { rangeMax[0] = max; }
            @Override public void setProgress(long value) { lastValue[0] = value; }
            @Override public void finished(boolean success, String errorMessage) { finishedSuccessfully.set(success); }
        });

        assertEquals(10, rangeMax[0]);
        assertEquals(10, lastValue[0]);
    }

    @Test
    void rejectsAnIsoWhoseSizeIsNotBlockAligned(@TempDir File dir) throws IOException {
        File iso = new File(dir, "bad.iso");
        Files.write(iso.toPath(), new byte[BLOCK_SIZE + 1]);
        File zso = new File(dir, "bad.zso");

        assertThrows(IOException.class, () -> IsoZsoConverter.compressForTest(iso, zso, noopProgress()));
    }

    @Test
    void rejectsAFileThatIsNotActuallyZso(@TempDir File dir) throws IOException {
        File notZso = new File(dir, "not.zso");
        Files.write(notZso.toPath(), "this is not a ziso file".getBytes());
        File iso = new File(dir, "out.iso");

        assertThrows(IOException.class, () -> IsoZsoConverter.decompressForTest(notZso, iso, noopProgress()));
    }

    @Test
    void alignUpPadsToTheRequestedPowerOfTwoBoundary() {
        assertEquals(0, IsoZsoConverter.alignUp(0, 0));
        assertEquals(5, IsoZsoConverter.alignUp(5, 0));     // align 0 -> unit 1, never pads
        assertEquals(6, IsoZsoConverter.alignUp(5, 1));     // unit 2
        assertEquals(6, IsoZsoConverter.alignUp(6, 1));     // already aligned
        assertEquals(8, IsoZsoConverter.alignUp(5, 3));     // unit 8
    }
}

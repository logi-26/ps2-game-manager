package ps2gm.game.manager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

/**
 * Converts a PS2 game file between plain .ISO and the LZ4-compressed .ZSO
 */
public final class IsoZsoConverter {

    private static final int MAGIC = 0x4F53495A;               // "ZISO", little-endian
    private static final int HEADER_SIZE = 0x18;                // 24 bytes
    private static final int BLOCK_SIZE = 0x800;                 // 2048 bytes
    private static final int COMPRESS_THRESHOLD_PERCENT = 95;    // matches ziso.py's default
    private static final byte PADDING_BYTE = (byte) 'X';
    private static final long PLAIN_FLAG = 0x80000000L;
    private static final long ALIGN_UNIT = 1L << 31;

    private final ConversionProgress ui;
    private final Game game;
    private final boolean toZso;

    private IsoZsoConverter(ConversionProgress ui, Game game, boolean toZso) {
        this.ui = ui;
        this.game = game;
        this.toZso = toZso;
    }

    // Starts the conversion on a background virtual thread
    public static void start(ConversionProgress ui, Game game, boolean toZso) {
        BackgroundTasks.runDaemon("iso-zso-convert", new IsoZsoConverter(ui, game, toZso)::run);
    }

    static void compressForTest(File iso, File zso, ConversionProgress ui) throws IOException {
        new IsoZsoConverter(ui, null, true).compress(iso, zso);
    }

    static void decompressForTest(File zso, File iso, ConversionProgress ui) throws IOException {
        new IsoZsoConverter(ui, null, false).decompress(zso, iso);
    }

    private void run() {
        File source = new File(game.getGamePath());
        File target = targetFile(source, toZso);
        File temp = new File(target.getParentFile(), target.getName() + ".converting");

        if (target.exists()) {
            ui.finished(false, "A file named \"" + target.getName() + "\" already exists.");
            return;
        }

        boolean ok = false;
        String error = null;
        try {
            if (toZso) {
                compress(source, temp);
            } else {
                decompress(source, temp);
            }
            if (!temp.renameTo(target)) {
                throw new IOException("Could not rename the converted file into place.");
            }
            ok = true;
        } catch (Exception ex) {
            error = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            PopsGameManager.displayErrorMessageDebug("ISO/ZSO conversion failed: " + ex);
        } finally {
            if (!ok) {
                temp.delete();
            }
        }

        if (ok) {
            if (!source.delete()) {
                PopsGameManager.displayErrorMessageDebug(
                        "Converted " + source.getName() + " but could not delete the original file.");
            }
            game.setGamePath(target.toString());
            GameListManager.createGameListsPS2(false);
            PopsGameManager.callbackToUpdateGUIGameList(game.getGameID(), -1);
        }
        ui.finished(ok, error);
    }

    private static File targetFile(File source, boolean toZso) {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(source.getParentFile(), base + (toZso ? ".zso" : ".iso"));
    }

    private void compress(File isoFile, File zsoFile) throws IOException {
        long totalBytes = isoFile.length();
        if (totalBytes <= 0 || totalBytes % BLOCK_SIZE != 0) {
            throw new IOException("Not a valid ISO file (size is not a multiple of " + BLOCK_SIZE + " bytes).");
        }
        int totalBlocks = (int) (totalBytes / BLOCK_SIZE);
        int align = (int) (totalBytes / ALIGN_UNIT);

        LZ4Compressor compressor = LZ4Factory.fastestJavaInstance().highCompressor();
        CRC32 sourceCrc = new CRC32();

        ui.setProgressRange(0, totalBlocks);

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(isoFile), 1 << 20);
             RandomAccessFile out = new RandomAccessFile(zsoFile, "rw")) {

            out.setLength(0);
            writeHeader(out, totalBytes, align);

            long[] index = new long[totalBlocks + 1];
            long writePos = HEADER_SIZE + (long) (totalBlocks + 1) * 4;
            out.seek(writePos);

            byte[] block = new byte[BLOCK_SIZE];
            for (int i = 0; i < totalBlocks; i++) {
                readFully(in, block);
                sourceCrc.update(block);

                byte[] compressed = compressor.compress(block, 0, BLOCK_SIZE);

                long padded = alignUp(writePos, align);
                if (padded != writePos) {
                    byte[] pad = new byte[(int) (padded - writePos)];
                    Arrays.fill(pad, PADDING_BYTE);
                    out.write(pad);
                    writePos = padded;
                }

                long entry = writePos >>> align;
                byte[] toWrite;
                if (compressed.length * 100L / BLOCK_SIZE >= COMPRESS_THRESHOLD_PERCENT) {
                    toWrite = block;
                    entry |= PLAIN_FLAG;
                } else if ((entry & PLAIN_FLAG) != 0) {
                    throw new IOException("ISO too large to index at this alignment - internal error.");
                } else {
                    toWrite = compressed;
                }
                index[i] = entry;

                out.write(toWrite);
                writePos += toWrite.length;

                if ((i & 0xFF) == 0 || i == totalBlocks - 1) {
                    ui.setProgress(i + 1);
                }
            }
            index[totalBlocks] = writePos >>> align;

            out.seek(HEADER_SIZE);
            ByteBuffer indexBuf = ByteBuffer.allocate((totalBlocks + 1) * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (long v : index) { indexBuf.putInt((int) v); }
            out.write(indexBuf.array());
        }

        verifyRoundTrip(zsoFile, totalBytes, sourceCrc.getValue());
    }

    static long alignUp(long pos, int align) {
        long unit = 1L << align;
        long rem = pos % unit;
        return rem == 0 ? pos : pos + (unit - rem);
    }

    private static void writeHeader(RandomAccessFile out, long totalBytes, int align) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(MAGIC);
        buf.putInt(HEADER_SIZE);
        buf.putLong(totalBytes);
        buf.putInt(BLOCK_SIZE);
        buf.put((byte) 1);     // version
        buf.put((byte) align);
        buf.put((byte) 0);     // reserved
        buf.put((byte) 0);     // reserved
        out.seek(0);
        out.write(buf.array());
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) { throw new EOFException("Unexpected end of file while reading a block."); }
            off += n;
        }
    }

    private void decompress(File zsoFile, File isoFile) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(zsoFile, "r");
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(isoFile), 1 << 20)) {

            ZsoHeader h = readHeader(in);
            ui.setProgressRange(0, h.totalBlocks);

            LZ4FastDecompressor decompressor = LZ4Factory.fastestJavaInstance().fastDecompressor();
            for (int i = 0; i < h.totalBlocks; i++) {
                byte[] decoded = readAndDecodeBlock(in, h, i, decompressor);
                out.write(decoded, 0, h.blockSize);

                if ((i & 0xFF) == 0 || i == h.totalBlocks - 1) {
                    ui.setProgress(i + 1);
                }
            }
        }
    }

    // Re-decompresses the just-written ZSO (no output file, just a checksum) and
    // compares it against the ISO bytes seen while compressing
    private void verifyRoundTrip(File zsoFile, long expectedTotalBytes, long expectedCrc) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(zsoFile, "r")) {
            ZsoHeader h = readHeader(in);
            if (h.totalBytes != expectedTotalBytes) {
                throw new IOException("Verification failed: size mismatch after conversion.");
            }

            LZ4FastDecompressor decompressor = LZ4Factory.fastestJavaInstance().fastDecompressor();
            CRC32 crc = new CRC32();
            for (int i = 0; i < h.totalBlocks; i++) {
                byte[] decoded = readAndDecodeBlock(in, h, i, decompressor);
                crc.update(decoded, 0, h.blockSize);
            }
            if (crc.getValue() != expectedCrc) {
                throw new IOException("Verification failed: checksum mismatch after conversion.");
            }
        }
    }

    static final class ZsoHeader {
        long totalBytes;
        int blockSize;
        int align;
        int totalBlocks;
        long[] index;
    }

    static ZsoHeader readHeader(RandomAccessFile in) throws IOException {
        if (in.length() < HEADER_SIZE) {
            throw new IOException("Not a valid ZSO file (too small).");
        }
        byte[] headerBytes = new byte[HEADER_SIZE];
        in.readFully(headerBytes);
        ByteBuffer hb = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = hb.getInt();
        int headerSize = hb.getInt();
        long totalBytes = hb.getLong();
        int blockSize = hb.getInt();
        int ver = hb.get();
        int align = hb.get();

        if (magic != MAGIC || headerSize != HEADER_SIZE || blockSize <= 0 || totalBytes <= 0 || ver > 1) {
            throw new IOException("Not a valid ZSO file (bad header).");
        }
        if (totalBytes % blockSize != 0) {
            throw new IOException("Not a valid ZSO file (size is not block-aligned).");
        }

        ZsoHeader h = new ZsoHeader();
        h.totalBytes = totalBytes;
        h.blockSize = blockSize;
        h.align = align;
        h.totalBlocks = (int) (totalBytes / blockSize);

        h.index = new long[h.totalBlocks + 1];
        byte[] indexBytes = new byte[(h.totalBlocks + 1) * 4];
        in.readFully(indexBytes);
        ByteBuffer ib = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i <= h.totalBlocks; i++) { h.index[i] = ib.getInt() & 0xFFFFFFFFL; }
        return h;
    }

    static byte[] readAndDecodeBlock(RandomAccessFile in, ZsoHeader h, int block,
                                              LZ4FastDecompressor decompressor) throws IOException {
        long entry = h.index[block];
        boolean plain = (entry & PLAIN_FLAG) != 0;
        long offset = (entry & ~PLAIN_FLAG) << h.align;
        long nextOffset = (h.index[block + 1] & ~PLAIN_FLAG) << h.align;
        long readSize = plain ? h.blockSize : nextOffset - offset;
        if (readSize <= 0 || readSize > Integer.MAX_VALUE) {
            throw new IOException("Corrupt ZSO index at block " + block + ".");
        }

        in.seek(offset);
        byte[] raw = new byte[(int) readSize];
        in.readFully(raw);

        if (plain) {
            if (raw.length != h.blockSize) {
                throw new IOException("Corrupt ZSO data at block " + block + ".");
            }
            return raw;
        }
        return decompressor.decompress(raw, 0, h.blockSize);
    }
}

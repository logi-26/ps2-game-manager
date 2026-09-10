package ps2gm.game.manager;

/**
 * OPL's UL-game hex-naming CRC32-variant algorithm, used to build the
 * "ul.&lt;HEX&gt;.&lt;gameID&gt;.&lt;n&gt;" fragment filenames USBUtil
 * reads/writes for USB-Advance mode.
 */
public final class UlHexNaming {

    private static final int[] CRC_TABLE = new int[256];

    static {
        for (int table = 0; table < 256; table++) {
            int crc = table << 24;
            for (int count = 8; count > 0; count--) {
                crc = (crc < 0) ? (crc << 1) : (crc << 1 ^ 0x04C11DB7);
            }
            CRC_TABLE[255 - table] = crc;
        }
    }

    private UlHexNaming() {}

    public static String toHex(byte[] gameName) {
        int crc = 0;
        for (byte b : gameName) {
            crc = CRC_TABLE[b ^ ((crc >>> 24) & 0xFF)] ^ ((crc << 8) & 0xFFFFFF00);
        }
        crc = CRC_TABLE[0 ^ ((crc >>> 24) & 0xFF)] ^ ((crc << 8) & 0xFFFFFF00);
        return String.format("%08X", crc);
    }
}

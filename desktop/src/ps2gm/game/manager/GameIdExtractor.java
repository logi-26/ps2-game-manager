package ps2gm.game.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.sevenzipjbinding.ArchiveFormat;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;

/**
 * Reads a game's unique disc-serial ID out of its ISO (PS2) or VCD (PS1)
 * file, and the pure filename/string-parsing helpers those two lean on.
 *
 * Extracted from GameListManager for cohesion - this has no dependency on
 * GameListManager's list state, unlike most of that class.
 */
public final class GameIdExtractor {

    private GameIdExtractor() {}

    /**
     * This searches the ISO file for the game's unique identifier file. Two
     * cases fall back to reading the ID out of the filename instead
     * ("&lt;id&gt;.&lt;name&gt;.iso" / "&lt;id&gt;.&lt;name&gt;.zso" style):
     *  - .zso is compressed, so the 7-Zip ISO reader can't parse its content at all;
     *  - the 7-Zip native library can fail to load on modern Windows (or lose its
     *    temp-dir lock to another instance), which makes every real ISO fail to
     *    open. Rather than detecting zero games and flooding the debug log,
     *    degrade to the filename in that case too.
     */
    public static String getPS2GameIDFromArchive(String archiveFile) throws Exception {
        if (archiveFile.toLowerCase().endsWith(".zso")) {
            return extractGameIDFromFilename(new File(archiveFile).getName());
        }

        IInArchive archive = null;
        RandomAccessFile randomAccessFile = null;
        String theGameID = null;
        try {
            randomAccessFile = new RandomAccessFile(archiveFile, "r");
            archive = SevenZip.openInArchive(ArchiveFormat.ISO, new RandomAccessFileInStream(randomAccessFile));

            for (int i = 0; i < archive.getNumberOfItems(); i++) {
                String gameID = archive.getStringProperty(i, PropID.PATH);
                for (String regionCode : RegionCodes.ALL) {
                    if (gameID.contains(regionCode)) {
                        theGameID = gameID;
                    }
                }
            }
        } catch (Exception ex) {
            // 7-Zip couldn't open the archive (broken native lib, temp-dir lock lost to another
            // instance, or a damaged ISO). Try the filename before giving up so a
            // "<id>.<name>.iso" still gets detected instead of flooding the debug log.
            String fromName = extractGameIDFromFilename(new File(archiveFile).getName());
            if (fromName == null) {
                PopsGameManager.displayErrorMessageDebug(
                        "7-Zip could not open " + new File(archiveFile).getName() + " and its name has no game ID: " + ex);
            }
            theGameID = fromName;
        } finally {
            if (archive != null) {
                try {
                    archive.close();
                } catch (Exception ignored) {
                }
            }
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (Exception ignored) {
                }
            }
        }

        return theGameID;
    }

    /**
     * Extracts a PS2 game ID (e.g. SLUS_215.93) directly from a filename, for
     * formats (like .zso) whose compressed content
     * {@link #getPS2GameIDFromArchive} can't read the archive listing from.
     */
    public static String extractGameIDFromFilename(String filename) {
        for (String regionCode : RegionCodes.ALL) {
            int index = filename.indexOf(regionCode);
            if (index != -1) {
                Matcher matcher = Pattern.compile(Pattern.quote(regionCode) + "\\d{3}\\.\\d{2}").matcher(filename.substring(index));
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }
        return null;
    }

    /** This searches the VCD file for the game's unique identifier string. */
    public static String getPS1GameIDFromVCD(File vcdfile) throws Exception {
        String theGameID;
        try (FileReader fileReader = new FileReader(vcdfile); BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            String line;
            boolean gameIDFound = false;
            theGameID = null;

            // Check each line for the game's region code and unique ID
            while ((line = bufferedReader.readLine()) != null && !gameIDFound) {
                for (String code : RegionCodes.ALL) {
                    if (line.contains(code)) {
                        gameIDFound = true;
                        String[] parts = line.split("_");
                        String regionCode = parts[0].substring(parts[0].length() - 4);
                        String idNumber = truncate(parts[1], 6);

                        // If the game ID does not contain a decimal
                        if (!idNumber.contains(".")) {
                            idNumber = truncate(idNumber, 5); // Remove empty char at the end of the string
                            String afterDecimal = idNumber.substring(idNumber.length() - 2); // Get the last 2 digits
                            idNumber = truncate(idNumber, 3); // Get the first 3 digits
                            idNumber += "." + afterDecimal; // Place a decimal between the digits
                        }
                        theGameID = regionCode + "_" + idNumber;
                    }
                }
            }
        }

        return theGameID;
    }

    /**
     * Strip a game ID from a filename base (extension already removed),
     * whether it sits at the start ("&lt;id&gt;.&lt;name&gt;"), the end
     * ("&lt;name&gt;.&lt;id&gt;", "&lt;name&gt; &lt;id&gt;",
     * "&lt;name&gt;-&lt;id&gt;") or in a "(&lt;id&gt;)" / "[&lt;id&gt;]"
     * group, together with its adjoining separator. Lets the scanner accept
     * OPL's ID-first and ID-last conventions.
     */
    public static String stripGameId(String base, String gameId) {
        if (base == null || gameId == null || gameId.isEmpty()) {
            return base;
        }
        String q = Pattern.quote(gameId);
        String s = base
                .replaceAll("[\\s._-]*[\\(\\[]\\s*" + q + "\\s*[\\)\\]]", "")
                .replaceAll("^" + q + "[\\s._-]+", "")
                .replaceAll("[\\s._-]+" + q + "$", "")
                .replaceAll("^" + q + "$", "");
        return s.trim();
    }

    /**
     * Derives a descriptive game name from a filename and its game ID:
     * strips the extension, then strips the ID (and its separator) via
     * {@link #stripGameId}, falling back to the ID - or, if there isn't one,
     * the extension-stripped filename - when nothing descriptive is left
     * (e.g. a file named just "&lt;id&gt;.vcd").
     */
    public static String deriveDescriptiveName(String fileName, String gameId) {
        int extDot = fileName.lastIndexOf('.');
        String nameWithoutExt = extDot > 0 ? fileName.substring(0, extDot) : fileName;
        String gameName = gameId != null ? stripGameId(nameWithoutExt, gameId) : nameWithoutExt;
        if (gameName.isEmpty()) {
            gameName = gameId != null ? gameId : nameWithoutExt;
        }
        return gameName;
    }

    private static String truncate(String value, int length) {
        return value.length() > length ? value.substring(0, length) : value;
    }
}

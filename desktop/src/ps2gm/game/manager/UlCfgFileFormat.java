package ps2gm.game.manager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes OPL's ul.cfg - the USB-Advance mode game list, a flat file
 * of fixed 64-byte records (name, ID, part count, then reserved/media bytes).
 *
 * Extracted from USBUtil; readULCFG/writeULCFG/padName/listULFragments were
 * previously static methods there.
 */
public final class UlCfgFileFormat {

    private static final int RECORD_SIZE = 64;
    private static final int NAME_LENGTH = 32;
    private static final int ID_OFFSET = 32;
    private static final int ID_LENGTH = 14;
    private static final int PARTS_OFFSET = 47;

    private UlCfgFileFormat() {}

    /** Reads ul.cfg from the OPL directory into a list of UL games. */
    public static List<Game> read() {
        List<Game> ulGameList = new ArrayList<>();

        File cfgFile = new File(PopsGameManager.getOPLFolder() + File.separator + "ul.cfg");
        Path path = Paths.get(cfgFile.getAbsolutePath());

        if (cfgFile.exists() && cfgFile.isFile()) {
            try {
                byte[] data = Files.readAllBytes(path);
                byte[] gameName = new byte[NAME_LENGTH];
                byte[] gameID = new byte[ID_LENGTH];
                byte[] gameParts = new byte[1];
                int charNumber = 0;

                for (int i = 0; i < data.length; i++) {

                    if (charNumber < NAME_LENGTH) {gameName[charNumber] = data[i];}
                    else if (charNumber < ID_OFFSET + ID_LENGTH) {gameID[charNumber - ID_OFFSET] = data[i];}
                    else if (charNumber == PARTS_OFFSET) {gameParts[0] = data[i];}

                    charNumber++;
                    if (charNumber == RECORD_SIZE) {

                        // List all of the UL Game fragments that are in the DVD folder
                        List<File> ulGameFragments = listULFragments();

                        // Check for each of the game fragments in the DVD folder in order to determine the game size
                        long totalGameSize = 0;
                        int filesFound = 0;
                        for (int count = 0; count < gameParts[0]; count++) {
                            for (File gameFragment : ulGameFragments) {
                                if (gameFragment.getName().contains(new String(gameID).substring(3, new String(gameID).length()))
                                        && gameFragment.getName().substring(gameFragment.getName().length() - 2, gameFragment.getName().length()).equals("0" + count)) {
                                    totalGameSize += gameFragment.length();
                                    filesFound++;
                                }
                            }
                        }

                        // If any of the fragments are missing, this sets the game size to zero (game size would be inaccurate if a single fragment is missing)
                        if (filesFound != gameParts[0]) {totalGameSize = 0;}

                        // Get the game name and the game ID without the leading "ul." and with the trailing whitespace removed
                        String name = new String(gameName).trim();
                        String id = new String(gameID).substring(3, new String(gameID).length()).trim();

                        // If the game name contains the game ID, this removes the game Id from the start of the string
                        if (name.contains(id)) {name = name.substring(12, name.length());}

                        // Create a new UL Game object and specify the number of fragments that the game contains
                        Game newULGame = new Game(name, id, "ul.cfg", PopsGameManager.bytesToHuman(totalGameSize), totalGameSize);
                        newULGame.setULGame(true);
                        newULGame.setNumberOfParts(gameParts[0]);

                        ulGameList.add(newULGame);
                        charNumber = 0;
                    }
                }

            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
        }

        return ulGameList;
    }

    /** Writes ul.cfg to the OPL directory from the given UL games. */
    public static void write(List<Game> ulGameList) {
        // BUG FIX (Phase 1): previously wrote each byte via Byte.parseByte(String.valueOf(num)),
        // which throws for any control-byte value >= 128 (byte's range is -128..127).
        // outputStream.write(int) already only uses the low 8 bits, so writing num
        // directly is both correct and simpler.
        try (OutputStream outputStream = new FileOutputStream(PopsGameManager.getOPLFolder() + File.separator + "ul.cfg")) {
            for (Game ulGame : ulGameList) {

                // Get the game name and pad it if the length is less than 32 bytes
                String gameName = ulGame.getGameName();

                if (gameName.length() <= NAME_LENGTH) {
                    gameName = padName(gameName);

                    // Add the additional 32 bytes, which consists of Desc:0, Parts:NumOfParts, Media:14, Info:4x0, 1x8, 10x0
                    String confLine = gameName + "ul." + ulGame.getGameID() + (char) 0 + (char) ulGame.getNumberOfParts() + (char) 20;
                    for (int i = 0; i < 15; i++) {if (i == 4) {confLine += (char) 8;} else {confLine += (char) 0;}}

                    for (char ch : confLine.toCharArray()) {
                        outputStream.write((int) ch);
                    }
                }
            }
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    /** Pads a game name to {@code NAME_LENGTH} bytes with NUL chars. */
    public static String padName(String name) {
        StringBuilder padded = new StringBuilder(name);
        while (padded.length() < NAME_LENGTH) {
            padded.append((char) 0);
        }
        return padded.toString();
    }

    /** Lists the UL game fragment files (named "*.NN") in the OPL directory. */
    public static List<File> listULFragments() {
        List<File> ulGameFragments = new ArrayList<>();
        File dvdFolder = new File(PopsGameManager.getOPLFolder());
        if (dvdFolder.exists() && dvdFolder.isDirectory()) {
            File[] listOfFiles = dvdFolder.listFiles();
            for (File file : listOfFiles) {
                if (file.isFile() && file.getName().length() > 3 && file.getName().contains(".")) {
                    String extension = file.getName().substring(file.getName().lastIndexOf('.') + 1, file.getName().length());
                    if (extension.length() == 2 && extension.chars().allMatch(Character::isDigit)) {
                        ulGameFragments.add(file);
                    }
                }
            }
        }
        return ulGameFragments;
    }
}

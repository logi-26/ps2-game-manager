package ps2gm.game.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Encrypted persistence of the PS1/PS2 game lists (gameListPS1/PS2.dat) and
 * the "bad game" list (games whose ID couldn't be detected).
 *
 * Extracted from GameListManager.
 */
public final class GameListPersistence {

    private static final File keyFilePS1 = new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "data_1");
    private static final File keyFilePS2 = new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "data_2");
    private static final File gameListFilePS1 = new File(PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator + "gameListPS1");
    private static final File gameListFilePS2 = new File(PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator + "gameListPS2");

    private static final File badGameListTextFile = new File(PopsGameManager.getCurrentDirectory() + File.separator + "invalidGameList.txt");
    private static final File badGameListEncryptedFile = new File(PopsGameManager.getCurrentDirectory() + File.separator + "invalidGameList");
    private static final File keyFileBadGames = new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "data_5");

    private GameListPersistence() {}

    /** The games read from a game list, plus their combined raw size (bytes). */
    public record ReadResult(List<Game> games, long totalRawSize) {}

    public static File gameListFilePS1() {
        return gameListFilePS1;
    }

    /** Encrypts and writes {@code gameList} to its gameListPSx.dat file, returning whether the file now exists. */
    public static boolean writeGameListFile(String console, List<Game> gameList) {
        File file = console.equals("PS1") ? gameListFilePS1 : gameListFilePS2;
        File key = console.equals("PS1") ? keyFilePS1 : keyFilePS2;

        List<String> lines = new ArrayList<>();
        for (Game game : gameList) {
            lines.add(game.getGameID() + "," + game.getGameRawSize() + "," + game.getGameName());
        }

        FileEncryptor encryptor = new FileEncryptor();
        encryptor.EncryptData(lines, file.getAbsolutePath(), key.getAbsolutePath());

        return file.exists() && file.isFile();
    }

    /** Decrypts and reads a gameListPSx.dat-style file into games + their combined raw size. */
    public static ReadResult readGameListFile(String console, File file) throws IOException {
        File key = console.equals("PS1") ? keyFilePS1 : keyFilePS2;
        FileEncryptor encryptor = new FileEncryptor();
        List<String> decryptedList = encryptor.DecryptData(file.getAbsolutePath(), key.getAbsolutePath());

        List<Game> games = new ArrayList<>();
        long totalSize = 0;

        for (String line : decryptedList) {
            String[] splitLines = line.split(",");
            String gameID = splitLines[0];
            String gameName = splitLines[2];
            long gameRawSize = Long.parseLong(splitLines[1]);
            totalSize += gameRawSize;

            if (console.equals("PS1")) {
                // This sets the PS1 game compatability values from the text file within the resources
                PS1CompatibilityLookup.Compatibility compat = PS1CompatibilityLookup.lookup(gameID);

                Game selectedGame = new Game(gameName, gameID, "GAME PATH HERE!!!!", PopsGameManager.bytesToHuman(gameRawSize), gameRawSize);
                // BUG FIX: these were swapped - setCompatibleHDD was getting the USB flag and
                // vice versa. The compat-list file's own column labels ("-USB=", "-HDD=", "-SMB=")
                // are unambiguous about which is which.
                selectedGame.setCompatibleHDD(compat.hdd());
                selectedGame.setCompatibleUSB(compat.usb());
                selectedGame.setCompatibleSMB(compat.smb());

                games.add(selectedGame);
            } else {
                games.add(new Game(gameName, gameID, "GAME PATH HERE!!!!", PopsGameManager.bytesToHuman(gameRawSize), gameRawSize));
            }
        }

        return new ReadResult(games, totalSize);
    }

    // ---- bad-game list (games whose ID couldn't be detected) --------------------

    /** Encrypts the invalidGameList.txt file. */
    public static void encryptBadGameListFile() {
        try {
            List<String> list = Files.readAllLines(badGameListTextFile.toPath(), Charset.defaultCharset());
            FileEncryptor encryptor = new FileEncryptor();
            encryptor.EncryptData(list, badGameListEncryptedFile.getAbsolutePath(), keyFileBadGames.getAbsolutePath());
            if (badGameListTextFile.exists() && badGameListTextFile.isFile()) {
                badGameListTextFile.delete();
            }
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    private static void decryptBadGameListFile() {
        if (new File(badGameListEncryptedFile.getAbsolutePath()).exists() && new File(badGameListEncryptedFile.getAbsolutePath()).isFile()) {
            try {
                FileEncryptor encryptor = new FileEncryptor();
                List<String> list = encryptor.DecryptData(badGameListEncryptedFile.getAbsolutePath(), keyFileBadGames.getAbsolutePath());
                Files.write(Paths.get(PopsGameManager.getCurrentDirectory() + File.separator + "invalidGameList.txt"), list, Charset.defaultCharset());
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
        }
    }

    /** Reads the invalidGameList.txt file (decrypting/re-encrypting it around the read). */
    public static List<String> readBadGameListFile() {
        List<String> fileList = new ArrayList<>();
        if (badGameListEncryptedFile.exists() && !badGameListEncryptedFile.isDirectory()) {
            decryptBadGameListFile();
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(badGameListTextFile.getAbsolutePath()))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    fileList.add(line);
                }
            } catch (FileNotFoundException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
            encryptBadGameListFile();
        }
        return fileList;
    }

    /** Writes invalidGameList.txt from the VCD/ISO files whose game ID couldn't be detected. */
    public static void createBadGameListFile(File[] vcdFiles, File[] isoFiles) {

        List<String> badGameList = new ArrayList<>();

        if (vcdFiles != null) {
            for (File vcdFile : vcdFiles) {
                String gameID = null;
                try {
                    gameID = GameIdExtractor.getPS1GameIDFromVCD(vcdFile);
                } catch (Exception ex) {
                    PopsGameManager.displayErrorMessageDebug(ex.toString());
                }
                if (gameID == null) {
                    badGameList.add(vcdFile.getName());
                }
            }
        }

        if (isoFiles != null) {
            for (File isoFile : isoFiles) {
                String gameID = null;
                try {
                    gameID = GameIdExtractor.getPS2GameIDFromArchive(isoFile.getAbsolutePath());
                } catch (Exception ex) {
                    PopsGameManager.displayErrorMessageDebug(ex.toString());
                }
                if (gameID == null) {
                    badGameList.add(isoFile.getName());
                }
            }
        }

        try (FileWriter writer = new FileWriter(badGameListTextFile, StandardCharsets.UTF_8)) {
            for (String badGameName : badGameList) {
                writer.write(badGameName);
                writer.write(System.lineSeparator());
            }
            encryptBadGameListFile();
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }
}

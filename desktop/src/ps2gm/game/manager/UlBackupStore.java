package ps2gm.game.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The encrypted ul-backup sidecar file: records each UL game's original name,
 * hex name and fragment count so ul.cfg can be regenerated from the fragment
 * files actually present in the OPL directory.
 *
 * Extracted from USBUtil; writeULBackupFile/regenerateULCFG/encryptBackupFile/
 * decryptBackupFile were previously static methods there.
 */
public final class UlBackupStore {

    private static final File UL_BACKUP_TXT = new File(PopsGameManager.getCurrentDirectory() + File.separator + "ul-backup.txt");
    private static final File UL_BACKUP_FILE = new File(PopsGameManager.getCurrentDirectory() + File.separator + "ul-backup");
    private static final File KEY_FILE_BACKUP = new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "data_4");

    private UlBackupStore() {}

    /** One entry from the ul-backup file. */
    public record BackupGame(String hexName, String originalName, String gameID, int numberOfFragments, long gameRawSize) {}

    /** Records a newly-split UL game in the backup file (creating/decrypting/re-encrypting it as needed). */
    public static void record(byte[] originalName, String hexName, String gameID, int numberOfFragments, long gameRawSize) {

        decrypt();

        if (!UL_BACKUP_TXT.exists() || !UL_BACKUP_TXT.isFile()) {
            try {
                UL_BACKUP_TXT.createNewFile();
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
        }

        // Read the ul-backup file to make sure the data is not already in the file
        boolean gameInBackupFile = false;
        try (BufferedReader br = new BufferedReader(new FileReader(UL_BACKUP_TXT))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains(hexName) && line.contains(gameID)) {
                    gameInBackupFile = true;
                }
            }
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }

        // Append the new game to the ul-backup file
        if (!gameInBackupFile) {
            try (PrintWriter output = new PrintWriter(new FileWriter(UL_BACKUP_TXT, true))) {
                output.printf("%s-%s*%s(%s)-%s\n", hexName, new String(originalName), gameID, numberOfFragments, gameRawSize);
            } catch (Exception ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
        }

        encrypt();
    }

    /** Reconciles the backup file against the fragments actually present in the OPL directory, and rewrites ul.cfg. */
    public static void regenerateULCFG() {

        List<File> ulGameFragments = UlCfgFileFormat.listULFragments();
        List<Game> ulCompleteGames = new ArrayList<>();
        List<BackupGame> backupGameList = new ArrayList<>();

        decrypt();

        if (UL_BACKUP_TXT.exists() && UL_BACKUP_TXT.isFile()) {
            try (BufferedReader br = new BufferedReader(new FileReader(UL_BACKUP_TXT))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.equals("")) {
                        backupGameList.add(new BackupGame(
                                line.substring(0, 8),
                                line.substring(9, line.lastIndexOf('*')),
                                line.substring(line.lastIndexOf('*') + 1, line.lastIndexOf("(")),
                                Integer.parseInt(line.substring(line.lastIndexOf("(") + 1, line.lastIndexOf("(") + 2)),
                                Long.parseLong(line.substring(line.lastIndexOf("-") + 1, line.length()))));
                    }
                }
            } catch (FileNotFoundException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
        }

        // Loop through all of the game data in the ul-backup file to try and determine the games original name and the number of fragments
        backupGameList.forEach((backupGame) -> {
            // Check for each of the game fragments in the DVD folder in order to determine the game size
            int filesFound = 0;
            for (int count = 0; count < backupGame.numberOfFragments(); count++) {
                for (File gameFragment : ulGameFragments) {
                    if (gameFragment.getName().contains(backupGame.gameID())
                            && gameFragment.getName().substring(gameFragment.getName().length() - 2, gameFragment.getName().length()).equals("0" + count)) {
                        filesFound++;
                    }
                }
            }
            // If all of the fragments are available, this adds the game to the UL Complete Game List
            if (filesFound == backupGame.numberOfFragments()) {
                Game completeBackupGame = new Game(backupGame.originalName(), backupGame.gameID(), "PATH", PopsGameManager.bytesToHuman(backupGame.gameRawSize()), backupGame.gameRawSize());
                completeBackupGame.setULGame(true);
                completeBackupGame.setNumberOfParts(backupGame.numberOfFragments());
                ulCompleteGames.add(completeBackupGame);
            }
        });

        encrypt();

        UlCfgFileFormat.write(ulCompleteGames);
    }

    private static void encrypt() {
        try {
            List<String> list = Files.readAllLines(UL_BACKUP_TXT.toPath(), Charset.defaultCharset());
            FileEncryptor encryptor = new FileEncryptor();
            encryptor.EncryptData(list, UL_BACKUP_FILE.getAbsolutePath(), KEY_FILE_BACKUP.getAbsolutePath());
            if (UL_BACKUP_TXT.exists() && UL_BACKUP_TXT.isFile()) {
                UL_BACKUP_TXT.delete();
            }
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    private static void decrypt() {
        if (new File(UL_BACKUP_FILE.getAbsolutePath()).exists() && new File(UL_BACKUP_FILE.getAbsolutePath()).isFile()) {
            try {
                FileEncryptor encryptor = new FileEncryptor();
                List<String> list = encryptor.DecryptData(UL_BACKUP_FILE.getAbsolutePath(), KEY_FILE_BACKUP.getAbsolutePath());
                Files.write(Paths.get(PopsGameManager.getCurrentDirectory() + File.separator + "ul-backup.txt"), list, Charset.defaultCharset());
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
        }
    }
}

package ps2gm.game.manager;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Deletes files (local and/or remote, depending on the current mode) from an
 * OPL subdirectory such as ART/CFG/CHT/VMC.
 */
public final class RemoteFileCleaner {

    private RemoteFileCleaner() {}

    // Deletes every file in specific directory, local and/or remote depending on the current mode
    public static void deleteAllFiles(String directory) {

        if (PopsGameManager.getCurrentMode() == Mode.SMB || PopsGameManager.getCurrentMode() == Mode.HDD_USB) {

            File selectedFolder = new File(PopsGameManager.getOPLFolder() + File.separator + directory);
            if (selectedFolder.exists() && selectedFolder.isDirectory()) {
                deleteAllLocalFiles(selectedFolder);
                PopsGameManager.callbackToUpdateGUIGameList(null, -1);
            }
        } else if (PopsGameManager.getCurrentMode() == Mode.HDD) {

            MyFTPClient myFTP = new MyFTPClient();

            if (PopsGameManager.getPS2IP() != null) {
                if (myFTP.connectToConsole(PopsGameManager.getPS2IP())) {

                    String drive = resolveDrive();
                    List<String> remoteDirectoryList = myFTP.listRemoteDirectory("/" + drive + "/" + GameListManager.getFormattedOPLPartition() + "/" + directory, "+OPL", true);

                    if (remoteDirectoryList != null && remoteDirectoryList.size() > 0) {
                        for (String file : remoteDirectoryList) {
                            myFTP.deleteRemoteFile("/" + drive + "/" + GameListManager.getFormattedOPLPartition() + "/" + directory + "/" + file);
                        }
                        PopsGameManager.callbackToUpdateGUIGameList(null, -1);
                    }

                    myFTP.disconnectFromConsole();
                }

                File selectedFolder = new File(PopsGameManager.getOPLFolder() + File.separator + directory);
                if (selectedFolder.exists() && selectedFolder.isDirectory()) {
                    deleteAllLocalFiles(selectedFolder);
                }

                PopsGameManager.callbackToUpdateGUIGameList(null, -1);
            }
        }
    }

    // Delete files that no game in the current PS1/PS2 list still references
    public static void deleteUnusedFiles(String directory) {

        // Get the PS1 and PS2 game lists from the files
        File ps1GameList = new File(PopsGameManager.getOPLFolder() + File.separator + "gameListPS1");
        File ps2GameList = new File(PopsGameManager.getOPLFolder() + File.separator + "gameListPS2");

        if (ps1GameList.exists() && ps1GameList.isFile()) {
            try {
                GameListManager.createGameListFromFile(Console.PS1, new File(PopsGameManager.getOPLFolder() + File.separator + "gameListPS1"));
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug("Error creating PS1 game list from file!\n\n" + ex.toString());
            }
        }
        if (ps2GameList.exists() && ps2GameList.isFile()) {
            try {
                GameListManager.createGameListFromFile(Console.PS2, new File(PopsGameManager.getOPLFolder() + File.separator + "gameListPS2"));
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug("Error creating PS2 game list from file!\n\n" + ex.toString());
            }
        }

        // Add all of the game ID's from the PS1 and PS2 game list into a single list
        List<String> allGameList = new ArrayList<>();
        GameListManager.getGameListPS1().forEach((game) -> allGameList.add(game.getGameID()));
        GameListManager.getGameListPS2().forEach((game) -> allGameList.add(game.getGameID()));

        if (PopsGameManager.getCurrentMode() == Mode.SMB || PopsGameManager.getCurrentMode() == Mode.HDD_USB) {

            File selectedFolder = new File(PopsGameManager.getOPLFolder() + File.separator + directory);
            if (selectedFolder.exists() && selectedFolder.isDirectory()) {
                deleteUnreferencedLocalFiles(selectedFolder, allGameList);
            }
        } else if (PopsGameManager.getCurrentMode() == Mode.HDD) {

            MyFTPClient myFTP = new MyFTPClient();

            if (PopsGameManager.getPS2IP() != null) {
                if (myFTP.connectToConsole(PopsGameManager.getPS2IP())) {

                    String drive = resolveDrive();
                    List<String> remoteDirectoryList = myFTP.listRemoteDirectory("/" + drive + "/" + GameListManager.getFormattedOPLPartition() + "/" + directory, "+OPL", true);

                    if (remoteDirectoryList != null && remoteDirectoryList.size() > 0) {
                        List<String> deleteFileList = new ArrayList<>();
                        remoteDirectoryList.forEach((file) -> {
                            boolean fileInUse = false;
                            for (String idInList : allGameList) {
                                if (file.contains(idInList)) {
                                    fileInUse = true;
                                }
                            }
                            if (!fileInUse) {
                                deleteFileList.add(file);
                            }
                        });
                        for (String fileToDelete : deleteFileList) {
                            myFTP.deleteRemoteFile("/" + drive + "/" + GameListManager.getFormattedOPLPartition() + "/" + directory + "/" + fileToDelete);
                        }
                    }
                    myFTP.disconnectFromConsole();
                }

                File selectedFolder = new File(PopsGameManager.getOPLFolder() + File.separator + directory);
                if (selectedFolder.exists() && selectedFolder.isDirectory()) {
                    deleteUnreferencedLocalFiles(selectedFolder, allGameList);
                }
                PopsGameManager.callbackToUpdateGUIGameList(null, -1);
            }
        }
    }

    private static String resolveDrive() {
        if (GameListManager.getFormattedOPLDrive().equals("hdd")) {
            return "pfs";
        }
        return "mass";
    }

    private static void deleteAllLocalFiles(File folder) {
        try {
            Files.walkFileTree(Paths.get(folder.getAbsolutePath()), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    // Deletes files whose name doesn't contain any ID in {@code referencedIds}
    private static void deleteUnreferencedLocalFiles(File folder, List<String> referencedIds) {
        File[] localDirectoryList = folder.listFiles();
        if (localDirectoryList == null) {
            return;
        }

        List<String> deleteFileList = new ArrayList<>();
        for (File localFile : localDirectoryList) {
            boolean fileInUse = false;
            for (String idInList : referencedIds) {
                if (idInList != null && localFile.getName() != null && localFile.getName().contains(idInList)) {
                    fileInUse = true;
                }
            }
            if (!fileInUse) {
                deleteFileList.add(localFile.getAbsolutePath());
            }
        }

        deleteFileList.stream().filter((fileToDelete) -> (new File(fileToDelete).exists())).forEachOrdered((fileToDelete) -> {new File(fileToDelete).delete();});
    }
}

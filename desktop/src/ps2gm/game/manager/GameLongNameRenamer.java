package ps2gm.game.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The file/FTP side of renaming a game whose title is longer than 32 characters -
 * VCD/ELF/ISO, PS1 ART, PS1 CFG, multi-disc DISCS.TXT, game folder, and the FTP
 * path on the console in HDD mode.
 *
 * Extracted verbatim from the Swing {@code GameLongNameScreen} so the screen (Swing
 * now, JavaFX later) only owns the UI: the "name already used" check, calling one of
 * these methods, and refreshing its lists on success.
 */
public final class GameLongNameRenamer {

    private final Game game;
    private final String oldTitle;
    private final String newTitle;

    public GameLongNameRenamer(Game game, String oldTitle, String newTitle) {
        this.game = game;
        this.oldTitle = oldTitle;
        this.newTitle = newTitle;
    }

    // Rename a local PS1 VCD and ELF file (SMB or HDD_USB). @return true if the VCD/ELF rename succeeded.
    public boolean renameLocalPS1(String prefix) {

        File selectedGame = new File(game.getGamePath());
        if (!selectedGame.exists()) {
            return false;
        }

        boolean success;

        // Rename the VCD file
        success = selectedGame.renameTo(new File(selectedGame.getParentFile() + File.separator + newTitle + "-" + game.getGameID() + ".VCD"));

        // Rename the ELF file
        File elfFile = new File(selectedGame.getParent() + File.separator + prefix + selectedGame.getName().substring(0, selectedGame.getName().length() - 3) + "ELF");
        if (elfFile.exists()) {
            success = elfFile.renameTo(new File(selectedGame.getParentFile() + File.separator + prefix + newTitle + "-" + game.getGameID() + ".ELF"));
        }

        // Rename the PS1 game art files
        renameGameART();

        // Rename the PS1 config files
        renameConfigFilesPS1();

        String gameID = game.getGameID();

        // If the game is a multi-disc game, modify the DISCS.TXT file with the new file name
        if (game.getMultiDiscGame()) {
            renameMultiDiscPS1(game);
        } else {
            // Rename the game folder if it exists
            File gameFolder = new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + oldTitle + "-" + gameID);
            if (gameFolder.exists() && gameFolder.isDirectory()) {
                gameFolder.renameTo(new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + newTitle + "-" + gameID));
            }
        }

        return success;
    }

    // Rename a local PS2 ISO (SMB or HDD_USB). @return true if the ISO rename succeeded.
    public boolean renameLocalPS2() {

        File selectedGame = new File(game.getGamePath());
        if (!selectedGame.exists()) {
            return false;
        }

        // Rename the ISO/ZSO file, preserving whichever extension it actually has.
        // The game ID goes at the start or the end of the name per the user's
        // "PS2 Game ID Position" setting (OPL reads the ID from the ISO itself).
        String extension = selectedGame.getName().substring(selectedGame.getName().length() - 4);
        String base = "start".equals(PopsGameManager.getGameIDPositionPS2())
                ? game.getGameID() + "." + newTitle
                : newTitle + "." + game.getGameID();
        return selectedGame.renameTo(new File(selectedGame.getParentFile() + File.separator + base + extension));
    }

    // HDD mode (PS1): rename the VCD and ELF on the console over FTP. Fire and forget.
    public void ftpRenamePS1() {

        MyFTPClient myFTP = new MyFTPClient();

        // Connect to the PS2 console to rename the VCD file
        if (myFTP.connectToConsole(PopsGameManager.getPS2IP())) {

            String remoteDrive = GameListManager.getFormattedVCDDrive();
            if (remoteDrive.equals("hdd")) { remoteDrive = "pfs"; }

            // Check if the VCD file is still on the console
            List<String> remoteDirectoryList = myFTP.listRemoteDirectory("/pfs/0/", "__.POPS", true);
            boolean vcdOnConsole = false;
            for (String arrayItem : remoteDirectoryList) {
                if (arrayItem.equals(oldTitle + "-" + game.getGameID() + ".VCD")) { vcdOnConsole = true; }
            }

            // Rename the VCD file on the console
            if (vcdOnConsole) {
                myFTP.renameFile("/pfs/0/" + oldTitle + "-" + game.getGameID() + ".VCD", "/pfs/0/" + newTitle + "-" + game.getGameID() + ".VCD");
            }

            myFTP.disconnectFromConsole();
        }

        // Connect to the PS2 console to rename the ELF file
        if (myFTP.connectToConsole(PopsGameManager.getPS2IP())) {

            String remoteDrive = GameListManager.getFormattedELFDrive();
            if (remoteDrive.equals("hdd")) { remoteDrive = "pfs"; }

            // Check if the ELF file is still on the console
            List<String> remoteDirectoryList = myFTP.listRemoteDirectory("/pfs/0/APPS/", "+OPL", true);
            boolean elfOnConsole = false;
            for (String arrayItem : remoteDirectoryList) {
                if (arrayItem.equals(oldTitle + "-" + game.getGameID() + ".ELF")) { elfOnConsole = true; }
            }

            // Rename the ELF file on the console
            if (elfOnConsole) {
                myFTP.renameFile("/pfs/0/" + oldTitle + "-" + game.getGameID() + ".ELF", "/pfs/0/" + newTitle + "-" + game.getGameID() + ".ELF");
            }

            myFTP.disconnectFromConsole();
        }
    }

    // This renames the PS1 ART files if they exist (PS2 ART does not contain the game name)
    private void renameGameART() {

        switch (PopsGameManager.getCurrentMode()) {
            case SMB:
                renameArtPS1("SB.", "COV");
                renameArtPS1("SB.", "COV2");
                renameArtPS1("SB.", "BG");
                renameArtPS1("SB.", "ICO");
                renameArtPS1("SB.", "SCR");
                renameArtPS1("SB.", "SCR2");
                break;
            case HDD_USB:
                renameArtPS1("XX.", "COV");
                renameArtPS1("XX.", "COV2");
                renameArtPS1("XX.", "BG");
                renameArtPS1("XX.", "ICO");
                renameArtPS1("XX.", "SCR");
                renameArtPS1("XX.", "SCR2");
                break;
            case HDD:
                break;
        }
    }

    // This renames the ART
    private void renameArtPS1(String prefix, String coverType) {

        String artFolder = PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator;
        String gameID = game.getGameID();

        // Rename art file if it exists
        if (coverType.equals("ICO")) {
            File frontCover = new File(artFolder + prefix + oldTitle + "-" + gameID + ".ELF_" + coverType + ".png");
            if (frontCover.exists() && frontCover.isFile()) {
                frontCover.renameTo(new File(artFolder + prefix + newTitle + "-" + gameID + ".ELF_" + coverType + ".png"));
            }
        } else {
            File frontCover = new File(artFolder + prefix + oldTitle + "-" + gameID + ".ELF_" + coverType + ".jpg");
            if (frontCover.exists() && frontCover.isFile()) {
                frontCover.renameTo(new File(artFolder + prefix + newTitle + "-" + gameID + ".ELF_" + coverType + ".jpg"));
            }
        }
    }

    // Rename the PS1 config files
    private void renameConfigFilesPS1() {

        String gameID = game.getGameID();
        File folder = new File(PopsGameManager.getOPLFolder() + File.separator + "CFG");
        File[] listOfFiles = folder.listFiles();

        for (File file : listOfFiles) {
            if (file.isFile()) {
                if (file.getName().equals("SB." + oldTitle + "-" + gameID + ".ELF.cfg")) {
                    file.renameTo(new File(file.getParentFile() + File.separator + "SB." + newTitle + "-" + gameID + ".ELF.cfg"));
                }

                if (file.getName().equals("XX." + oldTitle + "-" + gameID + ".ELF.cfg")) {
                    file.renameTo(new File(file.getParentFile() + File.separator + "XX." + newTitle + "-" + gameID + ".ELF.cfg"));
                }
            }
        }
    }

    // Modify the DISCS.TXT file with the new file name
    private void renameMultiDiscPS1(Game selectedGame) {

        File discsFolder = new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + selectedGame.getGameName() + "-" + selectedGame.getGameID());
        File discsFile = new File(discsFolder + File.separator + "DISCS.TXT");

        ArrayList<File> otherFolderList = new ArrayList<>();

        // Check the DISCS.TXT file for the game that is being renamed
        if (discsFile.exists() && discsFile.isFile()) {

            // Store each of the game names from the text file into a list
            ArrayList<String> discsInFile = new ArrayList<>();

            // Replace the game name that is in the DISCS.TXT file
            try (BufferedReader br = new BufferedReader(new FileReader(discsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.equals(selectedGame.getGameName() + "-" + selectedGame.getGameID() + ".VCD")) {
                        discsInFile.add(newTitle + "-" + selectedGame.getGameID() + ".VCD");
                    } else {
                        discsInFile.add(line);
                    }
                }
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }

            // Write the new modified text file using the data in the array list
            if (!discsInFile.isEmpty()) {
                try {
                    Files.write(Paths.get(discsFile.getAbsolutePath()), discsInFile, Charset.defaultCharset());
                } catch (IOException ex) {
                    PopsGameManager.displayErrorMessageDebug(ex.toString());
                }
            }

            String gameID = game.getGameID();

            // Rename the folder that contains the DISCS.TXT file
            discsFolder.renameTo(new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + newTitle + "-" + gameID));

            // Using the DISCS.TXT file, determine if there are any other game folders for the other discs in this mult-disc collection
            if (discsInFile.size() > 1) {
                discsInFile.stream().filter((arrayItem) -> (!arrayItem.equals(newTitle + "-" + selectedGame.getGameID() + ".VCD"))).forEachOrdered((arrayItem) -> {
                    otherFolderList.add(new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + arrayItem.substring(0, arrayItem.length() - 16)));
                });
            }

            // Modify the DISC.TXT file in all of the other game folders so that they all contain the new game name and not the old game name
            if (!otherFolderList.isEmpty()) {
                for (File folder : otherFolderList) {
                    File otherDiscsFile = new File(folder.getAbsolutePath() + File.separator + "DISCS.TXT");
                    discsInFile.clear();

                    // Replace the game name that is in the DISCS.TXT file
                    try (BufferedReader br = new BufferedReader(new FileReader(otherDiscsFile))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.equals(selectedGame.getGameName() + "-" + selectedGame.getGameID() + ".VCD")) {
                                discsInFile.add(newTitle + "-" + selectedGame.getGameID() + ".VCD");
                            } else {
                                discsInFile.add(line);
                            }
                        }
                    } catch (IOException ex) {
                        PopsGameManager.displayErrorMessageDebug(ex.toString());
                    }

                    // Write the new modified text file using the data in the array list
                    if (!discsInFile.isEmpty()) {
                        try {
                            Files.write(Paths.get(otherDiscsFile.getAbsolutePath()), discsInFile, Charset.defaultCharset());
                        } catch (IOException ex) {
                            PopsGameManager.displayErrorMessageDebug(ex.toString());
                        }
                    }
                }
            }
        }
    }
}

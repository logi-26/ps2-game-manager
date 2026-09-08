package ps2gm.game.manager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GameListManager {

    // <editor-fold defaultstate="collapsed" desc="Private Variables">

    // PS1 lists
    private static List<Game> gameListPS1;
    private static List<File> invalidGameListPS1;

    // PS2 lists
    private static List<Game> gameListPS2;
    private static List<File> invalidGameListPS2;

    // Stores the total game sizes for each console
    private static String totalGameSizeDisplayPS1;
    private static String totalGameSizeDisplayPS2;
    private static long totalGameSizeRawPS1;
    private static long totalGameSizeRawPS2;

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Public Functions">

    // PS1 list functions
    public static void setGameListPS1(List<Game> newGameList) {gameListPS1 = newGameList;}                          // Sets the list containg all of the PS1 games
    public static List<Game> getGameListPS1() {return gameListPS1;}                                                 // Returns a list containg all of the PS1 games
    public static String getGameSizeDisplayTotalPS1() {return totalGameSizeDisplayPS1;}                             // Returns the total size of all PS1 games converted to readable format

    // PS2 list functions
    public static void setGameListPS2(List<Game> newGameList) {gameListPS2 = newGameList;}                          // Sets the list containg all of the PS2 games
    public static List<Game> getGameListPS2() {return gameListPS2;}                                                 // Returns a list containg all of the PS2 games
    public static String getGameSizeDisplayTotalPS2() {return totalGameSizeDisplayPS2;}                             // Returns the total size of all PS2 games converted to readable format

    // This creates the game lists for PS1
    public static void createGameListsPS1() {

        gameListPS1 = new ArrayList<>();
        invalidGameListPS1 = new ArrayList<>();

        try {
            createGameListPS1();
            PopsGameManager.callbackToUpdateGUIGameList(null, 0);
        } catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
    }


    // This creates the game lists for PS2
    public static void createGameListsPS2(boolean checkBadGames) {

        gameListPS2 = new ArrayList<>();
        invalidGameListPS2 = new ArrayList<>();

        try {
            createGameListPS2(checkBadGames);
            PopsGameManager.callbackToUpdateGUIGameList(null, 0);
        } catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
    }


    // Strip a game ID from a filename base - see GameIdExtractor.stripGameId.
    public static String stripGameId(String base, String gameId) {
        return GameIdExtractor.stripGameId(base, gameId);
    }

    // Adds a game to the PS2 game list
    public static void addToGameListsPS2(File isoFile){

        String gameID = null;

        // Try and get the game ID from the ISO
        try {gameID = getPS2GameIDFromArchive(isoFile.getAbsolutePath());} catch (Exception ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}

        // Get the game name without the directory path or file extension and game ID
        String gameName = isoFile.getName();
        if (gameName.toLowerCase().endsWith(".iso") || gameName.toLowerCase().endsWith(".zso")) {gameName = gameName.substring(0, gameName.length() - 4);}
        gameName = stripGameId(gameName, gameID);

        gameListPS2.add(new Game(gameName, gameID, isoFile.toString(), PopsGameManager.bytesToHuman(isoFile.length()), isoFile.length()));
    }



    // Adds a game to the PS2 game list
    public static void addToGameListsPS1(File vcdFile){

        String gameID = null;

        // Try and get the game ID from the ISO
        try {gameID = getPS1GameIDFromVCD(vcdFile);} catch (Exception ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}

        // Get the game name without the directory path or file extension and game ID
        String gameName = vcdFile.getName();
        if (gameName.contains(".VCD")) {gameName = gameName.replace(".VCD", "");}
        gameName = stripGameId(gameName, gameID);

        gameListPS1.add(new Game(gameName, gameID, vcdFile.toString(), PopsGameManager.bytesToHuman(vcdFile.length()), vcdFile.length()));
    }



    // This gets the PS1 game list from the console using FTP
    public static List<Game> getGameListFromConsolePS1(){
        MyFTPClient ftpClient = new MyFTPClient();
        List<Game> gameList = null;
        if (ftpClient.connectToConsole(PopsGameManager.getPS2IP())){gameList = ftpClient.getGameListPS1();}
        return gameList;
    }


    // This creates the gameListPS2.dat for storing the list of PS2 games that are currently on the console - see GameListPersistence.
    public static boolean writeGameListFilePS2(List<Game> ps2GameList){
        if (ps2GameList == null) {ps2GameList = gameListPS2;}
        return GameListPersistence.writeGameListFile("PS2", ps2GameList);
    }


    // This creates the gameListPS1.dat for storing the list of PS1 games that are currently on the console - see GameListPersistence.
    public static boolean writeGameListFilePS1(List<Game> ps1GameList){
        if (ps1GameList == null) {ps1GameList = gameListPS1;}
        return GameListPersistence.writeGameListFile("PS1", ps1GameList);
    }


    // Creates the game list from a file - see GameListPersistence.
    public static void createGameListFromFile(String console, File file) throws IOException {
        GameListPersistence.ReadResult result = GameListPersistence.readGameListFile(console, file);
        if (console.equals("PS1")) {
            gameListPS1 = result.games();
            totalGameSizeRawPS1 = result.totalRawSize();
            totalGameSizeDisplayPS1 = PopsGameManager.bytesToHuman(result.totalRawSize());
        } else if (console.equals("PS2")) {
            gameListPS2 = result.games();
            totalGameSizeRawPS2 = result.totalRawSize();
            totalGameSizeDisplayPS2 = PopsGameManager.bytesToHuman(result.totalRawSize());
        }
    }


    // This searches the ISO file for the game's unique identifier file - see
    // GameIdExtractor.getPS2GameIDFromArchive for the fallback-to-filename details.
    public static String getPS2GameIDFromArchive(String archiveFile) throws Exception {
        return GameIdExtractor.getPS2GameIDFromArchive(archiveFile);
    }

    // This searches the VCD file for the game's unique identifier string.
    public static String getPS1GameIDFromVCD(File vcdfile) throws Exception {
        return GameIdExtractor.getPS1GameIDFromVCD(vcdfile);
    }


    // Generate the conf_apps file using data from the smb POPS folder, hdd game list and usb game list - see ConfigElmWriter.
    public static void writeConfigELM() {
        ConfigElmWriter.write();
    }


    // This deletes all of the specified files in a directory (remote and local) - see RemoteFileCleaner.
    public static void deleteAllFiles(String directory){
        RemoteFileCleaner.deleteAllFiles(directory);
    }


    // This deletes all of the unused files in a directory (remote and local) - see RemoteFileCleaner.
    public static void deleteUnusedFiles(String directory){
        RemoteFileCleaner.deleteUnusedFiles(directory);
    }


    // Returns the OPL Drive formatted
    public static String getFormattedOPLDrive(){
        return RemotePath.parse(PopsGameManager.getRemoteOPLPath()).drive();
    }

    // Returns the OPL Partition formatted
    public static String getFormattedOPLPartition(){
        return RemotePath.parse(PopsGameManager.getRemoteOPLPath()).partition();
    }

    // Returns the VCD Drive formatted
    public static String getFormattedVCDDrive(){
        return RemotePath.parse(PopsGameManager.getRemoteVCDPath()).drive();
    }

    // Returns the VCD Partition formatted
    public static String getFormattedVCDPartition(){
        return RemotePath.parse(PopsGameManager.getRemoteVCDPath()).partition();
    }

    // Returns the VCD Folder formatted
    public static String getFormattedVCDFolder(){
        return RemotePath.parse(PopsGameManager.getRemoteVCDPath()).folder();
    }

    // Returns the ELF Drive formatted
    public static String getFormattedELFDrive(){
        return RemotePath.parse(PopsGameManager.getRemoteELFPath()).drive();
    }

    // Returns the ELF Partition formatted
    public static String getFormattedELFPartition(){
        return RemotePath.parse(PopsGameManager.getRemoteELFPath()).partition();
    }

    // Returns the ELF Folder formatted
    public static String getFormattedELFFolder(){
        return RemotePath.parse(PopsGameManager.getRemoteELFPath()).folder();
    }

    // This creates the invalidGameList.txt file - see GameListPersistence.
    public static void createBadGameListFile(){
        File[] vcdFiles = null;
        try {vcdFiles = getVCDFiles();} catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
        File[] isoFiles = null;
        try {isoFiles = getISOFiles();} catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
        GameListPersistence.createBadGameListFile(vcdFiles, isoFiles);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Private Functions">

    // This creates the PS1 game lists
    private static void createGameListPS1() throws IOException {

        switch (PopsGameManager.getCurrentMode()) {
            case HDD_USB:
            case SMB:
                createPS1ListFromSMB();
                break;
            case HDD:
                createGameListFromFile("PS1", GameListPersistence.gameListFilePS1());
                break;
            default:
                break;
        }
    }


    // Creates the PS1 game list from SMB (Using the actual files in the folder)
    private static void createPS1ListFromSMB() {

        List<String> storedBadGameList = GameListPersistence.readBadGameListFile();
        List<String> badGameListPS1 = new ArrayList<>();
        long totalSize = 0;
        File [] files = null;
        try {files = getVCDFiles();} catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}

        if (files != null){

            // Loop through the vcd files
            for (File vcdFile : files) {

                String gameID = null;

                // Try and get the game ID from the VCD
                try {gameID = getPS1GameIDFromVCD(vcdFile);} catch (Exception ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}

                // Derive the descriptive game name from the file name - see
                // GameIdExtractor.deriveDescriptiveName. POPS VCDs are named
                // "<name> <id>.vcd", "<name>-<id>.vcd" or just "<id>.vcd" - the last has no
                // descriptive part, so fall back to the ID rather than running off the end of
                // the string (the old fixed "chop last 12 chars" crashed on "<id>.vcd").
                String gamePath = vcdFile.toString();
                String gameName = GameIdExtractor.deriveDescriptiveName(vcdFile.getName(), gameID);

                // Used to calculate the total size of all VCD files combined
                totalSize += vcdFile.length();

                if (gameID != null) {

                    if (gamePath.contains(gameID)) {

                        // This sets the PS1 game compatability values from the text file within the resources
                        PS1CompatibilityLookup.Compatibility compat = PS1CompatibilityLookup.lookup(gameID);

                        // This checks if the game is a multi-disc game
                        String[] multiDiscArray = MultiDiscGameCatalog.siblingDiscsFor(gameID);
                        boolean multiDiscGame = multiDiscArray != null;

                        // Create the game object
                        Game selectedGame = new Game(gameName, gameID, vcdFile.toString(), PopsGameManager.bytesToHuman(vcdFile.length()), vcdFile.length());
                        selectedGame.setCompatibleHDD(compat.hdd());
                        selectedGame.setCompatibleUSB(compat.usb());
                        selectedGame.setCompatibleSMB(compat.smb());

                        if (multiDiscGame){
                            selectedGame.setMultiDiscGame(true);
                            for (String arrayItem : multiDiscArray){selectedGame.addToMultiDiscList(arrayItem);}

                            File gameFolder = new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + selectedGame.getGameName() + "-" + selectedGame.getGameID());

                            // If the game folder does not exist, make the folder and add the DISCS.txt file
                            if (!gameFolder.exists()){
                                gameFolder.mkdir();

                                // Create the DISC.TXT file
                                try(PrintWriter out = new PrintWriter(gameFolder + File.separator + "DISCS.TXT")){
                                    out.println(selectedGame.getGameName() + "-" + selectedGame.getGameID() + ".VCD");
                                } catch (FileNotFoundException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
                            }
                        }

                        gameListPS1.add(selectedGame);
                    }
                    else {invalidGameListPS1.add(vcdFile);}
                }
                else {
                    if (!storedBadGameList.contains(vcdFile.getName())){badGameListPS1.add(vcdFile.getName());}
                }
            }

            // Make sure that the the DISC.TXT file is up-to-date for any of the mult-disc games
            MultiDiscGameCatalog.checkMultiDiscFiles(gameListPS1);

            // Display a message to the user, listing any games were the game ID could not be detected
            if (badGameListPS1.size() > 0) {
                StringBuilder badGames = new StringBuilder();
                badGameListPS1.forEach((game) -> {badGames.append(game).append("\n");});
                PopsGameManager.showWarningDialog("The system was unable to detect the unique game ID for the following PS1 games:\n" + badGames," Unable to Detect Game ID!");
            }

            totalGameSizeRawPS1 = totalSize;
            totalGameSizeDisplayPS1 = PopsGameManager.bytesToHuman(totalSize);
        }

        // If any of the games are not correctly named with the game ID, call the invalid games function
        if (!invalidGameListPS1.isEmpty()) {checkInvalidGamesPS1();}

        // This reorganizes the PS1 game list in alphaetical order
        Collections.sort(gameListPS1, new ListOrganiser());
    }


    // This enables the user to rename any PS1 games that are not correctly named
    private static void checkInvalidGamesPS1(){

        // Ask the user if they want to try and rename the game files
        if (PopsGameManager.confirmDialog("Some of your PS1 games are not correctly named! \n\nDo you want to try and re-name them?"," Incorrect Game Names")){
            // Ported to JavaFX (Swing GameRenamingScreenPS1 kept until the migration lands).
            ps2gm.game.manager.fx.GameRenamingScreen.openPS1(invalidGameListPS1);
        }
    }


    // This creates the PS2 game lists
    private static void createGameListPS2(boolean checkBadGames) throws IOException {

        switch (PopsGameManager.getCurrentMode()) {
            case HDD_USB:
                createPS2ListFromSMB(checkBadGames);
                break;
            case SMB:
                createPS2ListFromSMB(checkBadGames);
                readPS2GamesFromULCFG();
                break;
            case HDD:
                createGameListFromFile("PS2", new File(PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator + "gameListPS2"));
                break;
            default:
                break;
        }
    }


    // Creates the PS2 game list from SMB (Using the actual files in the folder)
    private static void createPS2ListFromSMB(boolean checkBadGames) {

        List<String> storedBadGameList = GameListPersistence.readBadGameListFile();
        List<String> badGameListPS2 = new ArrayList<>();
        long totalSize = 0;
        File [] files = null;
        try {files = getISOFiles();} catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}

        if (files != null){

            // Loop through the iso files
            for (File isoFile : files) {

                // Try and get the game ID from the ISO
                String gameID = null;
                try {gameID = getPS2GameIDFromArchive(isoFile.getAbsolutePath());} catch (Exception ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}

                // Split the string to get the game name without the directory path or file extension
                String gamePath = isoFile.toString();

                if (gameID != null) {

                    if (gamePath.contains(gameID)){

                        // Descriptive name = file name minus extension, minus the leading game ID
                        // and any separator after it - see GameIdExtractor.deriveDescriptiveName.
                        // PS2 ISOs are conventionally "<id>.<name>.iso", but a bare "<id>.iso" has
                        // no descriptive part, so fall back to the ID (the old fixed substring(12)
                        // crashed in that case).
                        String gameName = GameIdExtractor.deriveDescriptiveName(isoFile.getName(), gameID);

                        // Used to calculate the total size of all ISO files combined
                        totalSize += isoFile.length();
                        gameListPS2.add(new Game(gameName, gameID, isoFile.toString(), PopsGameManager.bytesToHuman(isoFile.length()), isoFile.length()));
                    }
                    else {invalidGameListPS2.add(isoFile);}
                }
                else {if (checkBadGames) {if (!storedBadGameList.contains(isoFile.getName())){badGameListPS2.add(isoFile.getName());}}}
            }
            totalGameSizeRawPS2 = totalSize;
            totalGameSizeDisplayPS2 = PopsGameManager.bytesToHuman(totalSize);
        }

        // Display a message to the user, listing any games were the game ID could not be detected
        if (badGameListPS2.size() > 0) {
            StringBuilder badGames = new StringBuilder();
            badGameListPS2.forEach((game) -> {badGames.append(game).append("\n");});
            PopsGameManager.showWarningDialog("The system was unable to detect the unique game ID for the following PS2 games:\n" + badGames," Unable to Detect Game ID!");
        }

        // If any of the games are not correctly named with the game ID, call the invalid games function
        if (!invalidGameListPS2.isEmpty()) {checkInvalidGamesPS2(totalSize);}

        // This reorganizes the PS2 game list in alphaetical order
        Collections.sort(gameListPS2, new ListOrganiser());
    }


    // This adds all PS2 games from the ul.cfg file to the PS2 game list
    private static void readPS2GamesFromULCFG() {

        // If the ul.cfg file exists and contains games
        if (!USBUtil.readULCFG().isEmpty()){

            // Add each game from the ul.cfg file to the PS2 game list
            USBUtil.readULCFG().forEach((ulGame) -> {gameListPS2.add(ulGame);});

            // This reorganizes the PS2 game list in alphaetical order after the UL games have been added
            Collections.sort(gameListPS2, new ListOrganiser());
        }
    }


    // This enables the user to rename any PS2 games that are not correctly named
    private static void checkInvalidGamesPS2(long totalSize){

        // Ask the user if they want to try and rename the game files
        if (PopsGameManager.confirmDialog("Some of your PS2 games are not correctly named! \n\nDo you want to try and re-name them?"," Incorrect Game Names")){
            // Ported to JavaFX (Swing GameRenamingScreenPS2 kept until the migration lands).
            ps2gm.game.manager.fx.GameRenamingScreen.openPS2(invalidGameListPS2);
        }
        else {

            for (File isoFile : invalidGameListPS2) {

                // Try and get the game ID from the ISO
                String gameID = null;
                try {gameID = getPS2GameIDFromArchive(isoFile.getAbsolutePath());} catch (Exception ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}

                // Split the string to get the game name without the directory path or file extension
                String gamePath = isoFile.toString();
                String gameName = gamePath.substring(gamePath.lastIndexOf(File.separator) + 1).substring(0,gamePath.substring(gamePath.lastIndexOf(File.separator) + 1).lastIndexOf('.'));
                if (gameID != null) { gameName = stripGameId(gameName, gameID); }

                // Used to calculate the total size of all ISO files combined
                totalSize += isoFile.length();

                gameListPS2.add(new Game(gameName, gameID, isoFile.toString(), PopsGameManager.bytesToHuman(isoFile.length()), isoFile.length()));
            }
            totalGameSizeRawPS2 = totalSize;
            totalGameSizeDisplayPS2 = PopsGameManager.bytesToHuman(totalSize);
        }
    }


    // This returns an array containing all of the .ISO and .ZSO (compressed ISO) files in the directory
    private static File [] getISOFiles() throws IOException {
        File directory = new File(PopsGameManager.getOPLFolder() + File.separator + "DVD" + File.separator);
        File [] files = directory.listFiles((File dir, String name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".iso") || lower.endsWith(".zso");
        });

        return files;
    }


    // This returns an array containing all of the .VCD files in the directory
    private static File [] getVCDFiles() throws IOException {
        File directory = new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator);
        File [] files = directory.listFiles((File dir, String name) -> name.endsWith(".VCD") ||  name.endsWith(".vcd"));

        return files;
    }


    // </editor-fold>


    // This is used to sort the game list alphabetically
    public static class ListOrganiser implements Comparator<Game> {
        @Override
        public int compare(Game firstGame, Game secondGame) {return firstGame.getGameName().compareTo(secondGame.getGameName());}
    }
}

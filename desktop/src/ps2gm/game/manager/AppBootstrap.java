package ps2gm.game.manager;

import java.awt.GraphicsEnvironment;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.JOptionPane;

/**
 * Application startup: verifies the bundled libraries and cue2pops tool are
 * present and current, cleans up files left by a previous install, then
 * hands off to the JavaFX UI.
 */
final class AppBootstrap {

    private AppBootstrap() {}

    static final String RELEASE_DATE = "06 January 2018";
    static final String OLD_APP_NAME = "PS2GM_0.6.jar";
    static final String CURRENT_VERSION_NUMBER = "1.0";
    static final String CURRENT_APP_NAME = "PS2GM_" + CURRENT_VERSION_NUMBER + ".jar";
    static final String FORM_TITLE = "PS2GM v" + CURRENT_VERSION_NUMBER;
    static final String[] ALL_PREVIOUS_VERSIONS = {"0.0","0.1","0.2","0.3","0.4","0.5","0.6","0.6.1"};

    // Application start
    static void run() {

        // Print the application details in the console
        System.out.println("PS2GM Game Manager v" + CURRENT_VERSION_NUMBER + " (" + RELEASE_DATE + ") - by Logi26");

        // This prevents the application from launching if the required Java libraries are not available
        if (!checkJavaLibraries()) {exitAppWithWarning();}
        else {
            PopsGameManager.determineOSVersion();

            if (!PopsGameManager.isDebugMode()) {System.out.println("");}
            else {
                System.out.println("\nDEBUG MODE Enabled!\n");
                System.out.println("Detected OS: " + PopsGameManager.getOSType() + " " + PopsGameManager.getOSVersion() + " " + PopsGameManager.getOSArchitecture() + "\n");
            }

            deletePreviousAppVersion();
            checkCue2Pops();
            PopsGameManager.loadSettings();

            if (PopsGameManager.isOPLFolderSet()) {
                try {
                    GameListManager.createGameListsPS1();
                    GameListManager.createGameListsPS2(true);
                    GameListManager.createBadGameListFile();
                } catch(NullPointerException ex){PopsGameManager.displayErrorMessageDebug(ex.toString());}
            }

            // Hand off to the JavaFX Application - blocks until the UI exits.
            ps2gm.game.manager.fx.MainApp.run(new String[0]);
        }
    }

    // Delete jar file for previous version of the app
    private static void deletePreviousAppVersion(){

        // Delete all previous versions of the application if they exist in the directory
        for (String previousVersion : ALL_PREVIOUS_VERSIONS){
            File previousApp = new File(PopsGameManager.getCurrentDirectory() + File.separator + OLD_APP_NAME.substring(0, OLD_APP_NAME.length()-7) + previousVersion + ".jar");
            if (previousApp.exists() && previousApp.isFile()) {previousApp.delete();}
        }

        // Delete previous .bat and .sh files (including the pre-rename "start-oplpops.*" ones)
        for (String stale : new String[]{"start-windows.bat", "start-linux.sh", "start-oplpops.bat", "start-oplpops.sh"}) {
            File staleFile = new File(PopsGameManager.getCurrentDirectory() + File.separator + stale);
            if (staleFile.exists() && staleFile.isFile()) {staleFile.delete();}
        }

        // Generate the new Windows .bat file
        String dataString = "@echo off\n" + "set root=" + PopsGameManager.getCurrentDirectory() + "\nCD /D %root%\njava -jar " + CURRENT_APP_NAME;
        byte[] data = dataString.getBytes();
        Path file = Paths.get(PopsGameManager.getCurrentDirectory() + File.separator + "start-ps2gm.bat");
        try {Files.write(file, data);} catch (IOException ex) {PopsGameManager.displayErrorMessageDebug("Error creating the .bat file!\n\n" + ex.toString());}
        data = null;

        // Generate the new Linux .sh file
        dataString = "#!/bin/sh\njava -jar $(dirname \"$0\")/" + CURRENT_APP_NAME;
        data = dataString.getBytes();
        file = Paths.get(PopsGameManager.getCurrentDirectory() + File.separator + "start-ps2gm.sh");
        try {Files.write(file, data);} catch (IOException ex) {PopsGameManager.displayErrorMessageDebug("Error creating the .sh file!\n\n" + ex.toString());}

        // Generate the latest read me file
        ReadMeFileWritter readMeWritter = new ReadMeFileWritter();
        readMeWritter.WriteFile();
    }

    // This checks if the current version of cue2pops is the latest version, otherwise this replaces it
    private static void checkCue2Pops(){

        if (PopsGameManager.getOSType().equals("Windows")){

            String cue2popsMD5 = "29429ee96127be3f24568b1a9db65d4c";
            File currentCue2PopsFile = new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "tools" + File.separator + "windows" + File.separator + "cue2pops.exe");

            if (currentCue2PopsFile.exists() && currentCue2PopsFile.isFile()){
                String currentCue2PopsMD5 = PopsGameManager.PerformQuickHashCheck(currentCue2PopsFile);

                // If the MD5 does not match the version on the server, this downloads the latest version
                if (!currentCue2PopsMD5.equals(cue2popsMD5)){
                    BackendClient apiClient = PopsGameManager.newBackendClient();

                    // Create the temporary cue2pops backup folder
                    new File(currentCue2PopsFile.getParent() + File.separator + "backup").mkdir();

                    // Get the latest version of cue2pops
                    apiClient.getCue2PopsFromServer(currentCue2PopsFile.getParent() + File.separator + "backup" + File.separator + currentCue2PopsFile.getName(), cue2popsMD5);
                }
            }
        }
    }

    // Ensure that all of the required Java libraries have not been moved and are available
    private static boolean checkJavaLibraries(){
        boolean allLibrariesAvailable = true;
        if (!new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "commons-net-3.5.jar").exists()) {allLibrariesAvailable = false;}
        if (!new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "sevenzipjbinding.jar").exists()) {allLibrariesAvailable = false;}
        if (!new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "sevenzipjbinding-AllPlatforms.jar").exists()){allLibrariesAvailable = false;}
        return allLibrariesAvailable;
    }

    // Display message and exit the application if there is a problem
    private static void exitAppWithWarning(){
        Console console = System.console();

        if (console != null) {
            console.format("Unable to locate the required libraries in the \"lib\" directory!%n");
            console.format("The application will exit in 5 seconds.%n");
            // Put the thread to sleep for 5 seconds before exiting
            try {Thread.sleep(5000);}catch (InterruptedException ex){PopsGameManager.displayErrorMessageDebug(ex.toString());}

        } else if (!GraphicsEnvironment.isHeadless()) {JOptionPane.showMessageDialog(null,"Unable to locate the required libraries in the \"lib\" directory!\nThe application will now close."," Error Locating Libraries!",JOptionPane.ERROR_MESSAGE);}
    }
}

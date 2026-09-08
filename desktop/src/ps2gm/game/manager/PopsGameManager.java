package ps2gm.game.manager;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;

public class PopsGameManager {

    // <editor-fold defaultstate="collapsed" desc="Private Variables">
    private static boolean DebugMode = false;
    private static String userOperatingSystem;
    private static String userOperatingSystemVersion;
    private static String userOSArchitecture;

    private static final List<MyListener> LISTENERS = new ArrayList<>();
    private static DialogCallback dialogCallback;
    private static boolean firstLaunch = false;
    private static String ps2IPAddress = "192.168.0.01";
    private static String oplFolder;
    private static String currentConsole;
    private static String currentMode;
    
    private static boolean emulatorInUsePS1 = false; 
    private static String emulatorPathPS1 = ""; 
    private static boolean emulatorFullScreenPS1 = false;
    private static boolean emulatorInUsePS2 = false; 
    private static String emulatorPathPS2 = ""; 
    private static boolean emulatorFullScreenPS2 = false;
    
    private static String remoteVCDPath = "hdd0:/__.POPS";
    private static String remoteELFPath = "hdd0:/+OPL/APPS";
    private static String remoteOPLPath = "hdd0:/+OPL";

    private static boolean gameCompatabilityPS1 = true;
    private static boolean splitGameDisplayPS2 = true;
    private static boolean darkMode = false;
    private static String themeName = ps2gm.game.manager.fx.Themes.DEFAULT;

    private static String gameIDPositionPS2 = "start";
    // </editor-fold>
    
    
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Public Functions">   
    public static void callbackToUpdateGUIGameList(String gameID, int listIndex){updateGUIGameList(gameID, listIndex);}     // This provides a callback to the main game screen (To update GUI list)
    
    
    
    
    
    
    
    public static void addListener(MyListener listener) {LISTENERS.add(listener);}                                          // Adds callback LISTENERS to the list

    public static void setDialogCallback(DialogCallback callback) {dialogCallback = callback;}                              // Registers the UI layer's dialog callback (see DialogCallback)
    public static void showInfoDialog(String message, String title) {if (dialogCallback != null) {dialogCallback.info(message, title);}}
    public static void showWarningDialog(String message, String title) {if (dialogCallback != null) {dialogCallback.warn(message, title);}}
    public static void showErrorDialog(String message, String title) {if (dialogCallback != null) {dialogCallback.error(message, title);}}
    public static boolean confirmDialog(String message, String title) {return dialogCallback != null && dialogCallback.confirm(message, title);}
    public static void showTimedInfoDialog(String message, String title, int seconds) {if (dialogCallback != null) {dialogCallback.infoTimed(message, title, seconds);}}

    public static void setDebugMode(boolean debugMode) {DebugMode = debugMode;}                                             // Set debug mode on or off (if debug on, most of the exception messages will be printed)
    public static void setCurrentConsole(String console){currentConsole = console;}                                         // This sets the current console and saves it to the settings.xml file
    public static void setFisrtLaunch(boolean first) {firstLaunch = first;}                                                 // This sets the first launch boolean value
    public static void setPS2IP(String ipAddress) {ps2IPAddress = ipAddress;}                                               // Sets the PS2 IP address  
    public static void setCurrentMode(String mode) {currentMode = mode;}                                                    // This sets the current mode and saves it to the settings.xml file
    public static void setOPLFolder(String oplDirectory) {oplFolder = oplDirectory;}                                        // Sets the OPL folder               
    public static void setEmulatorInUsePS1(boolean useEmulator) {emulatorInUsePS1 = useEmulator;}
    public static void setEmulatorPathPS1(String path) {emulatorPathPS1 = path;}
    public static void setEmulatorFullScreenPS1(boolean fullscreen) {emulatorFullScreenPS1 = fullscreen;}
    public static void setEmulatorInUsePS2(boolean useEmulator) {emulatorInUsePS2 = useEmulator;}
    public static void setEmulatorPathPS2(String path) {emulatorPathPS2 = path;}
    public static void setEmulatorFullScreenPS2(boolean fullscreen) {emulatorFullScreenPS2 = fullscreen;}
    public static void setRemoteVCDPath(String path) {remoteVCDPath = path;}
    public static void setRemoteELFPath(String path) {remoteELFPath = path;}
    public static void setRemoteOPLPath(String path) {remoteOPLPath = path;}
    public static void setGameCompatabilityPS1(boolean gameCompatability) {gameCompatabilityPS1 = gameCompatability;}
    public static void setSplitGameDisplayPS2(boolean splitGameDisplay) {splitGameDisplayPS2 = splitGameDisplay;}
    public static void setDarkMode(boolean enabled) {darkMode = enabled;}
    public static void setThemeName(String name) {themeName = ps2gm.game.manager.fx.Themes.normalise(name); darkMode = ps2gm.game.manager.fx.Themes.isDark(themeName);}
    public static void setGameIDPositionPS2(String position) {gameIDPositionPS2 = position;}
    
    public static Boolean getFisrtLaunch() {return firstLaunch;}                                                            // Returns the first launch boolean value
    public static String getFormTitle() {return AppBootstrap.FORM_TITLE;}
    public static String getApplicationVersionNumber() {return AppBootstrap.CURRENT_VERSION_NUMBER;}                        // Returns the application version number
    public static String getApplicationReleaseDate() {return AppBootstrap.RELEASE_DATE;}                                    // Returns the application release date
    public static String getPS2IP() {return ps2IPAddress;}                                                                  // Returns the PS2 IP address
    public static String getOPLFolder() {return oplFolder;}                                                                 // Returns the OPL folder
    public static String getCurrentMode() {return currentMode;}                                                             // Returns current mode
    public static String getCurrentConsole() {return currentConsole;}                                                       // Returns current console
    public static String getOSType() {return userOperatingSystem;}                                                          // Returns the OS type (windows, linux etc)
    static String getOSVersion() {return userOperatingSystemVersion;}                                                       // Returns the OS version (xp, 7, 10, ubuntu etc) - only used by AppBootstrap's debug print
    static String getOSArchitecture() {return userOSArchitecture;}                                                          // Returns the OS architecture (64bit or 86bit) - only used by AppBootstrap's debug print
    public static Boolean getEmulatorInUsePS1() {return emulatorInUsePS1;}
    public static String getEmulatorPathPS1() {return emulatorPathPS1;}
    public static Boolean getEmulatorFullScreenPS1() {return emulatorFullScreenPS1;}
    public static Boolean getEmulatorInUsePS2() {return emulatorInUsePS2;}
    public static String getEmulatorPathPS2() {return emulatorPathPS2;}
    public static Boolean getEmulatorFullScreenPS2() {return emulatorFullScreenPS2;}
    public static String getRemoteVCDPath() {return remoteVCDPath;}
    public static String getRemoteELFPath() {return remoteELFPath;}
    public static String getRemoteOPLPath() {return remoteOPLPath;}
    public static Boolean getGameCompatabilityPS1() {return gameCompatabilityPS1;}
    public static Boolean getSplitGameDisplayPS2() {return splitGameDisplayPS2;}
    public static Boolean getDarkMode() {return darkMode;}
    public static String getThemeName() {return ps2gm.game.manager.fx.Themes.normalise(themeName);}
    public static String getGameIDPositionPS2() {return gameIDPositionPS2;}
    
    public static Boolean isOPLFolderSet() {return oplFolder != null;}                                                      // Returns a boolean showing if the OPL folder has been set by user                        
    public static Boolean isCurrentConsoleSet() {return currentConsole != null;}                                            // Returns a boolean showing if the current console has been set by user
    
    
    
    // This is used for displaying the captured exceptions if debug mode is set
    public static void displayErrorMessageDebug(String message){
        if (DebugMode){
            System.out.println("\n**********************************************");
            System.out.println("Exception Caught:");
            System.out.println(message);
            System.out.println("**********************************************\n");
        } 
    }
    
    // This is used for displaying the messages if debug mode is set
    public static void displayMessageDebug(String message){
        if (DebugMode){
            System.out.println("\n**********************************************");
            System.out.println(message);
            System.out.println("**********************************************\n");
        }
    }

    static boolean isDebugMode() {return DebugMode;}                                                                        // Only used by AppBootstrap's startup debug print

    
    // Base URL of the HTTP API.
    // Override with -Dps2gm.api.baseurl=http://host:8000/v1 (or the PS2GM_API_BASEURL env var)
    public static String getApiBaseUrl(){
        String override = System.getProperty("ps2gm.api.baseurl", System.getenv("PS2GM_API_BASEURL"));
        return (override != null && !override.trim().isEmpty()) ? override.trim() : "https://ps2gm.logi26.co.uk/v1";
    }

    // Creates a fresh backend client. Every screen should get its client
    // through here rather than constructing MyApiClient directly - this is
    // the one place that would need to change if another backend ever showed up.
    public static BackendClient newBackendClient(){
        return new MyApiClient();
    }
    
    // This loads the settings from the settings.xml file
    public static void loadSettings(){
        XMLFileManager.readSettingsXML();
    }     
    
    
    // This returns the file prefix for SMB and USB mode
    public static String getFilePrefix(){
        String filePrefix = null;
        switch (getCurrentMode()) {
            case "SMB":
                filePrefix = "SB.";
                break;
            case "HDD_USB":
                filePrefix = "XX.";
                break;
            default:
                filePrefix = "";
                break;
        }
        
        return filePrefix;
    }
    
    
    // This opens a directory in explorer
    public static void openDirectory(String directory){
        
        Desktop desktop = Desktop.getDesktop();
        File dirToOpen = null;
        try {
            dirToOpen = new File(directory);
            desktop.open(dirToOpen);
        } 
        catch (IllegalArgumentException | IOException ex) {displayErrorMessageDebug("Error opening directory!\n\n" + ex.toString());}
    }
    
    // This determines the game region based on the games ID
    public static String determineGameRegion(String gameID){
        
        String gameRegion;
        switch (gameID) {
            case "SLES":
            case "SCES":
                gameRegion = "PAL";
                break;
            case "SLUS":
            case "SCUS":
                gameRegion = "NTSCU";
                break;
            case "SLPM":
            case "SLKA": 
            case "SCPS":
            case "SLPS":
            case "ESPM": 
            case "CPCS":
            case "SCAJ":
            case "GUST":
            case "SCCS":
            case "SLAJ":
                gameRegion = "NTSCJ";
                break;
            default:
                gameRegion = "NTSCJ";
                break;
        }
        return gameRegion;
    }
    
    // This returns the current directory of the .Jar file
    public static String getCurrentDirectory(){
        
        CodeSource codeSource = PopsGameManager.class.getProtectionDomain().getCodeSource();
        File jarFile;
        String jarDirectory = null;
        try {
            jarFile = new File(codeSource.getLocation().toURI().getPath());
            jarDirectory = jarFile.getParentFile().getPath();
        } 
        catch (URISyntaxException ex) {displayErrorMessageDebug("Unable to detect the current directory of the jar file!\n\n" + ex.toString());}  

      return jarDirectory;
    }
    
    // This formats a double value to a float
    private static String doubleToFloat(double d){return new DecimalFormat("#.##").format(d);}

    // This converts a bytes value to human readable form
    public static String bytesToHuman(long size){
        long Kb = 1  * 1024;
        long Mb = Kb * 1024;
        long Gb = Mb * 1024;
        long Tb = Gb * 1024;
        long Pb = Tb * 1024;
        long Eb = Pb * 1024;

        if (size <  Kb)                 return doubleToFloat(        size     ) + " byte";
        if (size >= Kb && size < Mb)    return doubleToFloat((double)size / Kb) + " Kb";
        if (size >= Mb && size < Gb)    return doubleToFloat((double)size / Mb) + " Mb";
        if (size >= Gb && size < Tb)    return doubleToFloat((double)size / Gb) + " Gb";
        if (size >= Tb && size < Pb)    return doubleToFloat((double)size / Tb) + " Tb";
        if (size >= Pb && size < Eb)    return doubleToFloat((double)size / Pb) + " Pb";
        if (size >= Eb)                 return doubleToFloat((double)size / Eb) + " Eb";

        return "???";
    }
    // </editor-fold>
    

    // <editor-fold defaultstate="collapsed" desc="Private Functions">   
    private static void updateGUIGameList(String gameID, int listIndex){LISTENERS.stream().forEach((listener) -> {listener.updateGameList(gameID, listIndex);});}                                    // Callback to update the games list in the MainScreen

    
    // This determines what Operating system - see SystemInfo for the actual detection logic.
    static void determineOSVersion() {
        SystemInfo.Detection detection = SystemInfo.detect();
        userOperatingSystem = detection.osType();
        userOperatingSystemVersion = detection.osVersion();
        userOSArchitecture = detection.osArchitecture();
    }

    // This returns an Md5 hash of the selected file
    public static String PerformQuickHashCheck(File selectedFile){

        // Performs a MD5 hash on a file!
        String fileMD5 = null;
        FileInputStream fileInputStream = null;
        try {

            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            fileInputStream = new FileInputStream(selectedFile);
            byte[] dataBytes = new byte[1024];
            int nread = 0;
            while ((nread = fileInputStream.read(dataBytes)) != -1) {messageDigest.update(dataBytes, 0, nread);}   
            byte[] mdbytes = messageDigest.digest();

            // Convert the byte to hex format
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mdbytes.length; i++) {sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));}   
            fileMD5 = sb.toString();
        } 
        catch (FileNotFoundException ex) {displayErrorMessageDebug(ex.toString());} catch (IOException | NoSuchAlgorithmException ex) {displayErrorMessageDebug(ex.toString());}
        finally {try {if (fileInputStream != null) {fileInputStream.close();}} catch (IOException ex) {displayErrorMessageDebug(ex.toString());}}
        
        return fileMD5;
    }
    

    // </editor-fold>


    // Application start - see AppBootstrap for the actual startup sequence.
    public static void startApplication(){
        AppBootstrap.run();
    }
}
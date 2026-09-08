package ps2gm.game.manager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JOptionPane;

/**
 * Builds and writes conf_apps.cfg (the config-ELM list OPL reads to find each
 * game's launcher ELF), and - in HDD mode - offers to upload it to the
 * console's memory card over FTP.
 *
 * Extracted from GameListManager.writeConfigELM, which conflated building the
 * list, writing the file, and the upload-confirmation flow in one method.
 */
public final class ConfigElmWriter {

    private ConfigElmWriter() {}

    /** Builds the config-ELM list for the current mode (SMB/HDD_USB/HDD). */
    public static List<String> buildList() {

        List<String> configElmList = new ArrayList<>();

        switch (PopsGameManager.getCurrentMode()) {
            case "SMB":
                if (new File(PopsGameManager.getOPLFolder() + File.separator + "POPS").exists()){
                    try {
                        List<File> filesInFolder = Files.walk(Paths.get(PopsGameManager.getOPLFolder() + File.separator + "POPS")).filter(Files::isRegularFile).map(Path::toFile).collect(Collectors.toList());
                        filesInFolder.stream().filter((file) -> (file.getName().substring(file.getName().length()-3, file.getName().length()).equals("VCD"))).forEachOrdered((file) -> {
                            configElmList.add(file.getName().substring(0, file.getName().length()-16) + "=smb:/POPS/SB." + file.getName().substring(0, file.getName().length()-4) + ".ELF");
                        });
                    } catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
                }
                break;
            case "HDD_USB":

                if (new File(PopsGameManager.getOPLFolder() + File.separator + "POPS").exists()){
                    try {
                        List<File> filesInFolder = Files.walk(Paths.get(PopsGameManager.getOPLFolder() + File.separator + "POPS")).filter(Files::isRegularFile).map(Path::toFile).collect(Collectors.toList());
                        String elfFolder = GameListManager.getFormattedELFFolder();
                        if (elfFolder.contains("+OPL/")) {elfFolder = elfFolder.replace("+OPL/", "");}

                        for (File file : filesInFolder){
                            if (file.getName().substring(file.getName().length()-3, file.getName().length()).toUpperCase().equals("VCD")){
                                configElmList.add(file.getName().substring(0, file.getName().length()-16) + "=mass" + GameListManager.getFormattedELFPartition() + ":" + "/" + elfFolder + "/" + "XX." + file.getName().substring(0, file.getName().length()-4) + ".ELF");
                            }
                        }
                    } catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
                }
                break;
            case "HDD":
                if (new File(PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator + "gameListPS1.dat").exists()){

                    try (Stream<String> lines = Files.lines(Paths.get(PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator + "gameListPS1.dat"), StandardCharsets.UTF_8)){
                        for (String line : (Iterable<String>) lines::iterator){
                            String[] parts = line.split(",");

                            String path = null;
                            String elfFolder = GameListManager.getFormattedELFFolder();
                            if (elfFolder.contains("+OPL/")) {elfFolder = elfFolder.replace("+OPL/", "");}
                            if (GameListManager.getFormattedELFDrive().equals("hdd")) {path = "=pfs" + GameListManager.getFormattedELFPartition() + ":" + "/" + elfFolder + "/";}
                            else if (GameListManager.getFormattedELFDrive().equals("mass")) {path = "=mass" + GameListManager.getFormattedELFPartition() + ":" + "/" + elfFolder + "/";}

                            configElmList.add(parts[2] + path + parts[2] + "-" + parts[0] + ".ELF");
                        }
                    } catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
                }
                break;
            default:
                break;
        }

        return configElmList;
    }

    /** Builds the list, writes conf_apps.cfg, and (in HDD mode) offers to upload it via FTP. */
    public static void write() {

        List<String> configElmList = buildList();

        if (configElmList.size() > 0){
            try {Files.write(Paths.get(PopsGameManager.getOPLFolder() + File.separator + "conf_apps.cfg"), configElmList, Charset.forName("UTF-8"));}
            catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
        }

        // If HDD mode, ask user if they want to upload the conf_apps file to the console via FTP
        if (PopsGameManager.getCurrentMode().equals("HDD")){

            int dialogResult = JOptionPane.showConfirmDialog (null, "Do you want to upload conf_apps.cfg to your console?\n\nFTP Server must be running on your console in order to perform this task!"," Connect to PlayStation 2",JOptionPane.YES_NO_OPTION);
            if(dialogResult == JOptionPane.YES_OPTION){

                // FTP to console and upload the conf_apps.cfg file to the +OPL directory
                MyFTPClient myFTP = new MyFTPClient();

                // Connect to the PS2 console
                if (myFTP.connectToConsole(PopsGameManager.getPS2IP())) {

                    // Change to the memory card
                    myFTP.changeDirectory("mc/0/");

                    // If a conf_apps.cfg is already on the memory card, delete it
                    if (myFTP.remoteFileExists("mc", "/mc/0/OPL/", "","conf_apps.cfg", true)){ myFTP.deleteRemoteFile("/mc/0/OPL/conf_apps.cfg");}

                    // Upload new conf_apps.cfg to the memory card
                    myFTP.uploadConfElmToConsole(PopsGameManager.getOPLFolder() + "conf_apps.cfg", "conf_apps.cfg", "/mc/0/OPL/");

                    // Disconnect the FTP connection with the console
                    myFTP.disconnectFromConsole();
                }
            }
        }
    }
}

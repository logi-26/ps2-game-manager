package ps2gm.game.manager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class ReadMeFileWritter {

    public ReadMeFileWritter(){
        
    }
    
    // This writes the read me file if it has been deleted or the MD5 is different to the latest version
    public void WriteFile(){

        String fileContent[] = {
            "PS2GM Game Manager V" + AppBootstrap.CURRENT_VERSION_NUMBER + " (Beta) By Logi26",
            "Cross platform application for managing PlayStation 1 and PlayStation 2 game, art, config, cheat and virtual memory card files for Open-PS2-Loader (OPL) and POPSTARTER.",
            " ",
            " ",
            "WARNING/DISCLAIMER:",
            "This application is still in beta and may contain bugs.",
            "The application is capable of writing data to the PlayStation 2's internal hard drive, therefore there is a risk of data corruption.",
            " ",
            "Use this application at your own risk!",
            "This Software is provided \"as is,\" without warranty of any kind.",
            "In no event shall the author be liable for any damages or other liability, arising from, or in connection with the use of this software.",
            " ",
            " ",
            "CREDITS:",
            "This application uses cue2pops and pops2cue which were developed by krHACKen, who kindly provided the sources.",
            "This application uses hdl_dump which was developed by The Wizard of Oz, AKuHAK and other contributors.",
            "This application also uses genvmc which was developed by jimmikaelkael.",
            "This application was inspired by the Official OPL Manager app which was developed by danielb.",
            " ",
            "All of the third party tools have been compiled for Linux and Windows from the same code bases:",
            "cue2pops = version 2.3 - Permission from krHACKen to distribute.",
            "pops2cue = version 1.0 - Permission from krHACKen to distribute.",
            "hdl_dump = version 093 - Licensed under: GNU GENERAL PUBLIC LICENSE Version 2.		https://bitbucket.org/AKuHAK/hdl-dump",
            "genvmc = version 0.1.0 - Licensed under: Academic Free License version 3.0.	        https://github.com/ifcaro/Open-PS2-Loader/tree/master/pc/genvmc",
            " ",
            " ",
            "CONTENT:",
            "Artwork, configs, cheats, VMCs and widescreen codes are downloaded on demand from the PS2GM database API as needed - there's no fixed catalogue size to report here, it grows over time.",
            " ",
            " ",
            "CURRENT FEATURES V" + AppBootstrap.CURRENT_VERSION_NUMBER + " (BETA):",
            "*********************************************************************************************************************************",
            "ALL MODES:",
            "Switch between the PlayStation 1 and PlayStation 2 game lists from the Console menu.",
            "Check for application updates from the File menu.",
            "Choose from five UI themes (Primer, Nord - light and dark - and Dracula), each with a matching dark/light Windows title bar.",
            "Rename a game (32-character limit, art/config/cheat files renamed to match); if its unique ID can't be detected at all, a Bad Game List screen offers ID suggestions looked up from a reference catalogue of known titles - multi-disc titles are shown as one suggestion per disc, never picked automatically.",
            "*********************************************************************************************************************************",
            "SMB/USB MODE (PS1):",
            "Add/batch import PS1 games to your OPL folder (.cue files are converted to .vcd). The app determines the game's unique ID, renames the file and generates the .elf file.",
            "Delete a PS1 game from your OPL folder.",
            "Download/batch download PS1 game art (cover, cover 2, background, disc icon, screenshots, spine and logo), config and cheat files from the database.",
            "Download or generate (genvmc) virtual memory cards for a PS1 game.",
            "Launch a PS1 game in your configured emulator.",
            "Generate new .elf files for all PS1 .vcd games in the OPL folder.",
            "Generate the conf_apps.cfg file.",
            "Generate spine ART for all PS1 games.",
            "Perform an MD5 hash check on a game file.",
            "Detect games with names longer than 32 characters.",
            "Display PS1 compatibility flags in the game list.",
            "Delete all ELF files in the OPL folder.",
            "Delete all/unused ART, CFG or CHT files in the OPL folder.",
            "*********************************************************************************************************************************",
            "HDD MODE (PS1):",
            "Add/batch upload PS1 games to your console (.cue files are converted to .vcd). The app determines the game's unique ID, renames the file and generates the .elf file. (Requires FTP Server Running)",
            "Delete a PS1 game from your console. (Requires FTP Server Running)",
            "Download/batch download PS1 game art, config and cheat files from the database to the OPL/HDD folder.",
            "Download or generate virtual memory cards for a PS1 game.",
            "Upload/download game art, config, cheat and VMC files to/from the console, or browse and delete them there. (Requires FTP Server Running)",
            "Generate new .elf files for all PS1 .vcd games on the console. (Requires FTP Server Running)",
            "Generate the conf_apps.cfg file and upload it to the console's memory card 1. (Requires FTP Server Running)",
            "Refresh the local game list from the console. (Requires FTP Server Running)",
            "Browse the console over FTP and transfer files to it, or set the remote ART/CFG/CHT locations. (Requires FTP Server Running)",
            "Display PS1 compatibility flags in the game list.",
            "Delete all/unused ART, CFG or CHT files in the OPL folder and on the console's internal hard drive. (Requires FTP Server Running)",
            "*********************************************************************************************************************************",
            "SMB/USB MODE (PS2):",
            "Add/batch import PS2 games to your OPL folder. The app determines the game's unique ID and renames the file.",
            "Delete a PS2 game from your OPL folder.",
            "Download/batch download PS2 game art (cover, cover 2, background, disc icon, screenshots, spine and logo), config and cheat files from the database.",
            "Download or generate virtual memory cards for a PS2 game.",
            "Launch a PS2 game in your configured emulator.",
            "Perform an MD5 hash check on a game file.",
            "Detect games with names longer than 32 characters.",
            "Read the game list from ul.cfg, or (re)generate ul.cfg.",
            "Convert a PS2 ISO to OPL's split UL format and back, from the game-list right-click menu.",
            "Convert a PS2 ISO to OPL's LZ4-compressed ZSO format and back, from the game-list right-click menu.",
            "Choose whether the game ID goes before or after the game name in the file name.",
            "Optionally highlight UL-format games in the game list.",
            "Generate spine ART for all PS2 games.",
            "Delete all spine ART.",
            "Delete all/unused ART, CFG or CHT files in the OPL folder.",
            "*********************************************************************************************************************************",
            "HDD MODE (PS2):",
            "Add/batch upload PS2 games to your console. The app determines the game's unique ID and renames the file. (Requires HDL Server Running)",
            "Download/batch download PS2 game art, config and cheat files from the database to the OPL/HDD folder.",
            "Download or generate virtual memory cards for a PS2 game.",
            "Upload game art, config, cheat and VMC files to the console, or browse and delete them there. (Requires FTP Server Running)",
            "Refresh the local game list from the console. (Requires HDL Server Running)",
            "Browse the console over FTP and transfer files to it, or set the remote ART/CFG/CHT locations. (Requires FTP Server Running)",
            "Delete all/unused ART, CFG or CHT files in the OPL folder and on the console's internal hard drive. (Requires FTP Server Running)",
            "*********************************************************************************************************************************",
            "",
            "NOTES:",
            "The PS1 .VCD files need to have the game's unique ID at the end of their name. (This is required in order to be able to manage the PS1 games remotely and identify the associated art, config and cheat files)",
            "The PS1 .VCD files must follow this naming convention: GAME NAME-GAME ID.VCD. Example: Resident Evil-SLES_123.45.VCD and Resident Evil-SLES_123.45.ELF",
            "",
            "This application will auto name the PS1 .VCD files for you when you use the add game option from the menu.",
            "If you have manually put PS1 .VCD files on your console's internal HDD and they do not have the game's unique ID at the end of their name, this application will not recognise them - the Bad Game List screen can suggest a corrected name for these.",
            "",
            "PS2 game files can be either .ISO or OPL's compressed .ZSO format, and are named GAME ID.GAME NAME.ISO/.ZSO (or with the ID at the end - see Tools -> PS2 Game ID Position).",
            "",
            "PS1 Emulator feature was only designed for use with PCSX-Reloaded which supports Windows/Linux - Other emulators may work as long as they end their process when a game window is closed (ePSX does not)",
            "PS2 Emulator feature was only designed for use with PCSX2 which supports Windows/Linux - Other emulators may work as long as they end their process when a game window is closed",
            "",
            "PS2 CD-based games are not currently supported - PS2 DVD titles only."
        };
        
        // Delete previous read me file and then generate a new one
        File localReadMeFile = new File(PopsGameManager.getCurrentDirectory() + File.separator + "READ ME.txt");

        if (localReadMeFile.exists() && localReadMeFile.isFile()){
            String latestReadMeMD5 = "beba9130d68b495ea04a188c2d5049a9";
            String localReadMeMD5 = PopsGameManager.PerformQuickHashCheck(localReadMeFile);
            
            if (!localReadMeMD5.equals(latestReadMeMD5)){
                localReadMeFile.delete();
                List<String> lines = Arrays.asList(fileContent);
                try {Files.write(Paths.get(localReadMeFile.getAbsolutePath()), lines, Charset.forName("UTF-8"));} catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
            }
        }
        else {
            localReadMeFile.delete();
            List<String> lines = Arrays.asList(fileContent);
            try {Files.write(Paths.get(localReadMeFile.getAbsolutePath()), lines, Charset.forName("UTF-8"));} catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
        }
    }
}
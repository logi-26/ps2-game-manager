package ps2gm.game.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches the PS2 game list from the console via hdl_dump's TOC command.
 *
 * Extracted from HDLDumpManager.hdlDumpGetTOC/addGameToList - stateless,
 * doesn't touch HDLDumpManager's upload-progress instance fields (which is
 * why runUpload/runBatchUpload's own finally blocks construct a throwaway
 * HDLDumpManager just to call hdlDumpGetTOC again afterward).
 */
final class HdlDumpToc {

    private HdlDumpToc() {}

    static List<Game> fetch(String destination) throws IOException, InterruptedException {

        List<Game> gameListPS2 = null;
        boolean errorConnecting = false;

        HdlDumpProcess.Executable exe = HdlDumpProcess.resolveExecutable();

        // Create a local directory to store the file for transfering to the console (If the local directory doesnt already exist)
        if (!HdlLocalDirectoryBootstrap.createLocalHDLDirectory("hdd")) {
            PopsGameManager.showWarningDialog("There was a problem creating the local hdd folder in the same directory as this Jar file.", " Unable to Create Directory!");
        }

        // Ensure that the HDL_Dump executable/binary is available before trying to launch it
        if (new File(exe.path()).exists()) {

            Process process = HdlDumpProcess.start(exe, List.of("hdl_toc", destination));

            String line = null;
            try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                while ((line = stdError.readLine()) != null) {
                    errorConnecting = true;
                }

                // Try and store the HDD game list to an XML file
                if (!errorConnecting) {
                    gameListPS2 = new ArrayList<>();
                    while ((line = stdInput.readLine()) != null) {
                        if (line.contains("DVD")) {
                            addGameToList(gameListPS2, line);
                        }
                    }
                }
            }

            // Wait for HDL_Dump
            process.waitFor();

            if (errorConnecting) {
                PopsGameManager.showErrorDialog("HDL_Dump reported an error! \n\nPlease ensure that you have HDL_Server running on your PlayStation 2 console. \nAlso make sure that you have enetered the correct IP address.", " HDL_Dump Error!");
            }
        }

        return gameListPS2;
    }

    // This determines information about the game before adding it to the list which is passed in
    private static void addGameToList(List<Game> gameListPS2, String line) {

        // Split the line using whitespace as the deliminator
        String[] splitLine = line.split("\\s+");

        // Get the game size without the KB prefix
        String gameRawSize = splitLine[1].substring(0, splitLine[1].length() - 2);

        // Determine the location of the game unique ID/Region code, within the string array
        int gameIDPosition = 0;
        boolean gameIDFound = false;

        for (String regionCode : RegionCodes.ALL) {

            if (splitLine.length > 2 && !gameIDFound) {
                if (splitLine[2].contains(regionCode)) {
                    gameIDFound = true;
                    gameIDPosition = 2;
                }
            }

            if (splitLine.length > 3 && !gameIDFound) {
                if (splitLine[3].contains(regionCode)) {
                    gameIDFound = true;
                    gameIDPosition = 3;
                }
            }

            if (splitLine.length > 4 && !gameIDFound) {
                if (splitLine[4].contains(regionCode)) {
                    gameIDFound = true;
                    gameIDPosition = 4;
                }
            }

            if (splitLine.length > 5 && !gameIDFound) {
                if (splitLine[5].contains(regionCode)) {
                    gameIDFound = true;
                    gameIDPosition = 5;
                }
            }
        }

        String gameID = splitLine[gameIDPosition];

        // This gets the game name (does not know how many white spaces are in the game name)
        String gameName = "";
        for (int i = gameIDPosition + 1; i < splitLine.length; i++) {
            if (i != splitLine.length - 1) {
                gameName += (splitLine[i] + " ");
            } else {
                gameName += (splitLine[i]);
            }
        }

        // Add the values to the lists
        if (!"".equals(gameName)) {
            gameListPS2.add(new Game(gameName, gameID, "PATH HERE!!", PopsGameManager.bytesToHuman(Long.parseLong(gameRawSize) * 1024), Long.parseLong(gameRawSize) * 1024));
        }
    }
}

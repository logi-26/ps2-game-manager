package ps2gm.game.manager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The hardcoded PS1 multi-disc game catalogue (games whose other discs share
 * a DISCS.TXT bootstrap file), and the logic that keeps that file in sync
 * with the games actually present in the current list.
 *
 * Extracted from GameListManager.
 */
public final class MultiDiscGameCatalog {

    // Metal Gear Solid - 2 disc game (3 discs for Japan special editions)
    private static final String[] METAL_GEAR_SOLID_NTSCU = {"SLUS_005.94", "SLUS_007.76"};
    private static final String[] METAL_GEAR_SOLID_PAL_E = {"SLES_013.70", "SLES_113.70"};
    private static final String[] METAL_GEAR_SOLID_PAL_F = {"SLES_015.06", "SLES_115.06"};
    private static final String[] METAL_GEAR_SOLID_PAL_G = {"SLES_015.07", "SLES_115.07"};
    private static final String[] METAL_GEAR_SOLID_PAL_I = {"SLES_015.08", "SLES_115.08"};
    private static final String[] METAL_GEAR_SOLID_PAL_S = {"SLES_017.34", "SLES_117.34"};
    private static final String[] METAL_GEAR_SOLID_NTSCJ = {"SCPS_453.17", "SCPS-453.18"};
    private static final String[] METAL_GEAR_SOLID_NTSCJ_KONAMI = {"SLPM_864.85", "SLPM_864.86"};
    private static final String[] METAL_GEAR_SOLID_NTSCJ_B1 = {"SCPS_453.20", "SCPS_453.21", "SCPS_453.22"};
    private static final String[] METAL_GEAR_SOLID_NTSCJ_B2 = {"SLPM_861.14", "SLPM_861.15", "SLPM_861.16"};
    private static final String[] METAL_GEAR_SOLID_NTSCJ_20TH = {"SLPM_874.11", "SLPM_874.12", "SLPM_874.13"};

    // Oddworld Abe Exoddus - 2 disc game
    private static final String[] ODDWORLD_ABE_EXODDUS_NTSCU = {"SLUS_007.10", "SLUS_007.31"};
    private static final String[] ODDWORLD_ABE_EXODDUS_PAL_E = {"SLES_014.80", "SLES_114.80"};
    private static final String[] ODDWORLD_ABE_EXODDUS_PAL_F = {"SLES_015.02", "SLES_115.02"};
    private static final String[] ODDWORLD_ABE_EXODDUS_PAL_G = {"SLES_015.03", "SLES_115.03"};
    private static final String[] ODDWORLD_ABE_EXODDUS_PAL_I = {"SLES_015.04", "SLES_115.04"};
    private static final String[] ODDWORLD_ABE_EXODDUS_PAL_S = {"SLES_015.05", "SLES_115.05"};

    // Array of all multi-disc PS1 games
    private static final String[][] MULTI_DISC_GAME_LIST = {
        METAL_GEAR_SOLID_NTSCU,
        METAL_GEAR_SOLID_PAL_E,
        METAL_GEAR_SOLID_PAL_F,
        METAL_GEAR_SOLID_PAL_G,
        METAL_GEAR_SOLID_PAL_I,
        METAL_GEAR_SOLID_PAL_S,
        METAL_GEAR_SOLID_NTSCJ,
        METAL_GEAR_SOLID_NTSCJ_KONAMI,
        METAL_GEAR_SOLID_NTSCJ_B1,
        METAL_GEAR_SOLID_NTSCJ_B2,
        METAL_GEAR_SOLID_NTSCJ_20TH,
        ODDWORLD_ABE_EXODDUS_NTSCU,
        ODDWORLD_ABE_EXODDUS_PAL_E,
        ODDWORLD_ABE_EXODDUS_PAL_F,
        ODDWORLD_ABE_EXODDUS_PAL_G,
        ODDWORLD_ABE_EXODDUS_PAL_I,
        ODDWORLD_ABE_EXODDUS_PAL_S
    };

    private MultiDiscGameCatalog() {}

    /** The sibling disc IDs for {@code gameID}, or null if it isn't a known multi-disc game. */
    public static String[] siblingDiscsFor(String gameID) {
        for (String[] selectedArray : MULTI_DISC_GAME_LIST) {
            for (String selectedGame : selectedArray) {
                if (selectedGame.equals(gameID)) {
                    return selectedArray;
                }
            }
        }
        return null;
    }

    /** Ensures each multi-disc game's DISCS.TXT lists all its other discs that are actually in {@code gameListPS1}. */
    public static void checkMultiDiscFiles(List<Game> gameListPS1) {
        for (Game ps1Game : gameListPS1) {

            // If the game is a multi-disc game
            if (ps1Game.getMultiDiscGame()) {

                File gameFolder = new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + ps1Game.getGameName() + "-" + ps1Game.getGameID());

                // Check if the game folder exists (it should have been created in the function that calls this function if it did not exist)
                if (gameFolder.exists() && gameFolder.isDirectory()) {

                    File discsFile = new File(gameFolder + File.separator + "DISCS.TXT");

                    // Check if the DISCS.TXT file exists (it should have been created in the function that calls this function if it did not exist)
                    if (discsFile.exists() && discsFile.isFile()) {

                        // This gets a list of the other multi-disc games that are in the game list
                        List<String> newDiscsList = new ArrayList<>();
                        List<String> multiDiscList = ps1Game.getMultiDiscList();
                        for (String gameID : multiDiscList) {
                            for (Game game : gameListPS1) {
                                if (game.getGameID().equals(gameID)) {
                                    newDiscsList.add(game.getGameName() + "-" + game.getGameID() + ".VCD");
                                }
                            }
                        }

                        // This writes the DISCS.TXT file to include the other multi-discs that are associated with it
                        // BUG FIX: previously a plain FileWriter, never closed on the happy path.
                        if (!newDiscsList.isEmpty()) {
                            try (FileWriter fileWriter = new FileWriter(discsFile.getAbsolutePath(), StandardCharsets.UTF_8)) {
                                for (String arrayItem : newDiscsList) {
                                    fileWriter.write(arrayItem + "\n");
                                }
                            } catch (IOException ex) {
                                PopsGameManager.displayErrorMessageDebug(ex.toString());
                            }
                        }
                    }
                }
            }
        }
    }
}

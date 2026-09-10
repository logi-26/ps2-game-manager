package ps2gm.game.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The PS1 multi-disc game catalogue (games whose other discs share a
 * DISCS.TXT bootstrap file), and the logic that keeps that file in sync with
 * the games actually present in the current list.
 */
public final class MultiDiscGameCatalog {

    private static final String RESOURCE = "/ps2gm/game/manager/MultiDiscListPS1.txt";

    private static final List<String[]> MULTI_DISC_GAME_LIST = load();

    private MultiDiscGameCatalog() {}

    private static List<String[]> load() {
        List<String[]> groups = new ArrayList<>();
        try (InputStream in = MultiDiscGameCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                PopsGameManager.displayErrorMessageDebug("Missing bundled resource: " + RESOURCE);
                return groups;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        groups.add(line.split(","));
                    }
                }
            }
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
        return groups;
    }

    // The sibling disc IDs for gameID, or null if it isn't a known multi-disc game
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

    // One row for BadGameListController's suggestions list
    public record SuggestionRow(String gameId, String label, boolean multiDisc) {}

    /**
     * Turns raw name-match candidates from the API into display rows, expanding any
     * candidate that's part of a known multi-disc game into one row per sibling disc ID
     * never just the one fuzzy hit
     */
    public static List<SuggestionRow> expandForDisplay(List<GameSuggestion> rawSuggestions) {
        List<SuggestionRow> rows = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (GameSuggestion suggestion : rawSuggestions) {
            String[] siblings = siblingDiscsFor(suggestion.gameId());
            if (siblings == null) {
                if (seenIds.add(suggestion.gameId())) {
                    rows.add(new SuggestionRow(suggestion.gameId(),
                            suggestion.gameId() + " — " + suggestion.title() + " (" + suggestion.region() + ")",
                            false));
                }
                continue;
            }
            for (int i = 0; i < siblings.length; i++) {
                String discId = siblings[i];
                if (!seenIds.add(discId)) {
                    continue;
                }
                String label = discId + " — " + suggestion.title() + " (" + suggestion.region()
                        + ", Disc " + (i + 1) + " of " + siblings.length
                        + " — multi-disc, verify you have the right disc)";
                rows.add(new SuggestionRow(discId, label, true));
            }
        }
        return rows;
    }

    // Ensures each multi-disc game's DISCS.TXT lists all its other discs that are actually in gameListPS1
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

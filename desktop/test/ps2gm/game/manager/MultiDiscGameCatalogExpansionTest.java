package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MultiDiscGameCatalogExpansionTest {

    @Test
    void expandsAKnownMultiDiscMatchIntoOneRowPerSiblingDisc() {
        // The real danger case: the reference list only ever has disc 1's ID under
        // this name - the whole point of expandForDisplay is that both real discs
        // still show up as separate, clearly-labelled options.
        GameSuggestion raw = new GameSuggestion("SLUS_005.94", "Metal Gear Solid", "PS1", "NTSCU", 100);

        List<MultiDiscGameCatalog.SuggestionRow> rows = MultiDiscGameCatalog.expandForDisplay(List.of(raw));

        assertEquals(2, rows.size());
        assertEquals("SLUS_005.94", rows.get(0).gameId());
        assertTrue(rows.get(0).multiDisc());
        assertTrue(rows.get(0).label().contains("Disc 1 of 2"));
        assertEquals("SLUS_007.76", rows.get(1).gameId());
        assertTrue(rows.get(1).multiDisc());
        assertTrue(rows.get(1).label().contains("Disc 2 of 2"));
    }

    @Test
    void passesThroughANonMultiDiscMatchAsOneRow() {
        GameSuggestion raw = new GameSuggestion("SLUS_207.68", "Some Ordinary Game", "PS2", "NTSCU", 95);

        List<MultiDiscGameCatalog.SuggestionRow> rows = MultiDiscGameCatalog.expandForDisplay(List.of(raw));

        assertEquals(1, rows.size());
        assertEquals("SLUS_207.68", rows.get(0).gameId());
        assertFalse(rows.get(0).multiDisc());
        assertTrue(rows.get(0).label().contains("Some Ordinary Game"));
        assertTrue(rows.get(0).label().contains("NTSCU"));
    }

    @Test
    void dedupesWhenTwoRawSuggestionsLandInTheSameSiblingGroup() {
        // e.g. a fuzzy match returning both the NTSCJ base and Konami-Best reissue
        // rows that both happen to be members of the same disc group.
        GameSuggestion a = new GameSuggestion("SCPS_453.20", "Metal Gear Solid", "PS1", "NTSCJ", 100);
        GameSuggestion b = new GameSuggestion("SCPS_453.21", "Metal Gear Solid Best", "PS1", "NTSCJ", 90);

        List<MultiDiscGameCatalog.SuggestionRow> rows = MultiDiscGameCatalog.expandForDisplay(List.of(a, b));

        long distinctIds = rows.stream().map(MultiDiscGameCatalog.SuggestionRow::gameId).distinct().count();
        assertEquals(rows.size(), distinctIds, "no gameId should appear twice");
        assertEquals(3, rows.size()); // SCPS_453.20, SCPS_453.21, SCPS_453.22 - the whole group, once
    }

    @Test
    void dedupesIdenticalNonMultiDiscSuggestions() {
        GameSuggestion a = new GameSuggestion("SLUS_207.68", "Some Ordinary Game", "PS2", "NTSCU", 95);
        GameSuggestion b = new GameSuggestion("SLUS_207.68", "Some Ordinary Game", "PS2", "NTSCU", 95);

        List<MultiDiscGameCatalog.SuggestionRow> rows = MultiDiscGameCatalog.expandForDisplay(List.of(a, b));

        assertEquals(1, rows.size());
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        assertTrue(MultiDiscGameCatalog.expandForDisplay(List.of()).isEmpty());
    }
}

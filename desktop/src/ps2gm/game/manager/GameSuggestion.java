package ps2gm.game.manager;

import java.util.Map;

/** One fuzzy name-match candidate from {@code GET /games/suggest} - see {@link BackendClient#suggestGameIds}. */
public record GameSuggestion(String gameId, String title, String console, String region, int score) {

    public static GameSuggestion fromJson(Map<String, Object> json) {
        return new GameSuggestion(
                MiniJson.str(json, "game_id"),
                MiniJson.str(json, "title"),
                MiniJson.str(json, "console"),
                MiniJson.str(json, "region"),
                MiniJson.intVal(json, "score", 0));
    }
}

package ps2gm.game.manager;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bulk-renames the PS2 game files so the game ID sits at the start or the end of
 * the filename. OPL reads the real ID from inside the ISO, so either layout
 * boots and plays; this only changes how the files sort and read on disk (and
 * which layout {@link GameLongNameRenamer} produces for future renames).
 *
 * Local files only - SMB / USB modes. In HDD mode the game list is a cached
 * snapshot of the console with nothing local to rename. PS2 ART/CFG/CHT are
 * named by ID alone, so only the ISO/ZSO filename ever needs touching.
 */
public final class GameIdPositionSwitcher {

    private GameIdPositionSwitcher() {}

    /**
     * Where the game ID currently sits in the PS2 game files on disk - "start"
     * ("&lt;id&gt;.&lt;name&gt;"), "end" ("&lt;name&gt;.&lt;id&gt;" / "&lt;name&gt; (&lt;id&gt;)"),
     * or null if it can't tell (no games, or none have a descriptive part).
     * Majority wins; used to seed the menu's radio state.
     */
    public static String detectCurrentPS2Position() {
        List<Game> games = GameListManager.getGameListPS2();
        if (games == null) { return null; }

        int start = 0;
        int end = 0;
        for (Game game : games) {
            String id = game.getGameID();
            String path = game.getGamePath();
            if (id == null || id.isEmpty() || path == null || Boolean.TRUE.equals(game.getULGame())) { continue; }

            String base = new File(path).getName();
            int dot = base.lastIndexOf('.');
            if (dot > 0) { base = base.substring(0, dot); }
            if (base.equals(id)) { continue; }   // bare "<id>" - no descriptive part

            String q = Pattern.quote(id);
            if (base.matches("^" + q + "[\\s._-].*")) { start++; }
            else if (base.matches(".*[\\s._-]" + q + "$") || base.matches(".*[\\(\\[]\\s*" + q + "\\s*[\\)\\]].*")) { end++; }
        }
        if (start == 0 && end == 0) { return null; }
        return start > end ? "start" : "end";
    }

    /** Outcome of a {@link #switchAllPS2} run. */
    public static final class Result {
        public int renamed;
        public int skipped;   // UL games, missing files, already in place
        public int failed;    // rename() returned false
    }

    /**
     * Rename every PS2 game file in the current list so its ID is at
     * {@code position} ("start" or "end"). Updates each {@link Game}'s path in
     * place; the caller persists the new preference and refreshes the UI.
     */
    public static Result switchAllPS2(String position) {
        Result result = new Result();
        List<Game> games = GameListManager.getGameListPS2();
        if (games == null) { return result; }

        boolean idFirst = "start".equals(position);

        for (Game game : games) {
            String id = game.getGameID();
            String path = game.getGamePath();

            if (id == null || id.isEmpty() || path == null || Boolean.TRUE.equals(game.getULGame())) {
                result.skipped++;
                continue;
            }

            File iso = new File(path);
            String lower = iso.getName().toLowerCase();
            if (!iso.isFile() || !(lower.endsWith(".iso") || lower.endsWith(".zso"))) {
                result.skipped++;
                continue;
            }

            String ext = iso.getName().substring(iso.getName().length() - 4);   // keep .iso / .zso as typed
            String base = iso.getName().substring(0, iso.getName().length() - 4);
            String name = GameListManager.stripGameId(base, id);
            if (name.isEmpty() || name.equals(id)) {
                // bare "<id>.iso" - no descriptive part to move the ID around
                result.skipped++;
                continue;
            }

            String targetName = (idFirst ? id + "." + name : name + "." + id) + ext;
            if (targetName.equals(iso.getName())) {
                result.skipped++;
                continue;
            }

            File target = new File(iso.getParentFile(), targetName);
            if (iso.renameTo(target)) {
                game.setGamePath(target.toString());
                result.renamed++;
            } else {
                result.failed++;
                PopsGameManager.displayErrorMessageDebug(
                        "Could not rename " + iso.getName() + " -> " + targetName);
            }
        }
        return result;
    }
}

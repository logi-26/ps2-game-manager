package oplpops.game.manager;

import java.io.File;
import java.util.List;

/**
 * Builds and resolves the OPL ART filename for a game's cover/background/screenshot
 * images. PS1 and PS2 name these differently - PS1 embeds the game's OPL-visible
 * name and ".ELF" (matching the ELF OPL actually launches), PS2 just uses the game
 * ID - so this branches on PopsGameManager.getCurrentConsole(), the same pattern
 * GameConfigFileManager already uses for the equivalent CFG-file naming split.
 *
 * Used where BatchDownloadScreenPS1/PS2 and GameImageScreenPS1/PS2 (and its image
 * selector screens) need to check for or display an existing art file: they all
 * follow the same "does the .jpg exist, else the .png, else fall back" rule this
 * mirrors. A handful of other call sites (e.g. GameImageScreen's getImageFromServer,
 * which picks a single expected extension per file type with no fallback) have
 * different existence-check behaviour and only use baseName() below, not
 * isMissing()/resolve().
 */
final class GameArtFileManager {

    private GameArtFileManager() {}

    // The base OPL ART filename for a game's art of the given kind, without extension.
    // e.g. PS1: "<prefix><name>-<id>.ELF_COV", PS2: "<id>_COV"
    static String baseName(Game game, String suffix) {
        if ("PS1".equals(PopsGameManager.getCurrentConsole())) {
            return PopsGameManager.getFilePrefix() + game.getGameName() + "-" + game.getGameID() + ".ELF" + suffix;
        }
        return game.getGameID() + suffix;
    }

    // True if neither the .jpg nor .png variant is present in a pre-listed filename set.
    static boolean isMissing(List<String> artList, Game game, String suffix) {
        String base = baseName(game, suffix);
        return !artList.contains(base + ".jpg") && !artList.contains(base + ".png");
    }

    // Resolves the actual art file on disk for a game (checks .jpg then .png), or null if neither exists.
    static File resolve(Game game, String suffix) {
        File file = new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + baseName(game, suffix) + ".jpg");
        if (file.exists() && !file.isDirectory()) {return file;}
        file = new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + baseName(game, suffix) + ".png");
        if (file.exists() && !file.isDirectory()) {return file;}
        return null;
    }
}

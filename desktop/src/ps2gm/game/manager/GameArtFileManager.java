package ps2gm.game.manager;

import java.io.File;
import java.util.List;

/**
 * Builds and resolves the OPL ART filename for a game's cover/background/screenshot
 * images. PS1 and PS2 name these differently - PS1 embeds the game's OPL-visible
 * name and ".ELF" (matching the ELF OPL actually launches), PS2 just uses the game
 * ID - so this branches on PopsGameManager.getCurrentConsole(), the same pattern
 * GameConfigFileManager already uses for the equivalent CFG-file naming split.
 */
public final class GameArtFileManager {

    private GameArtFileManager() {}

    // The base OPL ART filename for a game's art of the given kind, without extension.
    public static String baseName(Game game, String suffix) {
        if (PopsGameManager.getCurrentConsole() == Console.PS1) {
            return PopsGameManager.getFilePrefix() + game.getGameName() + "-" + game.getGameID() + ".ELF" + suffix;
        }
        return game.getGameID() + suffix;
    }

    // True if neither the .jpg nor .png variant is present in a pre-listed filename set.
    public static boolean isMissing(List<String> artList, Game game, String suffix) {
        String base = baseName(game, suffix);
        return !artList.contains(base + ".jpg") && !artList.contains(base + ".png");
    }

    // Resolves the actual art file on disk for a game (checks .jpg then .png), or null if neither exists.
    public static File resolve(Game game, String suffix) {
        File file = new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + baseName(game, suffix) + ".jpg");
        if (file.exists() && !file.isDirectory()) {return file;}
        file = new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + baseName(game, suffix) + ".png");
        if (file.exists() && !file.isDirectory()) {return file;}
        return null;
    }

    // Deletes both the .jpg and .png variant of a game's art file, if present.
    public static void deleteAll(Game game, String suffix) {
        File file = new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + baseName(game, suffix) + ".jpg");
        if (file.exists() && !file.isDirectory()) {file.delete();}
        file = new File(PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + baseName(game, suffix) + ".png");
        if (file.exists() && !file.isDirectory()) {file.delete();}
    }
}

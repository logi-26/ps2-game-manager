package ps2gm.game.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

public class GameConfigFileManager {

    // The 24 keys this app edits in a game's own .cfg file, in NEW_CONFIG_DATA/configData's
    // index order (GameConfigScreen). Anything else found in the file - $AltStartup, $DMA,
    // $ConfigSource, PADEMU's own keys, the OSD-language block, or anything a newer OPL adds
    // that this list doesn't know about yet - is preserved untouched on save; see
    // writeGameConfigFile()'s merge-with-existing-file behaviour below.
    //
    // Index 22 is "$GSMFIELDFix" - OPL's real key (confirmed against its own include/config.h).
    // This used to be written here as "$GSMSkipVideos", which isn't a key OPL recognises at all,
    // so every "skip videos" checkbox a user saved was silently a no-op for real OPL.
    // Indices 24-26 are new: $GSMSource/$CheatsSource/$PADEMUSource ("0"=Global, "1"=PerGame -
    // matches OPL's own SETTINGS_GLOBAL/SETTINGS_PERGAME) - which of this game's own file vs.
    // the global conf_game.cfg OPL actually uses for that feature's settings. Read/written the
    // same safe way as every other managed key; see GameConfigScreen's Source radio buttons.
    private static final String[] MANAGED_KEYS = {
        "CfgVersion", "Title", "Genre", "Developer", "Release", "Players", "Rating",
        "Description", "Notes", "$VMC_0", "$VMC_1", "Cheat", "$EnableCheat", "Device",
        "Vmode", "Aspect", "Scan", "$Compatibility", "$EnableGSM", "$GSMVMode",
        "$GSMXOffset", "$GSMYOffset", "$GSMFIELDFix", "Parental",
        "$GSMSource", "$CheatsSource", "$PADEMUSource"
    };

    // The keys this app edits in the GLOBAL conf_game.cfg - OPL's own defaults for GSM/Cheat,
    // applied to a game whenever its own $GSMSource/$CheatsSource is 0 (Global) or absent.
    // Confirmed against OPL's own src/config.c: conf_game.cfg lives at the OPL root (same file
    // list as conf_apps.cfg, which this app already writes there) and uses the exact same
    // "Key=Value" line format as a per-game .cfg - no game identity in it, settings only.
    // $CheatMode isn't here: this app has no UI for it yet (only $EnableCheat, via
    // jCheckBoxCheatEnabled), and managing a key with nothing to write only means every save
    // clobbers whatever real value a user set through OPL itself - see MANAGED_KEYS' own
    // comment for the same reasoning.
    private static final String[] GLOBAL_MANAGED_KEYS = {
        "$EnableGSM", "$GSMVMode", "$GSMXOffset", "$GSMYOffset", "$GSMFIELDFix", "$EnableCheat"
    };

    // A handful of values carry a second, redundant "type/" sub-prefix baked into the value
    // itself - e.g. Rating is stored as "Rating=rating/4", not "Rating=4" - a long-standing quirk
    // of this app's own file format (composeGameConfigData() still writes it), not an OPL
    // key/value split. Indexed the same as MANAGED_KEYS; null everywhere else.
    // readGameConfigFormatted() strips this too, so callers keep getting the bare value they
    // always have (Parental is deliberately not here - its "esrb/17" form is still split on "/"
    // by the caller, so it needs the whole thing intact).
    private static final String[] VALUE_SUBPREFIXES = new String[MANAGED_KEYS.length];
    static {
        VALUE_SUBPREFIXES[5] = "players/";  // Players
        VALUE_SUBPREFIXES[6] = "rating/";   // Rating
        VALUE_SUBPREFIXES[13] = "device/";  // Device
        VALUE_SUBPREFIXES[14] = "vmode/";   // Vmode
        VALUE_SUBPREFIXES[15] = "aspect/";  // Aspect
        VALUE_SUBPREFIXES[16] = "scan/";    // Scan
    }

    public GameConfigFileManager(){}


    // This checks if their is a config file for the specific game
    public Boolean gameConfigExists(String gameID, String gameName){
        File cfgFile = gameID != null ? resolveGameConfigFile(gameID, gameName) : null;
        return cfgFile != null && cfgFile.exists() && !cfgFile.isDirectory();
    }


    // This reads the data from a game config file and returns an array containing the formatted data
    // (just the value half of each managed key's line, e.g. "CfgVersion=5" -> "5", and with
    // VALUE_SUBPREFIXES' extra "type/" stripped too where it applies, e.g. "rating/4" -> "4")
    public String[] readGameConfigFormatted(String gameID, String gameName) throws IOException {

        LinkedHashMap<String, String> lines = readRawLines(resolveGameConfigFile(gameID, gameName));
        String configData[] = new String[MANAGED_KEYS.length];

        for (int i = 0; i < MANAGED_KEYS.length; i++) {
            String line = lines.get(MANAGED_KEYS[i]);
            if (line != null) {
                String value = line.substring(MANAGED_KEYS[i].length() + 1);
                if (VALUE_SUBPREFIXES[i] != null && value.startsWith(VALUE_SUBPREFIXES[i])) {value = value.substring(VALUE_SUBPREFIXES[i].length());}
                configData[i] = value;
            }
        }
        return configData;
    }


    // This reads the data from a game config file and returns an array containing the raw
    // "Key=Value" lines (used to compare against a freshly-composed set before saving)
    public String[] readGameConfigRaw(String gameID, String gameName) throws IOException {

        LinkedHashMap<String, String> lines = readRawLines(resolveGameConfigFile(gameID, gameName));
        String configData[] = new String[MANAGED_KEYS.length];

        for (int i = 0; i < MANAGED_KEYS.length; i++) {configData[i] = lines.get(MANAGED_KEYS[i]);}
        return configData;
    }


    // This writes a config file for the specific game. Reads whatever's already there first and
    // merges the app's own managed fields into it, rather than overwriting the whole file - any
    // key this app doesn't know about survives untouched (see MANAGED_KEYS above).
    public void writeGameConfigFile(String newConfigData[], String gameID, String gameName){

        File cfgFile = resolveGameConfigFile(gameID, gameName);
        LinkedHashMap<String, String> lines = readRawLines(cfgFile);

        for (int i = 0; i < newConfigData.length && i < MANAGED_KEYS.length; i++) {
            if (newConfigData[i] != null) {lines.put(MANAGED_KEYS[i], newConfigData[i]);}
            else {lines.remove(MANAGED_KEYS[i]);}
        }

        writeRawLines(cfgFile, lines);
    }


    // This compares 2 sets of config data to determine if they are identical (the order can be different but the actual content must be identical)
    public boolean compareGameConfig(String firstConfigData[], String secondConfigData[]){

        ArrayList<String> newList = new ArrayList<>();
        ArrayList<String> storedList = new ArrayList<>();

        // Store the first array elements in a list if they do not equal null
        for (String configData1 : firstConfigData){if (configData1 != null) {newList.add(configData1);}}

        // Store the second array elements in a list if they do not equal null
        for (String configData2 : secondConfigData){if (configData2 != null) {storedList.add(configData2);}}

        // Sort the lists
        Collections.sort(newList);
        Collections.sort(storedList);

        return newList.equals(storedList);
    }


    // Checks the titles inside each game's config file against the current game list and rewrites
    // them to match where they differ (the user may have named the game file slightly differently).
    // Shared by BatchDownloadScreenPS1/PS2 after a batch download finishes - identical for both consoles,
    // since it only touches the CFG files' own Title= line, not the console-specific ART naming.
    static void renameConfigTitlesToMatch(List<Game> gameList){

        try (Stream<Path> paths = Files.walk(Paths.get(PopsGameManager.getOPLFolder() + File.separator + "CFG" + File.separator))) {
            paths.forEach(filePath -> {
                if (Files.isRegularFile(filePath)) {

                    gameList.stream().filter((game) -> (game.getGameID().equals(filePath.getFileName().toString().substring(0, filePath.getFileName().toString().length()-4)))).forEachOrdered((game) -> {
                        try {
                            List<String> fileContent = new ArrayList<>(Files.readAllLines(Paths.get(PopsGameManager.getOPLFolder() + File.separator + "CFG" + File.separator + game.getGameID() + ".cfg"), StandardCharsets.UTF_8));
                            if (fileContent.get(1).substring(0, 5).equals("Title")) {fileContent.set(1, "Title=" + game.getGameName());}
                            Files.write(Paths.get(PopsGameManager.getOPLFolder() + File.separator + "CFG" + File.separator + game.getGameID() + ".cfg"), fileContent, StandardCharsets.UTF_8);
                        } catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
                    });
                }
            });
        } catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
    }


    // Reads conf_game.cfg (OPL's global GSM/Cheat defaults), in GLOBAL_MANAGED_KEYS' index order.
    public String[] readGlobalConfig(){
        LinkedHashMap<String, String> lines = readRawLines(resolveGlobalConfigFile());
        String configData[] = new String[GLOBAL_MANAGED_KEYS.length];
        for (int i = 0; i < GLOBAL_MANAGED_KEYS.length; i++) {
            String line = lines.get(GLOBAL_MANAGED_KEYS[i]);
            if (line != null) {configData[i] = line.substring(GLOBAL_MANAGED_KEYS[i].length() + 1);}
        }
        return configData;
    }


    // Writes conf_game.cfg, merging into whatever's already there the same way
    // writeGameConfigFile() does for a per-game file - anything else OPL keeps in this file
    // (network/BGM/theme settings etc.) is preserved untouched.
    public void writeGlobalConfig(String newConfigData[]){
        File file = resolveGlobalConfigFile();
        LinkedHashMap<String, String> lines = readRawLines(file);
        for (int i = 0; i < newConfigData.length && i < GLOBAL_MANAGED_KEYS.length; i++) {
            if (newConfigData[i] != null) {lines.put(GLOBAL_MANAGED_KEYS[i], newConfigData[i]);}
            else {lines.remove(GLOBAL_MANAGED_KEYS[i]);}
        }
        writeRawLines(file, lines);
    }


    private static File resolveGlobalConfigFile(){
        return new File(PopsGameManager.getOPLFolder() + File.separator + "conf_game.cfg");
    }


    // The per-game .cfg path, PS1 and PS2 naming conventions differ (see GameArtFileManager for
    // the equivalent ART-naming split) - factored out since every method above needed it.
    private static File resolveGameConfigFile(String gameID, String gameName){
        if ("PS1".equals(PopsGameManager.getCurrentConsole())) {
            return new File(PopsGameManager.getOPLFolder() + File.separator + "CFG" + File.separator + PopsGameManager.getFilePrefix() + gameName + "-" + gameID + ".ELF.cfg");
        }
        return new File(PopsGameManager.getOPLFolder() + File.separator + "CFG" + File.separator + gameID + ".cfg");
    }


    // Reads a "Key=Value" file into an ordered map (key -> whole line), preserving file order.
    // Shared by the per-game .cfg methods above and GlobalConfigManager's conf_game.cfg methods -
    // both use the exact same line format (confirmed against OPL's own configWrite(), which
    // writes every config type - conf_game.cfg included - as plain "%s=%s\r\n" lines).
    static LinkedHashMap<String, String> readRawLines(File file){
        LinkedHashMap<String, String> lines = new LinkedHashMap<>();
        if (file.exists() && !file.isDirectory()){
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        int eq = line.indexOf('=');
                        String key = eq >= 0 ? line.substring(0, eq) : line;
                        lines.put(key, line);
                    }
                }
            } catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
        }
        return lines;
    }


    // Writes an ordered "Key=Value" map back out, one line per entry, in map order.
    static void writeRawLines(File file, LinkedHashMap<String, String> lines){
        try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
            for (String line : lines.values()) {writer.println(line);}
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
    }
}

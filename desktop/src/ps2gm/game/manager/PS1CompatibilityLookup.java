package ps2gm.game.manager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Looks up a PS1 game's OPL compatibility-mode flags (USB/HDD/SMB) from the
 * bundled PS1CompatabilityList.txt resource, by fixed-column game-ID match -
 * e.g. "SLPS_019.86-USB=0-HDD=0-SMB=0" has the USB flag at column 16, HDD at
 * 22, SMB at 28.
 *
 * Previously duplicated three times (GameListManager.createGameListFromFile,
 * GameListManager.createPS1ListFromSMB, MyFTPClient.getGameListPS1), each
 * leaking its BufferedReader (opened without try-with-resources).
 */
public final class PS1CompatibilityLookup {

    private static final String RESOURCE = "/ps2gm/game/manager/PS1CompatabilityList.txt";
    private static final String DEFAULT_FLAG = "0";

    private PS1CompatibilityLookup() {}

    /** The three per-game compatibility flags, in the file's column order. */
    public record Compatibility(String usb, String hdd, String smb) {
        public static final Compatibility UNKNOWN =
                new Compatibility(DEFAULT_FLAG, DEFAULT_FLAG, DEFAULT_FLAG);
    }

    /** Returns {@link Compatibility#UNKNOWN} if {@code gameId} isn't listed. */
    public static Compatibility lookup(String gameId) {
        try (InputStream in = PS1CompatibilityLookup.class.getResourceAsStream(RESOURCE);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() >= 29 && line.substring(0, 11).equals(gameId)) {
                    return new Compatibility(line.substring(16, 17), line.substring(22, 23), line.substring(28, 29));
                }
            }
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
        return Compatibility.UNKNOWN;
    }
}

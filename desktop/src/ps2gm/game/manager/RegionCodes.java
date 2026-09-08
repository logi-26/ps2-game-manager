package ps2gm.game.manager;

import java.util.List;

/**
 * The disc-serial region-code prefixes ("SLUS_", "SCES_", ...) used to spot a
 * PS1/PS2 game ID inside a filename or archive listing. Previously declared
 * independently in GameListManager, HDLDumpManager (twice) and MyFTPClient.
 */
public final class RegionCodes {

    public static final List<String> ALL = List.of(
            "SCES_", "SLES_", "SCUS_", "SLUS_", "SLPS_", "SCAJ_", "SLKA_", "SLPM_", "SCPS_");

    private RegionCodes() {}
}

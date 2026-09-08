package ps2gm.game.manager;

/**
 * How the app reaches the console's game storage: a network share (SMB), a
 * USB drive mounted on this PC (HDD_USB), or the console's internal HDD,
 * reached over hdl_dump + FTP (HDD).
 */
public enum Mode {
    SMB,
    HDD_USB,
    HDD
}

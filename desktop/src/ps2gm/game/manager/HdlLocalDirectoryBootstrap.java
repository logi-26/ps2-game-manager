package ps2gm.game.manager;

import java.io.File;

/**
 * Creates the local hdd/hdd_usb mirror directories (ART/CFG/CHT/POPS/VMC)
 * used when running in local HDD/USB modes.
 *
 * Extracted from HDLDumpManager.createLocalHDLDirectory.
 */
final class HdlLocalDirectoryBootstrap {

    private HdlLocalDirectoryBootstrap() {}

    static boolean createLocalHDLDirectory(String directoryName) {

        File hdlLocalDirectory = new File(PopsGameManager.getCurrentDirectory() + File.separator + directoryName + File.separator);
        boolean directoriesCreated = false;

        if (!hdlLocalDirectory.exists()) {
            if (hdlLocalDirectory.mkdir()) {
                // BUG FIX (Phase 1): previously each line overwrote directoriesCreated instead of
                // accumulating, so only the last (VMC) mkdirs() result was ever reported.
                directoriesCreated = (new File(PopsGameManager.getCurrentDirectory() + File.separator + directoryName + File.separator + "ART" + File.separator)).mkdirs();
                directoriesCreated &= (new File(PopsGameManager.getCurrentDirectory() + File.separator + directoryName + File.separator + "CFG" + File.separator)).mkdirs();
                directoriesCreated &= (new File(PopsGameManager.getCurrentDirectory() + File.separator + directoryName + File.separator + "CHT" + File.separator)).mkdirs();
                directoriesCreated &= (new File(PopsGameManager.getCurrentDirectory() + File.separator + directoryName + File.separator + "POPS" + File.separator)).mkdirs();
                directoriesCreated &= (new File(PopsGameManager.getCurrentDirectory() + File.separator + directoryName + File.separator + "VMC" + File.separator)).mkdirs();
            }
        }
        return hdlLocalDirectory.exists() || directoriesCreated;
    }
}

package ps2gm.game.manager;

import java.io.File;

/**
 * Creates the local hdd/hdd_usb mirror directories (ART/CFG/CHT/POPS/VMC)
 * used when running in local HDD/USB modes.
 */
final class HdlLocalDirectoryBootstrap {

    private HdlLocalDirectoryBootstrap() {}

    static boolean createLocalHDLDirectory(String directoryName) {

        File hdlLocalDirectory = new File(PopsGameManager.getCurrentDirectory() + File.separator + directoryName + File.separator);
        boolean directoriesCreated = false;

        if (!hdlLocalDirectory.exists()) {
            if (hdlLocalDirectory.mkdir()) {
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

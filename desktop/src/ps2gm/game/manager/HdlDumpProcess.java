package ps2gm.game.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the OS-appropriate hdl_dump executable and starts it as a
 * subprocess. Previously duplicated (OS detection + ProcessBuilder setup)
 * across HDLDumpManager.hdlDumpGetTOC/runUpload/runBatchUpload.
 */
final class HdlDumpProcess {

    private HdlDumpProcess() {}

    /** Which OS-specific hdl_dump build to run. */
    record Executable(String folder, String name) {
        String path() {
            return PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "tools" + File.separator + folder + File.separator + name;
        }

        String toolsDirectory() {
            return PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "tools" + File.separator + folder;
        }
    }

    static Executable resolveExecutable() {
        if (PopsGameManager.getOSType().equals("Linux") || PopsGameManager.getOSType().equals("Mac")) {
            return new Executable("linux", "hdl_dump");
        }
        return new Executable("windows", "hdl_dump.exe");
    }

    /** Starts hdl_dump with the given trailing arguments (after the executable path itself). */
    static Process start(Executable exe, List<String> args) throws IOException {
        List<String> commands = new ArrayList<>();
        commands.add(exe.path());
        commands.addAll(args);

        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.directory(new File(exe.toolsDirectory()));
        return processBuilder.start();
    }
}

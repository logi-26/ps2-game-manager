package ps2gm.game.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

/**
 * Uploads PS2 games to the console via hdl_dump (single or batch), tracking
 * progress for the caller's {@link FtpTransferProgress} UI, and fetches the
 * console's game list (TOC).
 *
 * See {@link HdlDumpProcess} for the shared executable-resolution/process
 * startup, {@link HdlDumpToc} for the TOC fetch, and
 * {@link HdlLocalDirectoryBootstrap} for the local hdd mirror directories.
 */
public class HDLDumpManager {
    private String timeRemaining = "00:00";
    private int percentDownloaded = 0;

    public HDLDumpManager() {}


    // Closes hdl_dump's stdout/stderr readers once they're done with, swallowing (but logging) any close failure
    private static void closeQuietly(BufferedReader... readers){
        for (BufferedReader reader : readers){
            if (reader != null) {try {reader.close();} catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}}
        }
    }


    // Upload a PS2 game to the console using HDL_Dump (on a daemon thread; reports through FtpTransferProgress)
    public void hdlDumpUploadGame(FtpTransferProgress progress, String destination, String gameName, String gamePath) throws IOException, InterruptedException{
        timeRemaining = "0:00";
        Thread worker = new Thread(() -> runUpload(progress, destination, gameName, gamePath), "hdl-dump-upload");
        worker.setDaemon(true);
        worker.start();
    }


    // Batch upload PS2 games to the console using HDL_Dump (on a daemon thread; reports through FtpTransferProgress)
    public void hdlDumpUploadGameBatch(FtpTransferProgress progress, String destination, ArrayList<Path> gamePathList) throws IOException, InterruptedException{
        timeRemaining = "0:00";
        Thread worker = new Thread(() -> runBatchUpload(progress, destination, gamePathList), "hdl-dump-upload-batch");
        worker.setDaemon(true);
        worker.start();
    }


    // This gets the game list from the console using hdd_dump - see HdlDumpToc.
    public List<Game> hdlDumpGetTOC(String destination) throws IOException, InterruptedException{
        return HdlDumpToc.fetch(destination);
    }


    // Batch upload: multiple games to the console via hdl_dump, reporting through FtpTransferProgress.
    // Was an inner SwingWorker (BatchBackgroundWorker); now a plain method run on a daemon thread.
    private void runBatchUpload(FtpTransferProgress progress, String destination, ArrayList<Path> gamePathList) {

        String name = null;
        String path;
        String downloadSpeed = null;
        boolean errorConnecting = false;

        try {
            HdlDumpProcess.Executable exe = HdlDumpProcess.resolveExecutable();

            // Loop through all of the games
            int count = 0;
            for (Path game : gamePathList){

                count += 1;

                // Get the game name and path
                name = game.getFileName().toString().substring(0, game.getFileName().toString().length()-4);
                path = game.toString();

                // If the game name contains the region code at the start of the name, this removes it
                if (name.length() > 4){for (String regionCode : RegionCodes.ALL){if (name.substring(0, 5).equals(regionCode)) {name = name.substring(12, name.length());}}}

                // Set the game name in the text field
                progress.setGameName(" " + name);

                // Set the game ptrocessed counter in the text field
                progress.setGameCounter(count + "/" + gamePathList.size());

                Process process = HdlDumpProcess.start(exe, List.of("inject_dvd", destination, name, path, "*u4"));

                // Buffers for storing the command line output from hdl_dump
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                // Read the output from the command
                String line = null;

                while ((line = stdInput.readLine()) != null) {
                    HdlDumpProgressParser.Progress result = HdlDumpProgressParser.parseLine(line, percentDownloaded, timeRemaining);
                    percentDownloaded = result.percentDownloaded();
                    timeRemaining = result.timeRemaining();
                    if (result.downloadSpeed() != null) {
                        downloadSpeed = result.downloadSpeed();
                    }

                    if (downloadSpeed != null){
                        progress.setTimeRemaining(timeRemaining);
                        progress.setUploadSpeed(downloadSpeed);
                    }

                    progress.setProgress(percentDownloaded);
                }

                // Wait for HDL_Dump
                process.waitFor();

                while ((line = stdError.readLine()) != null) {errorConnecting = true;}
                closeQuietly(stdInput, stdError);

                if (errorConnecting) JOptionPane.showMessageDialog(null, "HDL_Dump reported an error! \n\nPlease ensure that you have HDL_Server running on your PlayStation 2 console. \nAlso make sure that you have enetered the correct IP address.", " HDL_Dump Error!", JOptionPane.ERROR_MESSAGE);

            // END OF LOOP!!
            }
        }
        catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
        finally {

            if (!errorConnecting) {
                progress.setTimeRemaining("00:00");
                progress.setUploadSpeed("0MB/sec");
                progress.setProgress(100);
            }

            progress.setInProgress(false);
            progress.closeWindow();

            List<Game> gameList = null;

            // Try and get the PS2 game list from the console, write the game list.dat file, callback to update the main gui
            HDLDumpManager hdlDump = new HDLDumpManager();
            try {
                gameList = hdlDump.hdlDumpGetTOC(PopsGameManager.getPS2IP());

                if (gameList != null && gameList.size() >0){
                    GameListManager.writeGameListFilePS2(gameList);

                    // Try and load the game data from the PS2 game list file
                    try {GameListManager.createGameListFromFile("PS2", new File(PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator + "gameListPS2"));} catch (IOException ex) {PopsGameManager.displayErrorMessageDebug("Error creating the PS2 game list from file!\n\n" + ex.toString());}
                }
            }
            catch (IOException | InterruptedException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}

            if (gameList != null && gameList.size()>0){PopsGameManager.callbackToUpdateGUIGameList(null, gameList.size()-1);}
        }
    }


    // Single upload: one game to the console via hdl_dump, reporting through FtpTransferProgress.
    // Was an inner SwingWorker (BackgroundWorker); now a plain method run on a daemon thread.
    private void runUpload(FtpTransferProgress progress, String destination, String name, String path) {

        String downloadSpeed = null;
        boolean errorConnecting = false;

        try {
            HdlDumpProcess.Executable exe = HdlDumpProcess.resolveExecutable();
            Process process = HdlDumpProcess.start(exe, List.of("inject_dvd", destination, name, path, "*u4"));

            // Buffers for storing the command line output from hdl_dump
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            // Read the output from the command
            String line = null;

            while ((line = stdInput.readLine()) != null) {
                HdlDumpProgressParser.Progress result = HdlDumpProgressParser.parseLine(line, percentDownloaded, timeRemaining);
                percentDownloaded = result.percentDownloaded();
                timeRemaining = result.timeRemaining();
                if (result.downloadSpeed() != null) {
                    downloadSpeed = result.downloadSpeed();
                }

                if (downloadSpeed != null){
                    progress.setTimeRemaining(timeRemaining);
                    progress.setUploadSpeed(downloadSpeed);
                }

                progress.setProgress(percentDownloaded);
            }

            // Wait for HDL_Dump
            process.waitFor();

            while ((line = stdError.readLine()) != null) {errorConnecting = true;}
            closeQuietly(stdInput, stdError);

            if (errorConnecting) JOptionPane.showMessageDialog(null, "HDL_Dump reported an error! \n\nPlease ensure that you have HDL_Server running on your PlayStation 2 console. \nAlso make sure that you have enetered the correct IP address.", " HDL_Dump Error!", JOptionPane.ERROR_MESSAGE);
        }
        catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
        finally {

            if (!errorConnecting) {
                progress.setTimeRemaining("00:00");
                progress.setUploadSpeed("0MB/sec");
                progress.setProgress(100);
            }

            progress.setInProgress(false);
            // NOTE: the single (non-batch) PS2 upload deliberately leaves its window open.

            List<Game> gameList = null;

            // Try and get the PS2 game list from the console, write the game list.dat file, callback to update the main gui
            HDLDumpManager hdlDump = new HDLDumpManager();
            try {
                gameList = hdlDump.hdlDumpGetTOC(PopsGameManager.getPS2IP());

                if (gameList != null && gameList.size() >0){
                    GameListManager.writeGameListFilePS2(gameList);

                    // Try and load the game data from the PS2 game list file
                    try {GameListManager.createGameListFromFile("PS2", new File(PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator + "gameListPS2"));} catch (IOException ex) {PopsGameManager.displayErrorMessageDebug("Error creating the PS2 game list from file!\n\n" + ex.toString());}
                }
            }
            catch (IOException | InterruptedException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}

            if (gameList != null && gameList.size()>0){PopsGameManager.callbackToUpdateGUIGameList(null, gameList.size()-1);}
        }
    }
}

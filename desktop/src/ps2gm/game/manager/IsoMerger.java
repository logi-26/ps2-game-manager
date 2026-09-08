package ps2gm.game.manager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges a PS2 game's split USB-Advance fragments back into a single ISO, on
 * a background daemon thread.
 *
 * Extracted from USBUtil.BackgroundWorkerMergeGame (promoted to a top-level
 * class - it had no external references by name, only via USBUtil.joinFiles).
 */
public final class IsoMerger implements Runnable {

    private final SplitMergeProgress ui;
    private final String gameID;

    private IsoMerger(SplitMergeProgress ui, String gameID) {
        this.ui = ui;
        this.gameID = gameID;
    }

    /** Starts the merge on a new daemon thread. */
    public static void start(SplitMergeProgress ui, String gameID) {
        BackgroundTasks.runDaemon("usbutil-merge", new IsoMerger(ui, gameID));
    }

    @Override
    public void run() {
        try {
            doInBackground();
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
        done();
    }

    private Object doInBackground() throws Exception {

        int maxReadBufferSize = 8 * 1024;
        String gameName = gameID + ".GAMENAME";

        // Try and get the actual game name from the ul.cfg file
        for (Game ulGame : UlCfgFileFormat.read()) {
            if (ulGame.getGameID().equals(gameID)) {
                gameName = gameID + "." + ulGame.getGameName();
            }
        }

        // List all of the split files for the selected game
        File[] allFiles = new File(PopsGameManager.getOPLFolder()).listFiles();
        List<File> splitFiles = new ArrayList<>();
        for (File ulSplitFile : allFiles) {
            if (ulSplitFile.getName().contains(gameID) && ulSplitFile.getName().substring(ulSplitFile.getName().length() - 2, ulSplitFile.getName().length() - 1).equals("0")) {
                splitFiles.add(ulSplitFile);
            }
        }

        // Join the files to create the original ISO file
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(PopsGameManager.getOPLFolder() + File.separator + "DVD" + File.separator + gameName + ".iso"))) {

            int fileCount = 0;
            long totalBytesProcessed = 0;
            for (File file : splitFiles) {

                fileCount++;
                ui.setPartsText(fileCount + "/" + splitFiles.size());

                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                long numReads = randomAccessFile.length() / maxReadBufferSize;
                long numRemainingRead = randomAccessFile.length() % maxReadBufferSize;

                ui.setProgressRange(0, numReads * splitFiles.size());

                for (int i = 0; i < numReads; i++) {
                    IsoFragmentIO.readWrite(randomAccessFile, bufferedOutputStream, maxReadBufferSize);
                    totalBytesProcessed++;
                    ui.setProgress(totalBytesProcessed);
                }

                if (numRemainingRead > 0) {
                    IsoFragmentIO.readWrite(randomAccessFile, bufferedOutputStream, numRemainingRead);
                }
                randomAccessFile.close();
                file.delete();
            }

            // Set the games UL value to false now that the game has been merged back into a single ISO file
            for (Game gameFromList : GameListManager.getGameListPS2()) {
                if (gameFromList.getGameID().equals(gameID)) {
                    gameFromList.setULGame(false);
                    gameFromList.setNumberOfParts(0);
                    gameFromList.setGamePath(PopsGameManager.getOPLFolder() + File.separator + "DVD" + File.separator + gameName + ".iso");
                }
            }
        }

        return null;
    }

    private void done() {

        // Create the ul.cfg file
        List<Game> ulGameList = new ArrayList<>();
        GameListManager.getGameListPS2().stream().filter(Game::getULGame).forEachOrdered(ulGameList::add);
        UlCfgFileFormat.write(ulGameList);

        // update the main game list in the GUI and close the split/merge dialog screen
        GameListManager.createGameListsPS2(false);
        PopsGameManager.callbackToUpdateGUIGameList(gameID, -1);
        ui.finished();
    }
}

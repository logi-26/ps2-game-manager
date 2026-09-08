package ps2gm.game.manager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a PS2 ISO into OPL's 1GB "ul.&lt;hex&gt;.&lt;gameID&gt;.&lt;n&gt;"
 * USB-Advance fragments, on a background daemon thread.
 *
 * Extracted from USBUtil.BackgroundWorkerSplitGame (promoted to a top-level
 * class - it had no external references by name, only via USBUtil.splitFile).
 */
public final class IsoSplitter implements Runnable {

    private final SplitMergeProgress ui;
    private final Game selectedGame;

    private IsoSplitter(SplitMergeProgress ui, Game selectedGame) {
        this.ui = ui;
        this.selectedGame = selectedGame;
    }

    /** Starts the split on a new daemon thread. */
    public static void start(SplitMergeProgress ui, Game selectedGame) {
        Thread t = new Thread(new IsoSplitter(ui, selectedGame), "usbutil-split");
        t.setDaemon(true);
        t.start();
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

        // Rename the ISO file and game path to remove the game ID
        File selectedISO = new File(selectedGame.getGamePath());
        File renamedISO = new File(selectedGame.getGamePath().substring(0, selectedGame.getGamePath().lastIndexOf(File.separator) + 1) + selectedGame.getGameName() + ".ISO");

        if (selectedISO.exists() && selectedISO.isFile()) {
            selectedISO.renameTo(renamedISO);
            selectedGame.setGamePath(renamedISO.getAbsolutePath());
        }

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(selectedGame.getGamePath(), "r")) {

            long bytesPerSplit = 1073741824; // 1GB chunks
            double isoSize = randomAccessFile.length();
            double splitSize = isoSize / 1073741824;
            long remainingBytes = Long.parseLong(String.valueOf(splitSize).substring(2, String.valueOf(splitSize).length()));
            int numberOfOutputFiles = IsoFragmentIO.getNumberOfFiles(randomAccessFile);
            int maxReadBufferSize = 8 * 1024; // 8KB
            long totalBytesProcessed = 0;

            for (int destIx = 1; destIx <= numberOfOutputFiles; destIx++) {

                ui.setPartsText(destIx + "/" + numberOfOutputFiles);

                try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(
                        PopsGameManager.getOPLFolder() + File.separator + "ul." + UlHexNaming.toHex(selectedGame.getGameName().getBytes()) + "." + selectedGame.getGameID() + "." + "0" + (destIx - 1)))) {
                    if (bytesPerSplit > maxReadBufferSize) {
                        long numReads = bytesPerSplit / maxReadBufferSize;
                        long numRemainingRead = bytesPerSplit % maxReadBufferSize;

                        ui.setProgressRange(0, numReads * numberOfOutputFiles);

                        for (int i = 0; i < numReads; i++) {
                            IsoFragmentIO.readWrite(randomAccessFile, bufferedOutputStream, maxReadBufferSize);
                            totalBytesProcessed++;
                            ui.setProgress(totalBytesProcessed);
                        }

                        if (numRemainingRead > 0) {
                            IsoFragmentIO.readWrite(randomAccessFile, bufferedOutputStream, numRemainingRead);
                        }
                    } else {
                        IsoFragmentIO.readWrite(randomAccessFile, bufferedOutputStream, bytesPerSplit);
                    }
                }
            }

            selectedGame.setULGame(true);
            selectedGame.setNumberOfParts(numberOfOutputFiles);

            // Add this game to the ul-backup file
            UlBackupStore.record(selectedGame.getGameName().getBytes(), UlHexNaming.toHex(selectedGame.getGameName().getBytes()), selectedGame.getGameID(), numberOfOutputFiles, selectedGame.getGameRawSize());

            // This often throws an error?
            if (remainingBytes > 0) {
                try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream("split." + numberOfOutputFiles + 1))) {
                    IsoFragmentIO.readWrite(randomAccessFile, bufferedOutputStream, remainingBytes);
                }
            }
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }

        return null;
    }

    private void done() {

        // Delete the original ISO file
        new File(selectedGame.getGamePath()).delete();

        // Create the ul.cfg file
        List<Game> ulGameList = new ArrayList<>();
        GameListManager.getGameListPS2().stream().filter(Game::getULGame).forEachOrdered(ulGameList::add);
        UlCfgFileFormat.write(ulGameList);

        // update the main game list in the GUI and close the split/merge dialog screen
        GameListManager.createGameListsPS2(false);
        PopsGameManager.callbackToUpdateGUIGameList(selectedGame.getGameID(), -1);
        ui.finished();
    }
}

package ps2gm.game.manager;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Shared chunked read/write helpers used by {@link IsoSplitter} and {@link IsoMerger}. */
final class IsoFragmentIO {

    private IsoFragmentIO() {}

    static void readWrite(RandomAccessFile randomAccessFile, BufferedOutputStream bufferedOutputStream, long numBytes) throws IOException {
        byte[] buffer = new byte[(int) numBytes];
        int value = randomAccessFile.read(buffer);
        if (value != -1) {bufferedOutputStream.write(buffer);}
    }

    /** How many 1GB fragments splitting this file will produce. */
    static int getNumberOfFiles(RandomAccessFile randomAccessFile) {
        int numberOfOutputFiles = 0;
        try {
            double isoSize = randomAccessFile.length();
            double splitSize = isoSize / 1073741824;
            String fullFragments = String.valueOf(splitSize).substring(0, 1);
            int numOfFullFragments = Integer.parseInt(fullFragments);
            numberOfOutputFiles = numOfFullFragments + 1;
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
        return numberOfOutputFiles;
    }
}

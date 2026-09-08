package ps2gm.game.manager;

import java.util.Arrays;

/**
 * A parsed OPL-style remote path such as {@code "hdd0:/+OPL/APPS"} - drive
 * ("hdd"/"mass"), partition digit, and the folder portion after the
 * drive:partition prefix.
 *
 * Replaces GameListManager's six near-identical getFormattedXXXDrive/
 * Partition/Folder methods, which each re-split the same
 * PopsGameManager.getRemote*Path() strings by hand.
 */
public record RemotePath(String drive, String partition, String folder) {

    public static RemotePath parse(String remotePath) {
        String[] segments = remotePath.split("/");
        String head = segments[0];
        String drive = head.substring(0, head.length() - 2);
        String partition = head.substring(head.length() - 2, head.length() - 1);
        String folder = String.join("/", Arrays.copyOfRange(segments, 1, segments.length));
        return new RemotePath(drive, partition, folder);
    }
}

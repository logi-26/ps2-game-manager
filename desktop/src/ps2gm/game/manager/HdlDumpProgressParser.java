package ps2gm.game.manager;

/**
 * Parses one line of hdl_dump's stdout progress output into percent
 * downloaded / time remaining / transfer speed. Was duplicated near-verbatim
 */
public final class HdlDumpProgressParser {

    public record Progress(int percentDownloaded, String timeRemaining, String downloadSpeed) {}

    private HdlDumpProgressParser() {}

    public static Progress parseLine(String rawLine, int previousPercent, String previousTimeRemaining) {
        String line = rawLine.replace(" ", "");
        int percentDownloaded = previousPercent;
        String timeRemaining = previousTimeRemaining;
        String downloadSpeed = null;

        if (line.contains(",")) {
            String[] fields = line.split(",");
            if (fields.length == 4) {
                percentDownloaded = leadingDigits(fields[0]);
                int minutes = leadingDigits(fields[1]);
                int seconds = leadingDigits(fields[2]);
                timeRemaining = minutes + ":" + seconds;
                downloadSpeed = fields[3];
            } else if (fields.length == 3) {
                percentDownloaded = leadingDigits(fields[0]);
                int seconds = leadingDigits(fields[1]);
                if (fields[1].contains("secremaining")) {
                    timeRemaining = "0:" + seconds;
                } else if (fields[1].contains("minremaining")) {
                    timeRemaining = seconds + ":00";
                }
                downloadSpeed = fields[2];
            }
        } else if (line.length() == 4) {
            percentDownloaded = Integer.parseInt(line.substring(0, 3));
        } else if (!line.isEmpty()) {
            percentDownloaded = Integer.parseInt(line.substring(0, 1));
        }

        return new Progress(percentDownloaded, timeRemaining, downloadSpeed);
    }

    // The numeric value of the leading 1-2 digits in the field, or 0 if there are no leading digits
    private static int leadingDigits(String field) {
        int end = 0;
        while (end < field.length() && end < 2 && Character.isDigit(field.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(field.substring(0, end));
    }
}

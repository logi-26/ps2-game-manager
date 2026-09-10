package ps2gm.game.manager;

/* Formats a duration in seconds as mm:ss or hh:mm:ss. */
public final class DurationFormatter {

    private DurationFormatter() {}

    public static String toDurationString(int totalSeconds, boolean usingHours) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (usingHours) {
            return twoDigits(hours) + ":" + twoDigits(minutes) + ":" + twoDigits(seconds);
        }
        return twoDigits(minutes) + ":" + twoDigits(seconds);
    }

    private static String twoDigits(int number) {
        return number < 10 ? "0" + number : String.valueOf(number);
    }
}

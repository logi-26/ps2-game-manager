package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationFormatterTest {

    @Test
    void formatsMinutesAndSeconds() {
        assertEquals("02:05", DurationFormatter.toDurationString(125, false));
    }

    @Test
    void formatsHoursMinutesSeconds() {
        assertEquals("01:02:05", DurationFormatter.toDurationString(3725, true));
    }

    @Test
    void padsSingleDigits() {
        assertEquals("00:09", DurationFormatter.toDurationString(9, false));
    }

    @Test
    void zeroIsDoubleZero() {
        assertEquals("00:00", DurationFormatter.toDurationString(0, false));
    }
}

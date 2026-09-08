package ps2gm.game.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DeviceCompatFormatterTest {

    @Test
    void decodesEachSingleDeviceCode() {
        assertEquals("USB", DeviceCompatFormatter.format("1"));
        assertEquals("ETH", DeviceCompatFormatter.format("5"));
        assertEquals("HDD", DeviceCompatFormatter.format("6"));
    }

    @Test
    void decodesEachTwoDeviceCode() {
        assertEquals("USB, ETH", DeviceCompatFormatter.format("2"));
        assertEquals("USB, HDD", DeviceCompatFormatter.format("3"));
        assertEquals("HDD, ETH", DeviceCompatFormatter.format("4"));
    }

    @Test
    void decodesAllDevicesCode() {
        assertEquals("USB, HDD, ETH", DeviceCompatFormatter.format("all"));
    }

    @Test
    void returnsEmptyStringForNullCode() {
        assertEquals("", DeviceCompatFormatter.format(null));
    }

    @Test
    void returnsEmptyStringForUnrecognisedCode() {
        assertEquals("", DeviceCompatFormatter.format("?"));
    }
}

package ps2gm.game.manager.fx;

import ps2gm.game.manager.Console;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.EmulatorSettingsScreen}.
 * {@code console} is {@link Console#PS1} (PCSXR) or {@link Console#PS2} (PCSX2).
 */
public final class EmulatorSettingsScreen {

    private EmulatorSettingsScreen() {}

    public static void open(Console console) {
        FxScreens.open("EmulatorSettingsScreen.fxml", " Emulator Settings", false,
                (EmulatorSettingsController c) -> c.setConsole(console));
    }
}

package ps2gm.game.manager.fx;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.EmulatorSettingsScreen}.
 * {@code console} is "PS1" (PCSXR) or "PS2" (PCSX2).
 */
public final class EmulatorSettingsScreen {

    private EmulatorSettingsScreen() {}

    public static void open(String console) {
        FxScreens.open("EmulatorSettingsScreen.fxml", " Emulator Settings", false,
                (EmulatorSettingsController c) -> c.setConsole(console));
    }
}

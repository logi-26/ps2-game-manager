package ps2gm.game.manager.fx;

import java.io.File;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.AddGameSMBScreen}
 * (copy a PS1 VCD/CUE or PS2 ISO/ZSO into the OPL folder for SMB / USB modes,
 * running cue2pops first when needed). A fire-and-forget progress window - it
 * starts the work as soon as it shows and closes itself when done.
 *
 * {@code fileExtension} is the 3-char extension of the picked file in single
 * mode, {@code null} in batch mode (where {@code selectedFile} is a directory).
 */
public final class AddGameSmbScreen {

    private AddGameSmbScreen() {}

    public static void open(boolean batchMode, String fileExtension, File selectedFile) {
        String title = "PS1".equals(ps2gm.game.manager.PopsGameManager.getCurrentConsole())
                ? " Add PlayStation Game" : " Add PlayStation 2 Game";
        FxScreens.open("AddGameSmbScreen.fxml", title, false,
                (AddGameSmbController c) -> c.init(batchMode, fileExtension, selectedFile));
    }
}

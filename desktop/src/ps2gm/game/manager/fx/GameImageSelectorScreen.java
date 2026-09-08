package ps2gm.game.manager.fx;

import java.io.File;
import java.util.function.Consumer;
import ps2gm.game.manager.Console;
import ps2gm.game.manager.ImageChangedListener;
import ps2gm.game.manager.ImageSelectListener;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.GameImageSelectorScreenPS1} /
 * {@code GameImageSelectorScreenPS2} (browse the alternative server images for one
 * ART slot). Opened by {@link GameImageController}, which passes itself as both
 * {@code sel} and {@code chg} and receives the live controller through {@code ready}
 * so it can push the next downloaded file in with
 * {@link GameImageSelectorController#updateImage}.
 */
public final class GameImageSelectorScreen {

    private GameImageSelectorScreen() {}

    public static void open(ImageSelectListener sel, ImageChangedListener chg, String imageType, Console console,
                            File image, int numberOfImages, int currentImageNumber,
                            Consumer<GameImageSelectorController> ready) {
        FxScreens.open("GameImageSelectorScreen.fxml", " Images Selector", false,
                (GameImageSelectorController c) -> {
                    c.setup(sel, chg, imageType, console, image, numberOfImages, currentImageNumber);
                    if (ready != null) {
                        ready.accept(c);
                    }
                });
    }
}

package ps2gm.game.manager.fx;

import java.io.File;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import ps2gm.game.manager.ImageChangedListener;
import ps2gm.game.manager.ImageSelectListener;

/**
 * Controller for {@code GameImageSelectorScreen.fxml} - preview one server image,
 * step through the alternatives with the arrow buttons, "Use this image" to keep it.
 *
 * Opened by {@link GameImageController} (which passes itself as both listeners).
 * The arrows fire {@link ImageChangedListener#imageChanged}; the parent downloads
 * the next file and calls {@link #updateImage}. "Use this image" fires
 * {@link ImageSelectListener#imageSelected}. Closing the window without saving
 * deletes the previewed file and still fires {@code imageSelected}, so the parent
 * re-reads the ART directory and falls back to its placeholder - the same
 * behaviour as the Swing {@code GameImageSelectorScreenPS1/PS2}.
 */
public class GameImageSelectorController implements FxScreens.StageAware {

    private static final Map<String, String> TITLES = Map.of(
            "_COV", "Front Cover", "_COV2", "Rear Cover", "_ICO", "Disc Image", "_BG", "Background Image",
            "_SCR", "Screenshot 1", "_SCR2", "Screenshot 2", "_LAB", "Spine", "_LGO", "Logo");
    private static final Map<String, int[]> SIZES = Map.of(
            "_COV", new int[] {160, 210}, "_COV2", new int[] {160, 210}, "_ICO", new int[] {120, 120},
            "_BG", new int[] {375, 200}, "_SCR", new int[] {225, 170}, "_SCR2", new int[] {225, 170},
            "_LAB", new int[] {60, 260}, "_LGO", new int[] {260, 120});

    @FXML private TitledPane titledPane;
    @FXML private ImageView imageView;
    @FXML private TextField numberField;

    private Stage stage;
    private ImageSelectListener selectListener;
    private ImageChangedListener changedListener;
    private String imageType;
    private String console;
    private File image;
    private int numberOfImages;
    private int currentImageNumber = 1;
    private boolean imageSaved = false;

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(" Images Selector");
        stage.setOnHidden(e -> {
            if (!imageSaved) {
                if (image != null) {
                    image.delete();
                }
                if (selectListener != null && image != null) {
                    selectListener.imageSelected(imageType, image);
                }
            }
        });
    }

    void setup(ImageSelectListener sel, ImageChangedListener chg, String imageType, String console,
               File image, int numberOfImages, int currentImageNumber) {
        this.selectListener = sel;
        this.changedListener = chg;
        this.imageType = imageType;
        this.console = console;
        this.image = image;
        this.numberOfImages = numberOfImages;
        this.currentImageNumber = currentImageNumber;
        render();
    }

    /** Called by the parent after it has downloaded the next alternative. */
    void updateImage(File image) {
        this.image = image;
        this.imageSaved = false;
        render();
    }

    private int[] sizeFor(String type) {
        int[] size = SIZES.getOrDefault(type, new int[] {200, 200});
        if (("_COV".equals(type) || "_COV2".equals(type)) && "PS1".equals(console)) {
            return new int[] {160, 160};
        }
        return size;
    }

    private void render() {
        titledPane.setText(TITLES.getOrDefault(imageType, "Image"));
        numberField.setText(currentImageNumber + "/" + numberOfImages);
        int[] size = sizeFor(imageType);
        if (image != null && image.isFile()) {
            imageView.setFitWidth(size[0]);
            imageView.setFitHeight(size[1]);
            imageView.setImage(new Image(image.toURI().toString(), size[0], size[1], true, true));
        } else {
            imageView.setImage(null);
        }
    }

    @FXML
    private void onNext() {
        if (currentImageNumber < numberOfImages) {
            currentImageNumber++;
            numberField.setText(currentImageNumber + "/" + numberOfImages);
            changedListener.imageChanged(imageType, currentImageNumber);
        }
    }

    @FXML
    private void onPrev() {
        if (currentImageNumber > 1) {
            currentImageNumber--;
            numberField.setText(currentImageNumber + "/" + numberOfImages);
            changedListener.imageChanged(imageType, currentImageNumber);
        }
    }

    @FXML
    private void onSave() {
        // Matches the Swing version: keep the file, tell the parent, leave the window
        // open so the user can keep browsing or close it with the X.
        imageSaved = true;
        if (selectListener != null && image != null) {
            selectListener.imageSelected(imageType, image);
        }
    }
}

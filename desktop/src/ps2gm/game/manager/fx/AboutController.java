package ps2gm.game.manager.fx;

import java.awt.Desktop;
import java.net.URI;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;

/** Controller for {@code AboutScreen.fxml}. */
public class AboutController {

    @FXML private Label titleLabel;
    @FXML private Label compiledLabel;
    @FXML private Hyperlink contactLink;

    /** Populate the fields that depend on runtime values (title string, build date). */
    void setContent(String title, String compiledDate) {
        titleLabel.setText(title);
        compiledLabel.setText(compiledDate);
    }

    @FXML
    private void initialize() {
        contactLink.setOnAction(e -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                    Desktop.getDesktop().mail(new URI("mailto:" + contactLink.getText()));
                }
            } catch (Exception ignored) {
                // best effort - the address is still shown as text
            }
        });
    }
}

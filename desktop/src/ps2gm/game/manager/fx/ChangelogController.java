package ps2gm.game.manager.fx;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javafx.fxml.FXML;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code ChangelogScreen.fxml}. Renders {@code changelog.txt} into a
 * {@link TextFlow}: "Version ..." lines become bold italic orange headers, the lone
 * indented non-bullet line ("Initial Beta release.") is bold, everything else is
 * plain body text - matching the styling the old Swing JTextPane applied by hand.
 */
public class ChangelogController {

    @FXML private TextFlow changelogFlow;

    private static final Color HEADER_COLOR = Color.rgb(218, 145, 30); // == AppTheme.statusOrange()

    @FXML
    private void initialize() {
        try (InputStream in = ChangelogController.class.getResourceAsStream("changelog.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                changelogFlow.getChildren().add(styledLine(line));
            }
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug("Error generating the changelog!\n\n" + ex);
        }
    }

    private static Text styledLine(String line) {
        Text text = new Text(line + "\n");
        if (line.startsWith("Version ")) {
            text.setFill(HEADER_COLOR);
            text.setFont(Font.font("Ubuntu", FontWeight.BOLD, FontPosture.ITALIC, 12));
        } else if (!line.isBlank() && !line.stripLeading().startsWith("*")) {
            text.setFont(Font.font("Ubuntu", FontWeight.BOLD, 12));
        } else {
            text.setFont(Font.font("Ubuntu", 12));
        }
        return text;
    }
}

package ps2gm.game.manager.fx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ps2gm.game.manager.PopsGameManager;

/**
 * JavaFX replacement for the Swing {@code ps2gm.game.manager.AboutScreen}.
 *
 * First screen ported in the Swing -> JavaFX migration. Call {@link #show} from the
 * Swing EDT exactly where the old dialog was constructed; it boots the FX toolkit
 * (if needed) and opens the window on the FX thread. Not modal against the Swing
 * window - fine for an informational dialog.
 */
public final class AboutScreen {

    private AboutScreen() {}

    public static void show(String title, String compiledDate) {
        FxRuntime.ensureStarted();
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(AboutScreen.class.getResource("AboutScreen.fxml"));
                Parent root = loader.load();
                AboutController controller = loader.getController();
                controller.setContent(title, compiledDate);

                Stage stage = new Stage();
                stage.setTitle("About");
                stage.setResizable(false);
                stage.setScene(new Scene(root));
                stage.centerOnScreen();
                stage.show();
            } catch (Exception ex) {
                PopsGameManager.displayErrorMessageDebug("FX About screen failed to open: " + ex);
            }
        });
    }
}

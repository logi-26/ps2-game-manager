package ps2gm.game.manager.fx;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ps2gm.game.manager.PopsGameManager;

/**
 * JavaFX entry point for the app. {@code PopsGameManager.startApplication()} does
 * the pre-UI work (library check, settings load, game lists) and then calls
 * {@link Application#launch}, which lands here.
 *
 * Replaces the Swing {@code MainScreen} + FlatLaf setup. Theme comes from AtlantaFX
 * ({@link PrimerLight} / {@link PrimerDark}), toggled live by the Dark Mode menu.
 */
public final class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FxRuntime.markStarted();

        Application.setUserAgentStylesheet(PopsGameManager.getDarkMode()
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());

        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("MainScreen.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();

        primaryStage.setTitle(PopsGameManager.getFormTitle());
        primaryStage.setScene(new Scene(root));
        primaryStage.centerOnScreen();
        controller.init(primaryStage);
        primaryStage.show();
    }

    /** Called from {@code Main.main} after the headless setup is done. */
    public static void run(String[] args) {
        Application.launch(MainApp.class, args);
    }
}

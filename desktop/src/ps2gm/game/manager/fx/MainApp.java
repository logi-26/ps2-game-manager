package ps2gm.game.manager.fx;

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
 * via {@link Themes}; the choice is a name persisted in settings.xml and switched
 * live by the Theme menu.
 */
public final class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FxRuntime.markStarted();

        Themes.apply(PopsGameManager.getThemeName());

        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("MainScreen.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();

        primaryStage.setTitle(PopsGameManager.getFormTitle());
        Scene scene = new Scene(root);
        Themes.decorate(scene);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        controller.init(primaryStage);
        primaryStage.show();
    }

    /** Called from {@code Main.main} after the headless setup is done. */
    public static void run(String[] args) {
        Application.launch(MainApp.class, args);
    }
}

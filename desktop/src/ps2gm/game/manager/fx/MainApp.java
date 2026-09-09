package ps2gm.game.manager.fx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
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
        primaryStage.getIcons().addAll(AppIcons.ALL);
        Scene scene = new Scene(root);
        Themes.decorate(scene);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        // The Exit menu item already does this (java.awt.Desktop, used for opening
        // links, starts a non-daemon AWT thread that otherwise keeps the JVM alive)
        // - the OS close button needs the same handling, not just a window hide.
        primaryStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
        controller.init(primaryStage);
        primaryStage.show();
        WindowsDarkTitleBar.apply(primaryStage, Themes.isDark(PopsGameManager.getThemeName()));

        // AppBootstrap runs the startup game-list scan (and any Bad Game List /
        // renaming screens it opens for problems found) before this Application is
        // even launched, so those windows are already showing by now - being older,
        // they'd otherwise get buried the instant the primary stage takes focus here.
        for (Window w : Window.getWindows()) {
            if (w != primaryStage && w instanceof Stage other && other.isShowing()) {
                other.toFront();
                other.requestFocus();
            }
        }
    }

    /** Called from {@code Main.main} after the headless setup is done. */
    public static void run(String[] args) {
        Application.launch(MainApp.class, args);
    }
}

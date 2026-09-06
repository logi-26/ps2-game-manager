package ps2gm.game.manager;

public class Main {

    public static void main(String[] args) {

        // Check the command line parameters and then start the application
        if (args.length > 0){
            if (args[0].toUpperCase().equals("-DEBUG")){PopsGameManager.setDebugMode(true);} else {PopsGameManager.setDebugMode(false);}
        }

        // startApplication() does the headless setup (library check, settings, game
        // lists) and then hands off to the JavaFX Application (ps2gm.game.manager.fx.MainApp).
        PopsGameManager.startApplication();
    }
}

package oplpops.game.manager;

import com.formdev.flatlaf.FlatLightLaf;

public class Main {

    public static void main(String[] args) {

        // Modern flat look and feel (replaces the default cross-platform/system L&F).
        // Must run before any Swing component is constructed.
        FlatLightLaf.setup();

        // Check the command line parameters and then start the application
        if (args.length > 0){
            if (args[0].toUpperCase().equals("-DEBUG")){PopsGameManager.setDebugMode(true);} else {PopsGameManager.setDebugMode(false);}
        }

        PopsGameManager.startApplication();
    }
}

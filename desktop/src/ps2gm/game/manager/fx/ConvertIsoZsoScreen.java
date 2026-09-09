package ps2gm.game.manager.fx;

import ps2gm.game.manager.Game;

/** Opens {@code ConvertIsoZsoScreen.fxml} - see {@link ConvertIsoZsoController}. */
public final class ConvertIsoZsoScreen {

    private ConvertIsoZsoScreen() {}

    public static void open(Game game, boolean toZso) {
        FxScreens.open("ConvertIsoZsoScreen.fxml", (toZso ? " Convert to ZSO" : " Convert to ISO"), false,
                (ConvertIsoZsoController c) -> c.init(game, toZso));
    }
}

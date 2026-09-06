package ps2gm.game.manager;

// Listener for callbacks to update the main GUI. Public so the JavaFX MainController
// (in the .fx subpackage) can implement it and register via PopsGameManager.addListener().
public interface MyListener {void updateGameList(String gameID, int listIndex);}

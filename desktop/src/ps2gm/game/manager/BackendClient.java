package ps2gm.game.manager;

import java.io.File;
import java.util.List;

/**
 * Everything the desktop app needs from a shared-content backend: art,
 * configs, cheats, VMCs, and app/tool updates.
 */
public interface BackendClient {

    AppReleaseInfo getLatestAppRelease(String channel);
    boolean downloadAppUpdatePackage(String version, AppPlatformAsset asset, File destZip, AppUpdateProgress progress);
    String downloadChecksumText(String version, AppPlatformAsset asset);
    void getCue2PopsFromServer(String cue2popsPath, String cue2popsMD5);

    void getImageFromServer(Game selectedGame, String gameRegion, String gameID, String gameName, String coverType, String coverPath, int gameNumber, boolean batchMode);
    int getImagesAvailableOnServer(Game selectedGame, String gameRegion, String gameID, String gameName, String coverType, boolean batchMode);
    void getConfigFromServer(String gameRegion, String gameID, String gameName, boolean batchMode);
    void getVMCFromServer(String gameRegion, String vmcName, String gameName, String gameID);
    String getCheatFromServer(String gameRegion, String game);

    void getListFromServer(String listType, String console);

    List<GameSuggestion> suggestGameIds(Console console, String query);

    String sendMessageToServer(String serverMessage);
}

package ps2gm.game.manager;

/**
 * Everything the desktop app needs from a shared-content backend: art,
 * configs, cheats, VMCs, and app/tool updates.
 *
 * {@link MyApiClient} (the HTTP API + database) is the only implementation.
 * It replaced the original raw-TCP file server (formerly MyTCPClient,
 * removed once the API backend had had enough real-world runway). Every
 * call site takes this interface, never the concrete client, so a future
 * backend would still only need changing in one place -
 * {@link PopsGameManager#newBackendClient()}.
 *
 * User uploads and bad-file reports were removed as a feature (no
 * shareImageWithServer/shareConfigWithServer/shareVMCWithServer/submitReport
 * here); see MyApiClient's history for what that used to look like.
 */
public interface BackendClient {

    // ---- app / tool updates -------------------------------------------------
    void getJarFileFromServer(String newVersionNumber);
    void getCue2PopsFromServer(String cue2popsPath, String cue2popsMD5);

    // ---- downloading game content -------------------------------------------------
    void getImageFromServer(Game selectedGame, String gameRegion, String gameID, String gameName, String coverType, String coverPath, int gameNumber, boolean batchMode);
    int getImagesAvailableOnServer(Game selectedGame, String gameRegion, String gameID, String gameName, String coverType, boolean batchMode);
    void getConfigFromServer(String gameRegion, String gameID, String gameName, boolean batchMode);
    void getVMCFromServer(String gameRegion, String vmcName, String gameName, String gameID);
    String getCheatFromServer(String gameRegion, String game);

    // ---- catalogue lists (cached locally as lib/data/lists/<console>_Server*List.dat) ----
    void getListFromServer(String listType, String console);

    // ---- misc -------------------------------------------------
    /** "RESPOND" -&gt; "RESPONSE"/"NO_RESPONSE"; "VERSION" -&gt; "&lt;version&gt;,&lt;date&gt;"/"NO_RESPONSE". */
    String sendMessageToServer(String serverMessage);
}

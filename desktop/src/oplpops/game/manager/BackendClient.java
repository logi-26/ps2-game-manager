package oplpops.game.manager;

import java.io.IOException;

/**
 * Everything the desktop app needs from a shared-content backend: art,
 * configs, cheats, VMCs, app/tool updates and bad-file reports.
 *
 * Two implementations exist side by side during the migration:
 * {@link MyTCPClient} (the original raw-TCP file server) and
 * {@link MyApiClient} (the HTTP API + database). Pick one with
 * {@link PopsGameManager#newBackendClient()}, which reads the
 * {@code oplpops.backend} setting ("tcp" or "api"). Every call site takes
 * this interface, never a concrete client, so the switch is one place.
 */
public interface BackendClient {

    // ---- sharing (upload) -------------------------------------------------
    void shareImageWithServer(String console, String gameRegion, String gameID, String coverType, String imagePath) throws IOException;
    void shareConfigWithServer(String console, String gameRegion, String gameID, String configPath) throws IOException;
    void shareVMCWithServer(String console, String gameRegion, String gameID, String vmcPath, String vmcDescription) throws IOException;

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

    /** Reports a bad/missing file for a game so it can be reviewed. */
    void submitReport(String fileName, String console, String fileType, String gameRegion, String errorDescription);
}

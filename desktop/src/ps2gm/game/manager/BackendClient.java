package ps2gm.game.manager;

import java.io.File;
import java.util.List;

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
    /** {@code GET /app/latest?channel=...}, or null if unreachable/no release on that channel. */
    AppReleaseInfo getLatestAppRelease(String channel);
    /** Streams the update package for {@code version}/{@code asset} into {@code destZip}, reporting progress. False on any failure (destZip is not left partially written). */
    boolean downloadAppUpdatePackage(String version, AppPlatformAsset asset, File destZip, AppUpdateProgress progress);
    /** The raw text of the asset's .sha256 sidecar, or null if unreachable. */
    String downloadChecksumText(String version, AppPlatformAsset asset);
    void getCue2PopsFromServer(String cue2popsPath, String cue2popsMD5);

    // ---- downloading game content -------------------------------------------------
    void getImageFromServer(Game selectedGame, String gameRegion, String gameID, String gameName, String coverType, String coverPath, int gameNumber, boolean batchMode);
    int getImagesAvailableOnServer(Game selectedGame, String gameRegion, String gameID, String gameName, String coverType, boolean batchMode);
    void getConfigFromServer(String gameRegion, String gameID, String gameName, boolean batchMode);
    void getVMCFromServer(String gameRegion, String vmcName, String gameName, String gameID);
    String getCheatFromServer(String gameRegion, String game);

    // ---- catalogue lists (cached locally as lib/data/lists/<console>_Server*List.dat) ----
    void getListFromServer(String listType, String console);

    /** {@code GET /games/suggest?console=...&amp;q=...} - fuzzy name-match candidates, ranked, never just one. Empty list on any failure. */
    List<GameSuggestion> suggestGameIds(Console console, String query);

    // ---- misc -------------------------------------------------
    /** "RESPOND" -&gt; "RESPONSE"/"NO_RESPONSE"; "VERSION" -&gt; "&lt;version&gt;,&lt;date&gt;"/"NO_RESPONSE". */
    String sendMessageToServer(String serverMessage);
}

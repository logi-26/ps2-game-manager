package ps2gm.game.manager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Talks to the HTTP API ({@code api/}). See {@link BackendClient} for the
 * contract; the raw-TCP server it replaced is gone (retired once the API
 * backend had had enough real-world runway) but this kept the same local
 * file layout and caller-visible behaviour, just a different wire format.
 */
public class MyApiClient implements BackendClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            // The JDK client defaults to attempting an HTTP/2 (h2c) upgrade on
            // every request. uvicorn's dev server doesn't negotiate that cleanly
            // and silently drops the body on POSTs (GETs are unaffected, which is
            // why only uploads/reports showed this). Pin 1.1 to match what the
            // API actually speaks.
            .version(HttpClient.Version.HTTP_1_1)
            // Daemon threads: an HttpClient's internal executor must not be the
            // reason the JVM stays alive (Swing's EDT already keeps the real app
            // running; this only matters for short-lived callers/tools).
            .executor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            }))
            .build();

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(60);
    // App update packages are much larger than any other download this client does.
    private static final Duration UPDATE_DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

    public MyApiClient() {}

    // =====================================================================
    // App / tool updates
    // =====================================================================

    @Override
    public AppReleaseInfo getLatestAppRelease(String channel) {
        try {
            // getJson returns null for any non-200 response too, e.g. the 404 the API
            // gives when no release has been published for this channel yet - that's a
            // normal "nothing to report" outcome, not a connectivity failure, so it's
            // returned as null rather than thrown.
            Map<String, Object> json = getJson("/app/latest?channel=" + urlEncode(channel), DEFAULT_TIMEOUT);
            return json == null ? null : AppReleaseInfo.fromJson(json);
        } catch (IOException | InterruptedException ex) {
            throw new BackendUnreachableException("Could not reach the API for /app/latest", ex);
        }
    }

    @Override
    public boolean downloadAppUpdatePackage(String version, AppPlatformAsset asset, File destZip, AppUpdateProgress progress) {
        String path = "/app/releases/" + urlEncode(version) + "/download?platform=" + urlEncode(asset.platform());
        File partial = new File(destZip.getPath() + ".part");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(UPDATE_DOWNLOAD_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = HTTP.send(request, BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                return false;
            }

            long expectedBytes = asset.bytes();
            long copied = 0;
            try (InputStream in = resp.body();
                 OutputStream out = Files.newOutputStream(partial.toPath())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    copied += read;
                    if (progress != null && expectedBytes > 0) {
                        progress.setProgress(Math.min(1.0, copied / (double) expectedBytes));
                    }
                }
            }

            if (expectedBytes > 0 && copied != expectedBytes) {
                Files.deleteIfExists(partial.toPath());
                return false;
            }
            Files.move(partial.toPath(), destZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
            try {Files.deleteIfExists(partial.toPath());} catch (IOException ignored) {}
            return false;
        }
    }

    @Override
    public String downloadChecksumText(String version, AppPlatformAsset asset) {
        String path = "/app/releases/" + urlEncode(version) + "/checksum?platform=" + urlEncode(asset.platform());
        try {
            byte[] data = getBytes(path, DEFAULT_TIMEOUT);
            return data == null ? null : new String(data, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
            return null;
        }
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public void getCue2PopsFromServer(String cue2popsPath, String cue2popsMD5) {
        String osType = PopsGameManager.getOSType();
        String os = (osType != null && osType.toLowerCase().contains("win")) ? "windows" : "linux";
        try {
            byte[] data = getBytes("/tools/cue2pops?os=" + os, DOWNLOAD_TIMEOUT);
            if (data == null) return;
            Files.write(Paths.get(cue2popsPath), data);

            File downloaded = new File(cue2popsPath);
            if (downloaded.exists() && downloaded.isFile() && PopsGameManager.PerformQuickHashCheck(downloaded).equals(cue2popsMD5)) {
                File parent = downloaded.getParentFile();
                String finalPath = parent.getParent() + File.separator + downloaded.getName();
                new File(finalPath).delete();
                downloaded.renameTo(new File(finalPath));
                parent.delete();
            }
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    // =====================================================================
    // Downloading game content
    // =====================================================================

    @Override
    public void getImageFromServer(Game selectedGame, String gameRegion, String gameID, String gameName, String coverType, String coverPath, int gameNumber, boolean batchMode) {
        String kind = coverType.equals("_SCR2") ? "SCR" : coverType.substring(1).toUpperCase();

        // _ICO/_LAB/_LGO are stored as PNG (icon, spine label and logo all commonly need
        // transparency); everything else is JPG.
        String ext = isPngArt(coverPath) ? ".png" : ".jpg";
        String localImagePath = null;
        if (PopsGameManager.getCurrentConsole() == Console.PS1) {
            localImagePath = PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + PopsGameManager.getFilePrefix() + gameName + "-" + gameID + ".ELF" + coverPath + ext;
        } else if (PopsGameManager.getCurrentConsole() == Console.PS2) {
            localImagePath = PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + gameID + coverPath + ext;
        }

        try {
            HttpResponse<byte[]> resp = get("/games/" + gameID + "/artwork/" + kind + "/" + gameNumber, BodyHandlers.ofByteArray(), DEFAULT_TIMEOUT);
            if (resp.statusCode() == 200 && localImagePath != null && resp.body().length > 0) {
                Files.write(Paths.get(localImagePath), resp.body());
            }
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    // Art suffixes this app stores as PNG rather than JPG (icon, spine label, logo - all
    // commonly carry transparency). Matches the extension the image screens look for.
    static boolean isPngArt(String suffix) {
        return "_ICO".equals(suffix) || "_LAB".equals(suffix) || "_LGO".equals(suffix);
    }

    @Override
    public int getImagesAvailableOnServer(Game selectedGame, String gameRegion, String gameID, String gameName, String coverType, boolean batchMode) {
        String kind = coverType.equals("_SCR2") ? "SCR" : coverType.substring(1).toUpperCase();
        try {
            Map<String, Object> obj = getJson("/games/" + gameID + "/artwork/" + kind, DEFAULT_TIMEOUT);
            return obj == null ? 0 : MiniJson.intVal(obj, "count", 0);
        } catch (IOException | InterruptedException ex) {
            if (!batchMode) PopsGameManager.showWarningDialog("The server does not seem to be responding at the moment, please try again later.", " Server Not Responding!");
            return 0;
        }
    }

    @Override
    public void getConfigFromServer(String gameRegion, String gameID, String gameName, boolean batchMode) {
        String localConfigPath = PopsGameManager.getCurrentConsole() == Console.PS1
                ? PopsGameManager.getOPLFolder() + File.separator + "CFG" + File.separator + PopsGameManager.getFilePrefix() + gameName + "-" + gameID + ".ELF.cfg"
                : PopsGameManager.getOPLFolder() + File.separator + "CFG" + File.separator + gameID + ".cfg";

        try {
            HttpResponse<byte[]> resp = get("/games/" + gameID + "/config", BodyHandlers.ofByteArray(), DEFAULT_TIMEOUT);
            if (resp.statusCode() == 200) {
                Files.write(Paths.get(localConfigPath), resp.body());
            } else if (!batchMode) {
                PopsGameManager.showWarningDialog("There is no config file available in the database for this game.", " No CFG Available!");
                return;
            } else {
                return;
            }
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
            return;
        }

        // Bump the config format version and stamp the user's game name in, same as the TCP client did
        if (new File(localConfigPath).exists()) {
            try {
                List<String> lines = new ArrayList<>(Files.readAllLines(Paths.get(localConfigPath), StandardCharsets.UTF_8));
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).equals("CfgVersion=3")) lines.set(i, "CfgVersion=5");
                    if (lines.get(i).length() >= 5 && lines.get(i).substring(0, 5).equals("Title")) lines.set(i, "Title=" + gameName);
                }
                Files.write(Paths.get(localConfigPath), lines, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
        }
    }

    @Override
    public void getVMCFromServer(String gameRegion, String vmcName, String gameName, String gameID) {
        String localVMCPath = null;
        if (PopsGameManager.getCurrentConsole() == Console.PS2) {
            localVMCPath = PopsGameManager.getOPLFolder() + File.separator + "VMC" + File.separator + vmcName + ".bin";
        } else if (PopsGameManager.getCurrentConsole() == Console.PS1) {
            localVMCPath = PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + gameName + "-" + gameID + File.separator + vmcName + ".VMC";
            File gameFolder = new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + gameName + "-" + gameID + File.separator);
            if (!gameFolder.exists()) gameFolder.mkdir();
        }

        try {
            List<Object> vmcs = getJsonArray("/games/" + gameID + "/vmc", DEFAULT_TIMEOUT);
            Integer vmcId = null;
            for (Object o : vmcs) {
                Map<String, Object> entry = MiniJson.asObject(o);
                if (vmcName.equals(MiniJson.str(entry, "label"))) {
                    vmcId = MiniJson.intVal(entry, "id", -1);
                    break;
                }
            }
            if (vmcId == null || vmcId < 0 || localVMCPath == null) return;

            HttpResponse<byte[]> resp = get("/games/" + gameID + "/vmc/" + vmcId, BodyHandlers.ofByteArray(), DEFAULT_TIMEOUT);
            if (resp.statusCode() == 200 && resp.body().length > 0) {
                Files.write(Paths.get(localVMCPath), resp.body());
                PopsGameManager.showInfoDialog("The VMC file has been successfully downloaded.", " VMC File Downloaded");
            }
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    @Override
    public String getCheatFromServer(String gameRegion, String game) {
        try {
            HttpResponse<byte[]> resp = get("/games/" + game + "/cheats", BodyHandlers.ofByteArray(), DEFAULT_TIMEOUT);
            return resp.statusCode() == 200 ? new String(resp.body(), StandardCharsets.UTF_8) : null;
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
            return null;
        }
    }

    // =====================================================================
    // Catalogue lists
    // =====================================================================

    @Override
    public void getListFromServer(String listType, String console) {
        String endpoint;
        String localPath;
        switch (listType) {
            case "CHEAT":
                endpoint = "/cheats";
                localPath = "lib" + File.separator + "data" + File.separator + "lists" + File.separator + console + "_ServerCheatList.dat";
                break;
            case "ART":
                endpoint = "/artwork";
                localPath = "lib" + File.separator + "data" + File.separator + "lists" + File.separator + console + "_ServerArtList.dat";
                break;
            case "CONFIG":
                endpoint = "/configs";
                localPath = "lib" + File.separator + "data" + File.separator + "lists" + File.separator + console + "_ServerConfigList.dat";
                break;
            case "VMC":
                endpoint = "/vmc";
                localPath = "lib" + File.separator + "data" + File.separator + "lists" + File.separator + console + "_ServerVMCList.dat";
                break;
            default:
                return;
        }
        localPath = PopsGameManager.getCurrentDirectory() + File.separator + localPath;

        try {
            List<String> outLines = new ArrayList<>();
            if (listType.equals("VMC")) {
                for (Map<String, Object> entry : fetchAllPages(endpoint, console)) {
                    String label = MiniJson.str(entry, "label");
                    String description = MiniJson.str(entry, "description");
                    // "<label> <description>" - the label has no spaces, so GameVMCScreen.readVMCList
                    // splits on the first space (no fixed-width assumption).
                    outLines.add(label + " " + (description == null ? "" : description));
                }
            } else {
                LinkedHashSet<String> gameIds = new LinkedHashSet<>();
                for (Map<String, Object> entry : fetchAllPages(endpoint, console)) {
                    gameIds.add(MiniJson.str(entry, "game_id"));
                }
                outLines.addAll(gameIds);
            }
            Path outPath = Paths.get(localPath);
            if (outPath.getParent() != null) {
                Files.createDirectories(outPath.getParent());
            }
            Files.write(outPath, outLines, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    @Override
    public List<GameSuggestion> suggestGameIds(Console console, String query) {
        try {
            String path = "/games/suggest?console=" + urlEncode(console.name()) + "&q=" + urlEncode(query);
            List<GameSuggestion> suggestions = new ArrayList<>();
            for (Object item : getJsonArray(path, DEFAULT_TIMEOUT)) {
                suggestions.add(GameSuggestion.fromJson(MiniJson.asObject(item)));
            }
            return suggestions;
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
            return new ArrayList<>();
        }
    }

    /** Pages through a list endpoint (?console=&amp;limit=&amp;offset=) collecting every item. */
    private List<Map<String, Object>> fetchAllPages(String endpoint, String console) throws IOException, InterruptedException {
        List<Map<String, Object>> all = new ArrayList<>();
        int limit = 1000, offset = 0, total = Integer.MAX_VALUE;
        while (offset < total) {
            Map<String, Object> page = getJson(endpoint + "?console=" + console + "&limit=" + limit + "&offset=" + offset, DOWNLOAD_TIMEOUT);
            if (page == null) break;
            total = MiniJson.intVal(page, "total", 0);
            List<Object> items = MiniJson.list(page, "items");
            if (items.isEmpty()) break;
            for (Object o : items) all.add(MiniJson.asObject(o));
            offset += limit;
        }
        return all;
    }

    // =====================================================================
    // Misc
    // =====================================================================

    @Override
    public String sendMessageToServer(String serverMessage) {
        try {
            switch (serverMessage) {
                case "RESPOND": {
                    HttpResponse<byte[]> resp = get("/health", BodyHandlers.ofByteArray(), Duration.ofSeconds(3));
                    return resp.statusCode() == 200 ? "RESPONSE" : "NO_RESPONSE";
                }
                case "VERSION": {
                    Map<String, Object> rel = getJson("/app/latest", Duration.ofSeconds(5));
                    if (rel == null) return "NO_RESPONSE";
                    String version = MiniJson.str(rel, "version");
                    String published = MiniJson.str(rel, "published_at");
                    return version + "," + (published == null ? "" : published);
                }
                default:
                    return "NO_RESPONSE";
            }
        } catch (IOException | InterruptedException ex) {
            return "NO_RESPONSE";
        }
    }

    // =====================================================================
    // HTTP plumbing
    // =====================================================================

    private static String baseUrl() { return PopsGameManager.getApiBaseUrl(); }

    private HttpResponse<byte[]> get(String pathAndQuery, HttpResponse.BodyHandler<byte[]> handler, Duration timeout) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + pathAndQuery))
                .timeout(timeout)
                .GET()
                .build();
        return HTTP.send(request, handler);
    }

    /** GET that returns the body bytes on 200, or null on any other status (mirrors the old "no such file" sentinels as absence). */
    private byte[] getBytes(String pathAndQuery, Duration timeout) throws IOException, InterruptedException {
        HttpResponse<byte[]> resp = get(pathAndQuery, BodyHandlers.ofByteArray(), timeout);
        return resp.statusCode() == 200 ? resp.body() : null;
    }

    private Map<String, Object> getJson(String pathAndQuery, Duration timeout) throws IOException, InterruptedException {
        HttpResponse<byte[]> resp = get(pathAndQuery, BodyHandlers.ofByteArray(), timeout);
        if (resp.statusCode() != 200) return null;
        return MiniJson.parseObject(new String(resp.body(), StandardCharsets.UTF_8));
    }

    /** For endpoints whose top-level JSON is a bare array (e.g. GET /games/{id}/vmc), not a {items:[...]} page. */
    @SuppressWarnings("unchecked")
    private List<Object> getJsonArray(String pathAndQuery, Duration timeout) throws IOException, InterruptedException {
        HttpResponse<byte[]> resp = get(pathAndQuery, BodyHandlers.ofByteArray(), timeout);
        if (resp.statusCode() != 200) return new ArrayList<>();
        Object parsed = MiniJson.parse(new String(resp.body(), StandardCharsets.UTF_8));
        return (parsed instanceof List) ? (List<Object>) parsed : new ArrayList<>();
    }

}

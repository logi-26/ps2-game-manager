package oplpops.game.manager;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import javax.swing.JOptionPane;

/**
 * Talks to the HTTP API ({@code api/}) instead of the raw-TCP server. See
 * {@link BackendClient} for the contract and {@link MyTCPClient} for the
 * client this replaces - method-for-method, same local file layout, same
 * caller-visible behaviour, different wire format.
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

    public MyApiClient() {}

    // =====================================================================
    // Sharing (upload)
    // =====================================================================

    @Override
    public void shareImageWithServer(String console, String gameRegion, String gameID, String coverType, String imagePath) throws IOException {
        File file = new File(imagePath);
        if (!file.exists() || file.isDirectory()) return;

        String kind = coverType.startsWith("_") ? coverType.substring(1) : coverType;
        kind = kind.toUpperCase();
        int variant = 0;
        if (kind.equals("SCR2")) { kind = "SCR"; variant = 1; }

        byte[] bytes = Files.readAllBytes(file.toPath());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("kind", kind);
        fields.put("variant", Integer.toString(variant));
        fields.put("submitted_by", PopsGameManager.getMacAddress());

        try {
            postMultipart("/games/" + gameID + "/artwork", fields, "file", file.getName(), bytes, DEFAULT_TIMEOUT);
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    @Override
    public void shareConfigWithServer(String console, String gameRegion, String gameID, String configPath) throws IOException {
        File file = new File(configPath);
        if (!file.exists() || file.isDirectory()) return;

        byte[] bytes = Files.readAllBytes(file.toPath());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("submitted_by", PopsGameManager.getMacAddress());

        try {
            postMultipart("/games/" + gameID + "/config", fields, "file", file.getName(), bytes, DEFAULT_TIMEOUT);
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    @Override
    public void shareVMCWithServer(String console, String gameRegion, String gameID, String vmcPath, String vmcDescription) throws IOException {
        File file = new File(vmcPath);
        if (!file.exists() || file.isDirectory()) return;

        byte[] bytes = Files.readAllBytes(file.toPath());
        Map<String, String> fields = new LinkedHashMap<>();
        // labels are unique per (game, source) - suffix with the sender's MAC so two
        // different contributors sharing the same game don't collide on upload
        fields.put("label", gameID + "_" + PopsGameManager.getMacAddress());
        if (vmcDescription != null) fields.put("description", vmcDescription);
        fields.put("submitted_by", PopsGameManager.getMacAddress());

        try {
            postMultipart("/games/" + gameID + "/vmc", fields, "file", file.getName(), bytes, DEFAULT_TIMEOUT);
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    // =====================================================================
    // App / tool updates
    // =====================================================================

    @Override
    public void getJarFileFromServer(String newVersionNumber) {
        try {
            byte[] data = getBytes("/app/releases/" + newVersionNumber + "/download", DOWNLOAD_TIMEOUT);
            if (data == null) return;

            String jarPath = PopsGameManager.getCurrentDirectory() + File.separator + "OPLPOPS-Manager_" + newVersionNumber + ".jar";
            Files.write(Paths.get(jarPath), data);
            checkJarFile(jarPath, data.length);
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    private void checkJarFile(String filePath, int expectedLength) {
        File jarFile = new File(filePath);
        if (jarFile.exists() && jarFile.length() == expectedLength) {
            long jarFileSize = jarFile.length();
            int dialogResult = JOptionPane.showConfirmDialog(null, "The update was successfully downloaded!  (size = " + PopsGameManager.bytesToHuman(jarFileSize) + "). \n\nDo you want to launch the new version now?", " Update Downloaded", JOptionPane.YES_NO_OPTION);
            if (dialogResult == JOptionPane.YES_OPTION) {
                int endIndex = filePath.lastIndexOf(File.separator);
                if (endIndex != -1) {
                    String jarPath = filePath.substring(0, endIndex + 1);
                    String jarName = filePath.substring(endIndex + 1);
                    ProcessBuilder pb = new ProcessBuilder("java", "-jar", jarPath + jarName);
                    pb.directory(new File(PopsGameManager.getCurrentDirectory()));
                    try {
                        pb.start();
                        System.exit(0);
                    } catch (IOException ex) {
                        PopsGameManager.displayErrorMessageDebug(ex.toString());
                    }
                }
            }
        } else {
            JOptionPane.showMessageDialog(null, "There was a problem downloading the update, please try again later!.", " Update Failed!", JOptionPane.ERROR_MESSAGE);
        }
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

        String localImagePath = null;
        if (PopsGameManager.getCurrentConsole().equals("PS1")) {
            localImagePath = coverPath.equals("_ICO")
                    ? PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + PopsGameManager.getFilePrefix() + gameName + "-" + gameID + ".ELF" + coverPath + ".png"
                    : PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + PopsGameManager.getFilePrefix() + gameName + "-" + gameID + ".ELF" + coverPath + ".jpg";
        } else if (PopsGameManager.getCurrentConsole().equals("PS2")) {
            localImagePath = coverPath.equals("_ICO")
                    ? PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + gameID + coverPath + ".png"
                    : PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator + gameID + coverPath + ".jpg";
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

    @Override
    public int getImagesAvailableOnServer(Game selectedGame, String gameRegion, String gameID, String gameName, String coverType, boolean batchMode) {
        String kind = coverType.equals("_SCR2") ? "SCR" : coverType.substring(1).toUpperCase();
        try {
            Map<String, Object> obj = getJson("/games/" + gameID + "/artwork/" + kind, DEFAULT_TIMEOUT);
            return obj == null ? 0 : MiniJson.intVal(obj, "count", 0);
        } catch (IOException | InterruptedException ex) {
            if (!batchMode) JOptionPane.showMessageDialog(null, "The server does not seem to be responding at the moment, please try again later.", " Server Not Responding!", JOptionPane.WARNING_MESSAGE);
            return 0;
        }
    }

    @Override
    public void getConfigFromServer(String gameRegion, String gameID, String gameName, boolean batchMode) {
        String localConfigPath = PopsGameManager.getCurrentConsole().equals("PS1")
                ? PopsGameManager.getOPLFolder() + File.separator + "CFG" + File.separator + PopsGameManager.getFilePrefix() + gameName + "-" + gameID + ".ELF.cfg"
                : PopsGameManager.getOPLFolder() + File.separator + "CFG" + File.separator + gameID + ".cfg";

        try {
            HttpResponse<byte[]> resp = get("/games/" + gameID + "/config", BodyHandlers.ofByteArray(), DEFAULT_TIMEOUT);
            if (resp.statusCode() == 200) {
                Files.write(Paths.get(localConfigPath), resp.body());
            } else if (!batchMode) {
                JOptionPane.showMessageDialog(null, "There is no config file available in the database for this game.", " No CFG Available!", JOptionPane.WARNING_MESSAGE);
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
        if (PopsGameManager.getCurrentConsole().equals("PS2")) {
            localVMCPath = PopsGameManager.getOPLFolder() + File.separator + "VMC" + File.separator + vmcName + ".bin";
        } else if (PopsGameManager.getCurrentConsole().equals("PS1")) {
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
                JOptionPane.showMessageDialog(null, "The VMC file has been successfully downloaded.", " VMC File Downloaded", JOptionPane.PLAIN_MESSAGE);
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
                    String padded = (label + "                     ").substring(0, 21); // fixed-width, matches GameVMCScreen.readVMCList
                    outLines.add(padded + " " + (description == null ? "" : description));
                }
            } else {
                LinkedHashSet<String> gameIds = new LinkedHashSet<>();
                for (Map<String, Object> entry : fetchAllPages(endpoint, console)) {
                    gameIds.add(MiniJson.str(entry, "game_id"));
                }
                outLines.addAll(gameIds);
            }
            Files.write(Paths.get(localPath), outLines, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
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

    @Override
    public void submitReport(String fileName, String console, String fileType, String gameRegion, String errorDescription) {
        String json = "{"
                + "\"game_id\":" + jsonString(fileName) + ","
                + "\"file_type\":" + jsonString(fileType) + ","
                + "\"reason\":" + jsonString("[" + console + "/" + gameRegion + "] " + errorDescription) + ","
                + "\"reporter\":" + jsonString(PopsGameManager.getMacAddress())
                + "}";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + "/reports"))
                    .timeout(DEFAULT_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            addApiKey(builder);
            HttpResponse<String> resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                PopsGameManager.displayErrorMessageDebug("submitReport failed: HTTP " + resp.statusCode() + " " + resp.body());
            }
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    // =====================================================================
    // HTTP plumbing
    // =====================================================================

    private static String baseUrl() { return PopsGameManager.getApiBaseUrl(); }

    private static void addApiKey(HttpRequest.Builder builder) {
        String key = PopsGameManager.getApiWriteKey();
        if (key != null && !key.trim().isEmpty()) builder.header("X-API-Key", key.trim());
    }

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

    private void postMultipart(String path, Map<String, String> fields, String fileFieldName, String fileName, byte[] fileBytes, Duration timeout) throws IOException, InterruptedException {
        String boundary = "----oplpops-" + System.nanoTime();
        BodyPublisher body = multipartBody(boundary, fields, fileFieldName, fileName, fileBytes);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(timeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(body);
        addApiKey(builder);
        HttpResponse<String> resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            PopsGameManager.displayErrorMessageDebug("upload to " + path + " failed: HTTP " + resp.statusCode() + " " + resp.body());
        }
    }

    private static BodyPublisher multipartBody(String boundary, Map<String, String> fields, String fileFieldName, String fileName, byte[] fileBytes) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        String crlf = "\r\n";
        for (Map.Entry<String, String> field : fields.entrySet()) {
            out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + field.getKey() + "\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write((field.getValue() + crlf).getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + fileFieldName + "\"; filename=\"" + fileName + "\"" + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: application/octet-stream" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return BodyPublishers.ofByteArray(out.toByteArray());
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}

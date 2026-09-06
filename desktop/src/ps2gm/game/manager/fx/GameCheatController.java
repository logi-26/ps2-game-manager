package ps2gm.game.manager.fx;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ps2gm.game.manager.BackendClient;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code GameCheatScreen.fxml} - the cheat-file editor. Left pane is
 * the editable local cheat file ({@code POPS/<name>-<id>/CHEATS.TXT} for PS1,
 * {@code CHT/<id>.cht} for PS2); right pane lists the server's cheats for the game,
 * colour-coded, with "&#171; Add" to copy the selected lines into the editor.
 *
 * JavaFX replacement for the Swing {@code GameCheatScreen}. The server fetch
 * (cheat-list index + per-game cheats + widescreen resource scan) runs on a daemon
 * thread. The Swing screen's drag-select + right-click-append gesture on a
 * {@code JTextPane} becomes a multi-select {@code ListView} + an Add button - the
 * {@code $}-prefix transformation for PS1 codes is unchanged.
 */
public class GameCheatController implements FxScreens.StageAware {

    // == AppTheme.statusBlue()/statusOrangeAlt()/statusGreen()/statusRed() (package-private there).
    private static final Color COLOUR_IGR_TITLE   = Color.rgb(38, 120, 190);
    private static final Color COLOUR_CHEAT_TITLE = Color.rgb(226, 149, 15);
    private static final Color COLOUR_WIDESCREEN  = Color.rgb(55, 170, 20);
    private static final Color COLOUR_ENABLE      = Color.rgb(190, 35, 25);

    private static final String[] IGR_ARRAY = {
        "IGR - In Game Reset Codes",
        "NOIGR - (L1+L2+R1+R2+Select+Start) - Disable IGR Menu",
        "IGR0 - (L1+L2+R1+R2+X+DPad-Down) - IGR Menu",
        "IGR1 - (Select+Start) - IGR Menu",
        "IGR2 - (L1+L2+R1+R2+Select+Start) - IGR Menu",
        "IGR3 - (L1+L2+R1+R2+X+DPad-Down) - No IGR Menu",
        "IGR4 - (Select+Start) - No IGR Menu",
        "IGR5 - (L1+L2+R1+R2+Select+Start) - No IGR Menu"
    };

    @FXML private TextField gameNumberField, gameNameField;
    @FXML private TextArea cheatContentArea;
    @FXML private ListView<String> serverCheatList;
    @FXML private Button prevButton, nextButton, saveButton, undoButton, deleteButton, addButton;

    private Stage stage;
    private final List<Game> gameList = new ArrayList<>();
    private int currentListIndex;
    private List<String> serverCheatIndex = new ArrayList<>();
    private List<String> displayedLines = new ArrayList<>();

    @FXML
    private void initialize() {
        serverCheatList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        serverCheatList.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                Color c = styleFor(item);
                if (c != null) {
                    setStyle("-fx-text-fill: " + toRgbCss(c) + "; -fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(" Manage Game Cheats");
        stage.setOnHidden(e -> {
            compareCheatFile();
            PopsGameManager.callbackToUpdateGUIGameList(null, currentListIndex);
        });
    }

    /** Called from the façade before the window is shown. */
    void init(int gameIndex) {
        currentListIndex = gameIndex;
        List<Game> src = "PS1".equals(PopsGameManager.getCurrentConsole())
                ? GameListManager.getGameListPS1() : GameListManager.getGameListPS2();
        if (src != null) { gameList.addAll(src); }
        refreshGameHeader();
        loadCheatContent();
        fetchServerCheats();
    }

    // --- navigation --------------------------------------------------

    @FXML
    private void onPrev() {
        compareCheatFile();
        if (currentListIndex > 0) {
            currentListIndex--;
            refreshGameHeader();
            loadCheatContent();
            fetchServerCheats();
        }
    }

    @FXML
    private void onNext() {
        compareCheatFile();
        if (currentListIndex < gameList.size() - 1) {
            currentListIndex++;
            refreshGameHeader();
            loadCheatContent();
            fetchServerCheats();
        }
    }

    private void refreshGameHeader() {
        Game g = gameList.get(currentListIndex);
        gameNameField.setText(g.getGameName() + "  :  " + g.getGameID());
        gameNumberField.setText("[" + (currentListIndex + 1) + "/" + gameList.size() + "]");
    }

    // --- local cheat file -----------------------------------------

    private File cheatFile() {
        Game g = gameList.get(currentListIndex);
        if ("PS1".equals(PopsGameManager.getCurrentConsole())) {
            return new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator
                    + g.getGameName() + "-" + g.getGameID() + File.separator + "CHEATS.TXT");
        }
        return new File(PopsGameManager.getOPLFolder() + File.separator + "CHT" + File.separator + g.getGameID() + ".cht");
    }

    private String ps2Placeholder() {
        Game g = gameList.get(currentListIndex);
        return "\"" + g.getGameName() + " /ID " + g.getGameID() + "\"\n" + "Mastercode\n";
    }

    private void loadCheatContent() {
        cheatContentArea.setText("");
        File file = cheatFile();
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());

        if (file.isFile()) {
            List<String> lines = new ArrayList<>();
            try (BufferedReader in = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = in.readLine()) != null) { lines.add(line); }
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
            if (!lines.isEmpty()) {
                cheatContentArea.setText(String.join("\n", lines) + "\n");
            }
        } else if (!ps1) {
            cheatContentArea.setText(ps2Placeholder());
        }
    }

    private void saveCheatFile() {
        File file = cheatFile();
        try {
            file.getParentFile().mkdirs();
            if (!file.exists()) { file.createNewFile(); }
            try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
                out.write(cheatContentArea.getText());
                out.flush();
            }
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    // Compare the editor against the file; prompt to save / create if it differs.
    private void compareCheatFile() {
        File file = cheatFile();
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());

        if (file.isFile()) {
            List<String> onDisk = new ArrayList<>();
            try (Scanner scanner = new Scanner(file)) {
                while (scanner.hasNextLine()) { onDisk.add(scanner.nextLine()); }
            } catch (Exception ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
            List<String> current = new ArrayList<>(Arrays.asList(cheatContentArea.getText().split("\\n")));
            if (!onDisk.equals(current)) {
                if (confirm("The cheat data has changed, do you want to save the changes?", " Cheat Data Changed")) {
                    saveCheatFile();
                }
            }
        } else {
            String text = cheatContentArea.getText();
            boolean changed = ps1 ? !text.isEmpty() : !text.equals(ps2Placeholder());
            if (changed && confirm("The cheat data has changed, do you want to create the cheat file?", " Cheat Data Changed")) {
                saveCheatFile();
            }
        }
    }

    @FXML
    private void onSave() {
        if (!cheatContentArea.getText().isEmpty()) { saveCheatFile(); }
    }

    @FXML
    private void onUndo() {
        loadCheatContent();
    }

    @FXML
    private void onDelete() {
        File file = cheatFile();
        if (file.isFile()) {
            if (confirm("Are you sure that you want to delete this cheat file?", " Delete Cheat File!")) {
                file.delete();
            }
            cheatContentArea.setText("PS1".equals(PopsGameManager.getCurrentConsole()) ? "" : ps2Placeholder());
        }
    }

    // --- server cheats ------------------------------------------

    private void fetchServerCheats() {
        serverCheatList.getItems().clear();
        Game game = gameList.get(currentListIndex);
        String console = PopsGameManager.getCurrentConsole();
        new Thread(() -> {
            List<String> index = downloadServerCheatIndex(console);
            List<String> lines = downloadCheatsForGame(game, console, index);
            Platform.runLater(() -> {
                serverCheatIndex = index;
                displayedLines = lines;
                serverCheatList.getItems().setAll(lines);
            });
        }, "fx-game-cheat-server").start();
    }

    private List<String> downloadServerCheatIndex(String console) {
        List<String> out = new ArrayList<>();
        BackendClient api = PopsGameManager.newBackendClient();
        api.getListFromServer("CHEAT", console);
        File listFile = new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator
                + "data" + File.separator + "lists" + File.separator + console + "_ServerCheatList.dat");
        if (listFile.isFile()) {
            try (Stream<String> lines = Files.lines(listFile.toPath(), Charset.defaultCharset())) {
                lines.forEachOrdered(out::add);
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug("Error reading the cheat list file!\n\n" + ex.toString());
            }
        }
        return out;
    }

    // Faithful port of getCheatsFromServer()'s data assembly (without the JTextPane styling -
    // styling moves to the ListView cell factory via styleFor()).
    private List<String> downloadCheatsForGame(Game game, String console, List<String> index) {
        List<String> out = new ArrayList<>();
        boolean ps1 = "PS1".equals(console);

        if (index.isEmpty() || !index.contains(game.getGameID())) {
            if (ps1) { out.addAll(Arrays.asList(IGR_ARRAY)); }
            return out;
        }

        BackendClient api = PopsGameManager.newBackendClient();
        String region = PopsGameManager.determineGameRegion(game.getGameID().split("_")[0]);
        String cheat = api.getCheatFromServer(region, game.getGameID());
        if (cheat == null || cheat.equals("NO_CHEAT")) {
            if (ps1) { out.addAll(Arrays.asList(IGR_ARRAY)); }
            return out;
        }

        List<String> serverLines = new ArrayList<>();
        for (String line : cheat.split("\\r?\\n")) { if (line.length() > 3) { serverLines.add(line); } }

        if (ps1) {
            out.addAll(Arrays.asList(IGR_ARRAY));
            appendWidescreen(out, game.getGameID(), "/ps2gm/game/manager/WidescreenListPS1.txt");
        } else {
            appendWidescreen(out, game.getGameID(), "/ps2gm/game/manager/WidescreenListPS2.txt");
        }
        out.addAll(serverLines);

        if (!ps1) {
            out.removeIf(l -> (l.length() > 3 && l.substring(0, 4).equalsIgnoreCase("Key:"))
                    || (l.length() > 6 && (l.startsWith("Disc 1:") || l.startsWith("Disc 2:"))));
        }
        return out;
    }

    private void appendWidescreen(List<String> out, String gameID, String resource) {
        InputStream in = GameListManager.class.getResourceAsStream(resource);
        if (in == null) { return; }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > 1 && line.substring(1).equals(gameID)) {
                    out.add("Widescreen 16:9");
                    boolean end = false;
                    while ((line = reader.readLine()) != null && !end) {
                        if (!line.startsWith("*")) { out.add(line); } else { end = true; }
                    }
                }
            }
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    // Colour classification, mirroring getCheatsFromServer()'s doc.insertString branches.
    private Color styleFor(String line) {
        if (line == null) { return null; }
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());
        int first = displayedLines.indexOf(line);
        if (ps1 && first == 0 && line.equals("IGR - In Game Reset Codes")) { return COLOUR_IGR_TITLE; }
        if (line.equals("Widescreen 16:9")) { return COLOUR_WIDESCREEN; }
        if (line.equals("Enable Code") || line.equals("POPS Enable Code")) { return COLOUR_ENABLE; }
        if (checkIfCheatTitle(line)) { return COLOUR_CHEAT_TITLE; }
        return null;
    }

    // --- add-to-editor -----------------------------------------

    @FXML
    private void onAddSelected() {
        List<String> selected = new ArrayList<>(serverCheatList.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) { return; }
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());
        StringBuilder sb = new StringBuilder(cheatContentArea.getText());
        for (String cheat : selected) {
            sb.append(transformForEditor(cheat, ps1)).append("\n");
        }
        cheatContentArea.setText(sb.toString());
    }

    // The $-prefix logic from the Swing left-click handler.
    private String transformForEditor(String cheat, boolean ps1) {
        if (!ps1) { return cheat; }
        boolean igrCode = false;
        for (String igr : IGR_ARRAY) {
            if (cheat.equals(igr) && !cheat.equals("IGR - In Game Reset Codes")) { igrCode = true; }
        }
        if (igrCode) {
            return cheat.startsWith("N") ? "$" + cheat.substring(0, 5) : "$" + cheat.substring(0, 4);
        }
        if (cheat.equals("IGR - In Game Reset Codes")) { return cheat; }
        if (checkIfCheatTitle(cheat)) { return cheat; }
        return "$" + cheat;
    }

    // --- helpers (verbatim ports) ------------------------------

    private boolean checkIfCheatTitle(String text) {
        boolean cheatTitle = false;
        int numOfChars = 0;
        int numOfNumbers = 0;
        for (char character : text.toCharArray()) {
            if (Character.isDigit(character)) { numOfNumbers += 1; } else { numOfChars += 1; }
        }
        if (numOfChars > numOfNumbers && !text.isEmpty() && Character.isLetter(text.charAt(0))) { cheatTitle = true; }
        else if (text.length() > 9 && numOfChars > numOfNumbers && !text.substring(8, 9).equals(" ")) { cheatTitle = true; }
        else if (numOfChars > numOfNumbers && countWhiteSpace(text) > 1) { cheatTitle = true; }

        if (text.length() == 17 && text.substring(8, 9).equals(" ") && countWhiteSpace(text) == 1 && text.matches(".*\\d.*")) { cheatTitle = false; }
        for (String igr : IGR_ARRAY) { if (text.equals(igr)) { cheatTitle = false; } }
        return cheatTitle;
    }

    private int countWhiteSpace(String word) {
        int spaceCount = 0;
        for (int i = 0; i < word.length(); i++) { if (word.charAt(i) == ' ') { spaceCount++; } }
        return spaceCount;
    }

    private static String toRgbCss(Color c) {
        return String.format("rgb(%d,%d,%d)",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private boolean confirm(String message, String title) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle(title);
        if (stage != null) { alert.initOwner(stage); }
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }
}

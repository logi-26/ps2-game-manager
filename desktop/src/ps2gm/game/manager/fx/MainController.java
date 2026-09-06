package ps2gm.game.manager.fx;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ps2gm.game.manager.AddGameManager;
import ps2gm.game.manager.BackendClient;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.GameConfigFileManager;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.GenerateSpineART;
import ps2gm.game.manager.HDLDumpManager;
import ps2gm.game.manager.MyFTPClient;
import ps2gm.game.manager.MyListener;
import ps2gm.game.manager.PopsGameManager;
import ps2gm.game.manager.USBUtil;
import ps2gm.game.manager.XMLFileManager;

/**
 * Controller for {@code MainScreen.fxml} - the app shell. JavaFX replacement for
 * the Swing {@code ps2gm.game.manager.MainScreen} (the last screen in the
 * migration). {@code implements MyListener} so every child screen's on-close
 * {@code callbackToUpdateGUIGameList} refreshes the list + detail panel here.
 *
 * Faithful port of the Swing screen's logic (updateGameList / displayGameDetails
 * cascade, console switching, cover display, compat-colour list cells, ~50 menu
 * actions). The clearly-blocking network / subprocess actions (emulator launch,
 * update check, console game-list refresh, FTP delete/ELF-regen) run on daemon
 * threads instead of freezing the UI as the Swing EDT did. Dark mode applies live
 * via AtlantaFX (the Swing version needed a restart).
 */
public class MainController implements MyListener {

    private static final javafx.scene.paint.Color COLOUR_RED = javafx.scene.paint.Color.rgb(190, 35, 25);
    private static final javafx.scene.paint.Color COLOUR_GREEN = javafx.scene.paint.Color.rgb(55, 170, 20);
    private static final javafx.scene.paint.Color COLOUR_ORANGE = javafx.scene.paint.Color.rgb(218, 145, 30);

    private static String img(String name) {
        return PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data"
                + File.separator + "images" + File.separator + name;
    }
    private static final String NO_IMAGE_PS1_COVER = img("No Image Cover.png");
    private static final String NO_IMAGE_PS2_COVER = img("No Image Cover PS2.png");

    @FXML private Label consoleLabel;
    @FXML private ListView<String> gameList;
    @FXML private TextField ps1CountField, ps2CountField;
    @FXML private TextField gameTitleField, gameNumberField, gameIdField, gameSizeField;
    @FXML private ImageView coverView;
    @FXML private Button artButton, cfgButton, chtButton, vmcButton;
    @FXML private TextField releaseDateField, developerField, playersField, deviceCompatField, vmc0Field, vmc1Field;

    @FXML private CheckMenuItem cmiPs1Compat, cmiPs2UlHighlight, cmiDarkMode;
    @FXML private RadioMenuItem rmiPlaystation1, rmiPlaystation2,
            rmiIdPosPs1Start, rmiIdPosPs1End, rmiIdPosPs2Start, rmiIdPosPs2End;
    @FXML private MenuItem miAddPs1Game, miAddPs2Game, miGenerateConfElm, miGenerateUlConf, miSplitPs2Game,
            miMergePs2Game, miPs1Emulator, miPs2Emulator, miMd5, miRefreshGameList, miBatchAddPs1Game,
            miBatchAddPs2Game, miBatchPs1Elf, miDeleteAllElf, miOpenOplDir, miAbout, miChangelog, miCheckUpdate;
    @FXML private Menu menuConsoleFileTransfer;

    private Stage stage;
    private final GameConfigFileManager configManager = new GameConfigFileManager();
    private File selectedGameFolder;
    private boolean suppressSelectionEvents = false;

    // ------------------------------------------------------------ setup

    @FXML
    private void initialize() {
        gameList.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle(empty ? "" : rowStyle(getIndex()));
            }
        });
        gameList.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            if (!suppressSelectionEvents && nv.intValue() >= 0) {
                displayGameDetails();
                updateSplitMergeMenuState();
            }
        });
        gameList.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                if (gameList.getSelectionModel().getSelectedIndex() != -1) { deleteGame(); }
                else { warn("You need to select a game before you can perform this action.", " No game selected!"); }
            } else if (e.getCode() == KeyCode.ENTER) {
                if ("PS1".equals(PopsGameManager.getCurrentConsole())) { launchEmulatorPS1(); }
                else { launchEmulatorPS2(); }
            }
        });
    }

    /** Called by {@link MainApp} before the window is shown. */
    void init(Stage stage) {
        this.stage = stage;
        PopsGameManager.addListener(this);
        suppressSelectionEvents = true;
        cmiPs1Compat.setSelected(PopsGameManager.getGameCompatabilityPS1());
        cmiPs2UlHighlight.setSelected(PopsGameManager.getSplitGameDisplayPS2());
        cmiDarkMode.setSelected(PopsGameManager.getDarkMode());
        suppressSelectionEvents = false;
        initialiseGUI(0);
    }

    private void initialiseGUI(int listIndex) {
        stage.setTitle(PopsGameManager.getFormTitle());

        if (PopsGameManager.getFisrtLaunch()) {
            SetModeScreen.open(); // FX-thread modal (showAndWait) - blocks until a mode is chosen
        }

        try {
            boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());
            suppressSelectionEvents = true;
            rmiPlaystation1.setSelected(ps1);
            rmiPlaystation2.setSelected(!ps1);
            suppressSelectionEvents = false;
            updateCoverLayout();
            updateMainLabel();
        } catch (NullPointerException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }

        PopsGameManager.setGameCompatabilityPS1(cmiPs1Compat.isSelected());
        PopsGameManager.setSplitGameDisplayPS2(cmiPs2UlHighlight.isSelected());

        updateList();
        selectIndex(listIndex);
        updateGameStats();
        try { displayGameDetails(); } catch (NullPointerException ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }

        if (PopsGameManager.isCurrentConsoleSet()) { updateMenuItems(); }
    }

    // -------------------------------------------------- MyListener callback

    @Override
    public void updateGameList(String gameID, int listIndex) {
        if (Platform.isFxApplicationThread()) { applyGameListUpdate(gameID, listIndex); }
        else { Platform.runLater(() -> applyGameListUpdate(gameID, listIndex)); }
    }

    private void applyGameListUpdate(String gameID, int listIndex) {
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());
        switchConsole(ps1 ? "PS1" : "PS2");

        if (gameID == null) {
            int idx = listIndex;
            if (idx == -1) { idx = 0; }
            if (idx == -2) { idx = gameList.getSelectionModel().getSelectedIndex(); }
            clearGameMainDetails();
            updateList();
            List<Game> list = ps1 ? GameListManager.getGameListPS1() : GameListManager.getGameListPS2();
            if (list != null && !list.isEmpty()) { selectIndex(idx); }
        } else {
            List<Game> list = ps1 ? GameListManager.getGameListPS1() : GameListManager.getGameListPS2();
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getGameID().equals(gameID)) { selectIndex(i); }
                }
            }
        }

        updateGameStats();
        updateMainLabel();
        updateMenuItems();
        displayGameDetails();
        int sel = gameList.getSelectionModel().getSelectedIndex();
        if (sel >= 0) { gameList.scrollTo(sel); }
    }

    // ------------------------------------------------------ list / details

    private void updateList() {
        if (!(PopsGameManager.isOPLFolderSet() && PopsGameManager.isCurrentConsoleSet())) { return; }
        List<Game> src = "PS1".equals(PopsGameManager.getCurrentConsole())
                ? GameListManager.getGameListPS1() : GameListManager.getGameListPS2();
        List<String> names = new ArrayList<>();
        if (src != null) { for (Game g : src) { names.add(g.getGameName()); } }
        suppressSelectionEvents = true;
        gameList.setItems(FXCollections.observableArrayList(names));
        suppressSelectionEvents = false;
    }

    private void selectIndex(int index) {
        int size = gameList.getItems().size();
        if (size == 0) { return; }
        int i = Math.max(0, Math.min(index, size - 1));
        gameList.getSelectionModel().select(i);
    }

    private void updateMainLabel() {
        String console = PopsGameManager.getCurrentConsole();
        String mode = PopsGameManager.getCurrentMode();
        String label = "PS1".equals(console) ? "PlayStation 1" : "PlayStation 2";
        if ("SMB".equals(mode) || "HDD".equals(mode)) { consoleLabel.setText(label + "  -  " + mode); }
        else { consoleLabel.setText(label + "  -  USB"); }
    }

    private void updateGameStats() {
        try {
            ps1CountField.setText(GameListManager.getGameListPS1().size() + "  -  Total Size  -  " + GameListManager.getGameSizeDisplayTotalPS1());
            ps2CountField.setText(GameListManager.getGameListPS2().size() + "  -  Total Size  -  " + GameListManager.getGameSizeDisplayTotalPS2());
        } catch (NullPointerException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    private void updateCoverLayout() {
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());
        coverView.setFitWidth(150);
        coverView.setFitHeight(ps1 ? 150 : 210);
        setCover(ps1 ? NO_IMAGE_PS1_COVER : NO_IMAGE_PS2_COVER);
    }

    private void setCover(String path) {
        File f = new File(path);
        coverView.setImage(f.isFile() ? new Image(f.toURI().toString(), coverView.getFitWidth(), coverView.getFitHeight(), true, true) : null);
    }

    private Game selectedGame() {
        int i = gameList.getSelectionModel().getSelectedIndex();
        if (i < 0) { return null; }
        List<Game> list = "PS1".equals(PopsGameManager.getCurrentConsole())
                ? GameListManager.getGameListPS1() : GameListManager.getGameListPS2();
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    private void displayGameDetails() {
        Game g = selectedGame();
        if (g == null) { clearGameConfigDetails(); return; }
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());

        displayGameImages(g, ps1);
        gameTitleField.setText(" " + g.getGameName());
        gameIdField.setText(g.getGameID());
        int total = (ps1 ? GameListManager.getGameListPS1() : GameListManager.getGameListPS2()).size();
        gameNumberField.setText((gameList.getSelectionModel().getSelectedIndex() + 1) + "/" + total);
        gameSizeField.setText(g.getGameReadableSize());

        if (configManager.gameConfigExists(g.getGameID(), g.getGameName())) {
            try { displayGameConfigDetails(configManager.readGameConfigFormatted(g.getGameID(), g.getGameName())); }
            catch (IOException ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
        } else {
            clearGameConfigDetails();
        }

        if (ps1) {
            File gameFolder = new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator
                    + g.getGameName() + "-" + g.getGameID());
            if (gameFolder.isDirectory()) {
                List<String> vmcList = new ArrayList<>();
                File[] files = gameFolder.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().length() > 4 && f.getName().substring(f.getName().length() - 4).equalsIgnoreCase(".VMC")) {
                            if (vmcList.size() <= 1) { vmcList.add(f.getName()); }
                        }
                    }
                }
                if (!vmcList.isEmpty()) {
                    vmc0Field.setText(vmcList.get(0));
                    if (vmcList.size() > 1) { vmc1Field.setText(vmcList.get(1)); }
                }
            }
        }
        updateGameStats();
    }

    private void displayGameImages(Game g, boolean ps1) {
        String artDir = PopsGameManager.getOPLFolder() + File.separator + "ART" + File.separator;
        String base = ps1
                ? PopsGameManager.getFilePrefix() + g.getGameName() + "-" + g.getGameID() + ".ELF_COV"
                : g.getGameID() + "_COV";
        File jpg = new File(artDir + base + ".jpg");
        File png = new File(artDir + base + ".png");
        if (jpg.isFile()) { setCover(jpg.toString()); }
        else if (png.isFile()) { setCover(png.toString()); }
        else { setCover(ps1 ? NO_IMAGE_PS1_COVER : NO_IMAGE_PS2_COVER); }
    }

    private void displayGameConfigDetails(String[] configData) {
        developerField.setText(configData[3] != null ? configData[3] : "");
        releaseDateField.setText(configData[4] != null ? configData[4] : "");
        playersField.setText(configData[5] != null ? configData[5] : "");
        vmc0Field.setText(configData[9] != null ? configData[9] : "");
        vmc1Field.setText(configData[10] != null ? configData[10] : "");

        String device = "";
        if (configData[13] != null) {
            switch (configData[13]) {
                case "1": device = "USB"; break;
                case "5": device = "ETH"; break;
                case "6": device = "HDD"; break;
                case "2": device = "USB, ETH"; break;
                case "3": device = "USB, HDD"; break;
                case "4": device = "HDD, ETH"; break;
                case "all": device = "USB, HDD, ETH"; break;
                default: break;
            }
        }
        deviceCompatField.setText(device);
    }

    private void clearGameConfigDetails() {
        for (TextField f : new TextField[] {developerField, releaseDateField, playersField, deviceCompatField, vmc0Field, vmc1Field}) {
            f.setText("");
        }
    }

    private void clearGameMainDetails() {
        gameTitleField.setText("");
        gameNumberField.setText("0/0");
        gameIdField.setText("");
        gameSizeField.setText("");
        setCover("PS1".equals(PopsGameManager.getCurrentConsole()) ? NO_IMAGE_PS1_COVER : NO_IMAGE_PS2_COVER);
    }

    // compat-colour styling for a list row, mirroring the Swing DefaultListCellRenderer.
    private String rowStyle(int index) {
        try {
            if ("PS1".equals(PopsGameManager.getCurrentConsole())) {
                Game g = GameListManager.getGameListPS1().get(index);
                if (PopsGameManager.getGameCompatabilityPS1()) {
                    switch (g.getCompatibleHDD()) {
                        case "1": return css(COLOUR_GREEN);
                        case "2": return css(COLOUR_RED);
                        case "0": return g.getMultiDiscGame() ? css(COLOUR_ORANGE) : "";
                        default: return "";
                    }
                }
                return g.getMultiDiscGame() ? css(COLOUR_ORANGE) : "";
            } else {
                Game g = GameListManager.getGameListPS2().get(index);
                if (PopsGameManager.getSplitGameDisplayPS2() && g.getULGame()) { return css(COLOUR_ORANGE); }
                return "";
            }
        } catch (Exception ex) {
            return "";
        }
    }

    private static String css(javafx.scene.paint.Color c) {
        return String.format("-fx-text-fill: rgb(%d,%d,%d);",
                (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    // ---------------------------------------------------- console / menu state

    private void switchConsole(String console) {
        updateCoverLayout();
        PopsGameManager.setCurrentConsole(console);
        updateMainLabel();
        updateList();
        updateMenuItems();
        try { XMLFileManager.writeSettingsXML(); }
        catch (Exception ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
    }

    private void updateMenuItems() {
        if (PopsGameManager.getFisrtLaunch()) { return; }
        String mode = PopsGameManager.getCurrentMode();
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());

        miMd5.setVisible("SMB".equals(mode));
        boolean hdd = "HDD".equals(mode);
        menuConsoleFileTransfer.setVisible(hdd);
        miRefreshGameList.setVisible(hdd);

        miAddPs1Game.setVisible(ps1);
        miAddPs2Game.setVisible(!ps1);
        miBatchAddPs1Game.setVisible(ps1);
        miBatchAddPs2Game.setVisible(!ps1);
        miGenerateConfElm.setVisible(ps1);
        miBatchPs1Elf.setVisible(ps1);
        miDeleteAllElf.setVisible(ps1);
        miPs1Emulator.setVisible(ps1);
        miPs2Emulator.setVisible(!ps1);

        boolean smb = "SMB".equals(mode);
        miGenerateUlConf.setVisible(!ps1 && smb);
        miMergePs2Game.setVisible(!ps1 && smb);
        miSplitPs2Game.setVisible(!ps1 && smb);

        suppressSelectionEvents = true;
        rmiPlaystation1.setSelected(ps1);
        rmiPlaystation2.setSelected(!ps1);
        String idPos = ps1 ? PopsGameManager.getGameIDPositionPS1() : PopsGameManager.getGameIDPositionPS2();
        if (ps1) {
            rmiIdPosPs1Start.setSelected("start".equals(idPos));
            rmiIdPosPs1End.setSelected("end".equals(idPos));
        } else {
            rmiIdPosPs2Start.setSelected("start".equals(idPos));
            rmiIdPosPs2End.setSelected("end".equals(idPos));
        }
        suppressSelectionEvents = false;
    }

    private void updateSplitMergeMenuState() {
        Game g = selectedGame();
        if (g == null || !"PS2".equals(PopsGameManager.getCurrentConsole())) { return; }
        if (g.getULGame()) {
            miMd5.setDisable(true);
            miSplitPs2Game.setDisable(true);
            miMergePs2Game.setDisable(g.getGameRawSize() <= 0);
        } else {
            miMd5.setDisable(false);
            miSplitPs2Game.setDisable(false);
            miMergePs2Game.setDisable(true);
        }
    }

    // ----------------------------------------------------------- toolbar

    @FXML private void onArt() {
        if (gameList.getSelectionModel().getSelectedIndex() != -1) {
            GameImageScreen.open(PopsGameManager.getCurrentConsole(), gameList.getSelectionModel().getSelectedIndex());
        } else { warn("You need to select a game before you can manage the game ART.", " No game selected!"); }
    }

    @FXML private void onCfg() {
        if (gameList.getSelectionModel().getSelectedIndex() != -1) {
            GameConfigScreen.open(gameList.getSelectionModel().getSelectedIndex());
        } else { warn("You need to select a game before you can create/edit the config file.", " No game selected!"); }
    }

    @FXML private void onCht() {
        if (gameList.getSelectionModel().getSelectedIndex() != -1) {
            GameCheatScreen.open(gameList.getSelectionModel().getSelectedIndex());
        } else { warn("You need to select a game before you can create/edit the cheat file.", " No game selected!"); }
    }

    @FXML private void onVmc() {
        if (gameList.getSelectionModel().getSelectedIndex() != -1) {
            GameVmcScreen.open(gameList.getSelectionModel().getSelectedIndex());
        } else { warn("You need to select a game before you can download any VMC files.", " No game selected!"); }
    }

    // ------------------------------------------------------- File menu

    @FXML private void onOpenOplDirectory() {
        if (!PopsGameManager.isOPLFolderSet()) { info("OPL directory has not been set.", " "); }
        else { PopsGameManager.openDirectory(PopsGameManager.getOPLFolder()); }
    }
    @FXML private void onAbout() { AboutScreen.show(PopsGameManager.getFormTitle(), PopsGameManager.getApplicationReleaseDate()); }
    @FXML private void onChangelog() { ChangelogScreen.show(); }
    @FXML private void onExit() { Platform.exit(); System.exit(0); }

    @FXML private void onPs1CompatToggle() {
        PopsGameManager.setGameCompatabilityPS1(cmiPs1Compat.isSelected());
        gameList.refresh();
        saveSettings();
    }
    @FXML private void onPs2UlToggle() {
        PopsGameManager.setSplitGameDisplayPS2(cmiPs2UlHighlight.isSelected());
        gameList.refresh();
        saveSettings();
    }
    @FXML private void onDarkModeToggle() {
        PopsGameManager.setDarkMode(cmiDarkMode.isSelected());
        Application.setUserAgentStylesheet(cmiDarkMode.isSelected()
                ? new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet()
                : new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
        saveSettings();
    }
    @FXML private void onIdPosPs1Start() { if (rmiIdPosPs1Start.isSelected() && !suppressSelectionEvents) { PopsGameManager.setGameIDPositionPS1("start"); } }
    @FXML private void onIdPosPs1End() { if (rmiIdPosPs1End.isSelected() && !suppressSelectionEvents) { PopsGameManager.setGameIDPositionPS1("end"); } }
    @FXML private void onIdPosPs2Start() { if (rmiIdPosPs2Start.isSelected() && !suppressSelectionEvents) { PopsGameManager.setGameIDPositionPS2("start"); } }
    @FXML private void onIdPosPs2End() { if (rmiIdPosPs2End.isSelected() && !suppressSelectionEvents) { PopsGameManager.setGameIDPositionPS2("end"); } }

    // -------------------------------------------------------- Console menu

    @FXML private void onSelectPs1() {
        if (suppressSelectionEvents) { return; }
        if ("PS2".equals(PopsGameManager.getCurrentConsole())) {
            switchConsole("PS1");
            selectIndex(0);
            updateGameStats();
            displayGameDetails();
        }
    }
    @FXML private void onSelectPs2() {
        if (suppressSelectionEvents) { return; }
        if ("PS1".equals(PopsGameManager.getCurrentConsole())) {
            switchConsole("PS2");
            selectIndex(0);
            updateGameStats();
            displayGameDetails();
        }
    }
    @FXML private void onChangeMode() { SetModeScreen.open(); }
    @FXML private void onConsolePartitions() { SetPartitionScreen.open(); }
    @FXML private void onTransferFiles() { SyncFileScreen.open(); }

    // ---------------------------------------------------------- Tools menu

    @FXML private void onGenerateConfElm() { GameListManager.writeConfigELM(); }
    @FXML private void onAddGame() { displayAddGameScreen(); }
    @FXML private void onBatchAddGame() { displayBatchAddGameScreen(); }
    @FXML private void onBatchDownload() { BatchDownloadScreen.open(PopsGameManager.getCurrentConsole()); }
    @FXML private void onCheckGameNames() { checkLongGameNames(); }
    @FXML private void onGeneratePs1Elf() { runBg(this::generateNewElfFiles); }
    @FXML private void onCheckUpdate() { runBg(this::checkForUpdate); }
    @FXML private void onPs1EmulatorSettings() { EmulatorSettingsScreen.open("PS1"); }
    @FXML private void onPs2EmulatorSettings() { EmulatorSettingsScreen.open("PS2"); }

    @FXML private void onMd5() {
        Game g = selectedGame();
        if (g == null) { return; }
        File file = "PS1".equals(PopsGameManager.getCurrentConsole())
                ? new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + g.getGameName() + "-" + g.getGameID() + ".VCD")
                : new File(g.getGamePath());
        if (file.exists()) { HashCheckerScreen.open(file); }
    }

    @FXML private void onGenerateUlConf() {
        if (confirm("Are you sure that you want to re-generate the ul.cfg file?\nThis will overwrite the file if it already exists!", " Generate ul.cfg File")) {
            USBUtil.regenerateULCFG();
            GameListManager.createGameListsPS2(false);
            initialiseGUI(gameList.getSelectionModel().getSelectedIndex());
        }
    }

    @FXML private void onSplitPs2Game() {
        Game g = GameListManager.getGameListPS2().get(gameList.getSelectionModel().getSelectedIndex());
        if (g.getGameName().length() <= 32) { SplitMergeScreen.open(g, "Split"); }
        else { warn("You cannot split games that have names greater than 32 characters in length.", " Game Name is Too Long!"); }
    }
    @FXML private void onMergePs2Game() {
        SplitMergeScreen.open(GameListManager.getGameListPS2().get(gameList.getSelectionModel().getSelectedIndex()), "Merge");
    }

    @FXML private void onGenerateSpine() {
        GenerateSpineART gen = new GenerateSpineART();
        if ("PS1".equals(PopsGameManager.getCurrentConsole())) { gen.generateForPS1(); } else { gen.generateForPS2(); }
    }
    @FXML private void onDeleteAllSpineArt() { new GenerateSpineART().deleteForPS2(); }

    @FXML private void onRefreshGameList() { runBg(this::refreshGameListFromConsole); }

    // ------------------------------------------------------ Delete Files menu

    @FXML private void onDeleteAllArt() { deleteFilesPrompt("ART", true); }
    @FXML private void onDeleteAllConfig() { deleteFilesPrompt("CFG", true); }
    @FXML private void onDeleteAllCheat() { deleteFilesPrompt("CHT", true); }
    @FXML private void onDeleteUnusedArt() { deleteFilesPrompt("ART", false); }
    @FXML private void onDeleteUnusedConfig() { deleteFilesPrompt("CFG", false); }
    @FXML private void onDeleteUnusedCheat() { deleteFilesPrompt("CHT", false); }

    private void deleteFilesPrompt(String type, boolean all) {
        String kind = all ? "" : "unused ";
        String mode = PopsGameManager.getCurrentMode();
        String msg = "HDD".equals(mode)
                ? "Are you sure that you want to delete all of the " + kind + type + " files on your console?\n\nFTP server must be running on your console in order to perform this task."
                : "Are you sure that you want to delete all of the " + kind + type + " files?";
        if (confirm(msg, " Delete " + (all ? "" : "Unused ") + type)) {
            if (all) { GameListManager.deleteAllFiles(type); } else { GameListManager.deleteUnusedFiles(type); }
        }
    }

    @FXML private void onDeleteAllElf() {
        String mode = PopsGameManager.getCurrentMode();
        if (!("SMB".equals(mode) || "HDD_USB".equals(mode))) { return; }
        if (!confirm("Are you sure that you want to delete all of the ELF files in the POPS directory?", " Delete All ELF Files")) { return; }
        File folder = new File(PopsGameManager.getOPLFolder() + File.separator + "POPS");
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().length() >= 3 && f.getName().substring(f.getName().length() - 3).equals("ELF")) { f.delete(); }
            }
        }
    }

    // --------------------------------------------------------- add game

    private void displayAddGameScreen() {
        File popstarter = new File(PopsGameManager.getCurrentDirectory() + File.separator + "POPSTARTER" + File.separator + "POPSTARTER.ELF");
        if ("PS1".equals(PopsGameManager.getCurrentConsole()) && !popstarter.exists()) {
            error("Could not locate POPSTARTER.ELF in the POPSTARTER directory.", " Missing POPSTARTER.ELF!");
            return;
        }
        if (!PopsGameManager.isOPLFolderSet()) { info("OPL directory is not set.", " "); return; }

        FileChooser chooser = new FileChooser();
        File start = selectedGameFolder != null ? selectedGameFolder : new File(PopsGameManager.getOPLFolder());
        if (start.isDirectory()) { chooser.setInitialDirectory(start); }
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());
        chooser.setTitle(ps1 ? "Select PS1 Game" : "Select PS2 Game");
        chooser.getExtensionFilters().add(ps1
                ? new FileChooser.ExtensionFilter("PS1 GAMES", "*.VCD", "*.vcd", "*.CUE", "*.cue")
                : new FileChooser.ExtensionFilter("PS2 GAMES", "*.ISO", "*.iso", "*.ZSO", "*.zso"));

        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) { return; }
        String ext = chosen.toString().substring(chosen.toString().length() - 3);
        selectedGameFolder = chosen.getParentFile();

        switch (PopsGameManager.getCurrentMode()) {
            case "HDD":
                AddGameHddScreen.open(PopsGameManager.getCurrentConsole(), false, null, chosen);
                break;
            case "HDD_USB":
            case "SMB":
                AddGameSmbScreen.open(false, ext, chosen);
                break;
            default:
                break;
        }
    }

    private void displayBatchAddGameScreen() {
        File popstarter = new File(PopsGameManager.getCurrentDirectory() + File.separator + "POPSTARTER" + File.separator + "POPSTARTER.ELF");
        if ("PS1".equals(PopsGameManager.getCurrentConsole()) && !popstarter.exists()) {
            error("Could not locate POPSTARTER.ELF in the POPSTARTER directory.", " Missing POPSTARTER.ELF!");
            return;
        }
        if (!PopsGameManager.isOPLFolderSet()) { info("OPL directory is not set.", " "); return; }

        DirectoryChooser chooser = new DirectoryChooser();
        File start = selectedGameFolder != null ? selectedGameFolder : new File(PopsGameManager.getOPLFolder());
        if (start.isDirectory()) { chooser.setInitialDirectory(start); }
        File dir = chooser.showDialog(stage);
        if (dir == null) { return; }
        selectedGameFolder = dir;
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());

        switch (PopsGameManager.getCurrentMode()) {
            case "HDD": {
                boolean hasFiles = false;
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getAbsolutePath().toUpperCase().endsWith(ps1 ? "VCD" : "ISO")) { hasFiles = true; }
                    }
                }
                if (hasFiles) { AddGameHddScreen.open(PopsGameManager.getCurrentConsole(), true, dir.getPath(), dir); }
                else { warn("This directory does not appear to contain any " + (ps1 ? "VCD" : "ISO") + " files!", " No Games Detected!"); }
                break;
            }
            case "HDD_USB":
            case "SMB":
                AddGameSmbScreen.open(true, null, dir);
                break;
            default:
                break;
        }
    }

    // --------------------------------------------------------- delete game

    private void deleteGame() {
        Game g = selectedGame();
        if (g == null) { return; }
        String name = g.getGameName();
        String id = g.getGameID();
        int idx = gameList.getSelectionModel().getSelectedIndex();
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());
        String mode = PopsGameManager.getCurrentMode();

        if (ps1) {
            if ("HDD_USB".equals(mode) || "SMB".equals(mode)) {
                if (!confirm("Are you sure you want to delete - " + name + " ?", " Delete Game")) { return; }
                File vcd = new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + name + "-" + id + ".VCD");
                if (vcd.exists()) {
                    vcd.delete();
                    new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + PopsGameManager.getFilePrefix() + name + "-" + id + ".ELF").delete();
                    List<Game> list = GameListManager.getGameListPS1();
                    list.remove(idx);
                    GameListManager.setGameListPS1(list);
                    updateGameList(null, idx - 1);
                    GameListManager.writeConfigELM();
                }
            } else if ("HDD".equals(mode)) {
                if (!confirm("Are you sure you want to delete - " + name + " ?\n\nFTP Server must be running on your console in order to perform this task!", " Connect to PlayStation 2")) { return; }
                runBg(() -> deletePs1GameOverFtp(name, id, idx));
            }
        } else {
            if ("HDD_USB".equals(mode) || "SMB".equals(mode)) {
                if (!confirm("Are you sure you want to delete - " + name + " ?", " Delete Game")) { return; }
                File file = new File(g.getGamePath());
                if (file.exists()) {
                    file.delete();
                    GameListManager.createGameListsPS2(false);
                    updateGameList(null, idx - 1);
                }
            } else if ("HDD".equals(mode)) {
                error("This application cannot yet delete PS2 games from the consoles internal hdd.", " Delete Game");
            }
        }
    }

    private void deletePs1GameOverFtp(String gameName, String gameID, int idx) {
        MyFTPClient myFTP = new MyFTPClient();
        if (!myFTP.connectToConsole(PopsGameManager.getPS2IP())) { return; }

        String remoteVcdPath = null;
        if ("hdd".equals(GameListManager.getFormattedVCDDrive())) { remoteVcdPath = "/pfs/" + GameListManager.getFormattedVCDPartition() + "/"; }
        else if ("mass".equals(GameListManager.getFormattedVCDDrive())) { remoteVcdPath = "/mass/" + GameListManager.getFormattedVCDPartition() + "/"; }
        myFTP.changeDirectory(GameListManager.getFormattedVCDDrive() + "/" + GameListManager.getFormattedVCDPartition() + "/" + GameListManager.getFormattedVCDFolder());
        myFTP.deleteRemoteFile(remoteVcdPath + gameName + "-" + gameID + ".VCD");
        myFTP.disconnectFromConsole();
        myFTP.connectToConsole(PopsGameManager.getPS2IP());

        String elfFolder = "";
        String elfPartitionName = "";
        String[] splitELFFolder = GameListManager.getFormattedELFFolder().contains("/") ? GameListManager.getFormattedELFFolder().split("/") : null;
        if (splitELFFolder != null && splitELFFolder.length > 1) {
            elfPartitionName = splitELFFolder[0];
            for (int i = 1; i < splitELFFolder.length; i++) { elfFolder = (i != 1) ? elfFolder + "/" + splitELFFolder[i] : elfFolder + splitELFFolder[i]; }
        } else {
            elfFolder = GameListManager.getFormattedELFFolder();
        }
        String remoteElfPath = null;
        if ("hdd".equals(GameListManager.getFormattedELFDrive())) { remoteElfPath = "/pfs/" + GameListManager.getFormattedELFPartition() + "/" + elfFolder + "/"; }
        else if ("mass".equals(GameListManager.getFormattedELFDrive())) { remoteElfPath = "/mass/" + GameListManager.getFormattedELFPartition() + "/" + elfFolder + "/"; }
        myFTP.changeDirectory(GameListManager.getFormattedELFDrive() + "/" + GameListManager.getFormattedELFPartition() + "/" + elfPartitionName);
        myFTP.changeDirectory(remoteElfPath);
        myFTP.deleteRemoteFile(remoteElfPath + gameName + "-" + gameID + ".ELF");
        myFTP.disconnectFromConsole();

        List<Game> list = GameListManager.getGameListFromConsolePS1();
        GameListManager.writeGameListFilePS1(list);
        try { GameListManager.createGameListFromFile("PS1", new File(PopsGameManager.getCurrentDirectory() + File.separator + "hdd" + File.separator + "gameListPS1")); }
        catch (IOException ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
        updateGameList(null, idx - 1);
    }

    // ------------------------------------------------------ console refresh

    private void refreshGameListFromConsole() {
        boolean ps1 = "PS1".equals(PopsGameManager.getCurrentConsole());
        if (ps1) {
            if (!confirmFx("FTP server must be running on your console in order to perform this task!", " Refresh PS1 Game List")) { return; }
            List<Game> list = GameListManager.getGameListFromConsolePS1();
            if (list != null) {
                GameListManager.writeGameListFilePS1(list);
                GameListManager.setGameListPS1(list);
                updateGameList(null, 0);
            }
        } else {
            if (!confirmFx("HDL Server must be running on your console in order to perform this task!", " Refresh PS2 Game List")) { return; }
            try {
                List<Game> list = new HDLDumpManager().hdlDumpGetTOC(PopsGameManager.getPS2IP());
                if (list != null) {
                    GameListManager.writeGameListFilePS2(list);
                    GameListManager.setGameListPS2(list);
                    updateGameList(null, 0);
                }
            } catch (IOException ex) {
                Platform.runLater(() -> error("There was a problem launching HDL_Dump!", " HDL_Dump Error!"));
            } catch (InterruptedException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
        }
    }

    // ------------------------------------------------------- generate ELF

    private void generateNewElfFiles() {
        String mode = PopsGameManager.getCurrentMode();
        if ("SMB".equals(mode) || "HDD_USB".equals(mode)) {
            List<String> vcdFiles = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(Paths.get(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator))) {
                paths.forEach(p -> {
                    if (Files.isRegularFile(p) && p.toString().endsWith(".VCD")) { vcdFiles.add(p.toString()); }
                });
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }
            if (vcdFiles.isEmpty()) { return; }

            File popstarter = new File(PopsGameManager.getCurrentDirectory() + File.separator + "POPSTARTER" + File.separator + "POPSTARTER.elf");
            if (popstarter.exists() && !popstarter.isDirectory()) {
                popstarter.renameTo(new File(PopsGameManager.getCurrentDirectory() + File.separator + "POPSTARTER" + File.separator + "POPSTARTER.ELF"));
            }
            popstarter = new File(PopsGameManager.getCurrentDirectory() + File.separator + "POPSTARTER" + File.separator + "POPSTARTER.ELF");
            if (!(popstarter.exists() && !popstarter.isDirectory())) {
                Platform.runLater(() -> error("Cannot find POPSTARTER.ELF in the POPSTARTER directory!\n\nPut the POPSTARTER.ELF in the directory and try again.", " Error Generating .ELF!"));
                return;
            }
            boolean ok = true;
            for (String vcdPath : vcdFiles) {
                String path = vcdPath.substring(0, vcdPath.lastIndexOf(File.separator) + 1);
                String vcdName = vcdPath.substring(vcdPath.lastIndexOf(File.separator) + 1);
                String elfName = PopsGameManager.getFilePrefix() + vcdName.substring(0, vcdName.length() - 3) + "ELF";
                File elf = new File(path + elfName);
                if (elf.exists() && !elf.isDirectory()) { elf.delete(); }
                AddGameManager.generateElf(elfName, PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator);
                if (!new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + elfName).exists()) { ok = false; }
            }
            if (ok) { Platform.runLater(() -> info("The new ELF files have been generated!.", " ELF Files Generated")); }
        } else if ("HDD".equals(mode)) {
            if (!confirmFx("FTP server must be running on your console in order to perform this task.", " Generate New ELF Files")) { return; }
            File tmp = new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + "ELF_TEMP");
            if (tmp.mkdir()) {
                GameListManager.getGameListFromConsolePS1().forEach(cg ->
                        AddGameManager.generateElf(cg.getGameName() + "-" + cg.getGameID() + ".ELF", PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + "ELF_TEMP" + File.separator));
            }
            List<File> files = null;
            try { files = Files.walk(tmp.toPath()).filter(Files::isRegularFile).map(Path::toFile).collect(Collectors.toList()); }
            catch (IOException ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }

            MyFTPClient myFTP = new MyFTPClient();
            if (myFTP.connectToConsole(PopsGameManager.getPS2IP())) {
                myFTP.changeDirectory("hdd/0/+OPL");
                if (files != null) {
                    for (File f : files) {
                        myFTP.deleteRemoteFile("/pfs/0/APPS/" + f.getName());
                        myFTP.addFileToPS2(PopsGameManager.getOPLFolder() + "POPS" + File.separator + "ELF_TEMP" + File.separator, f.getName(), "/pfs/0/APPS/", "PS1".equals(PopsGameManager.getCurrentConsole()));
                    }
                }
                myFTP.disconnectFromConsole();
            }
            deleteTree(tmp);
        }
    }

    // ------------------------------------------------------- emulator launch

    private void launchEmulatorPS2() {
        String mode = PopsGameManager.getCurrentMode();
        if (!("SMB".equals(mode) || "HDD_USB".equals(mode))) {
            error("PS2 emulator can only be used in \"SMB\" mode.", " Cannot Use Emulator!");
            return;
        }
        if (!(PopsGameManager.getEmulatorInUsePS2() && PopsGameManager.getEmulatorPathPS2() != null)) { return; }
        Game g = selectedGame();
        if (g == null) { return; }
        runBg(() -> {
            int idx = PopsGameManager.getEmulatorPathPS2().lastIndexOf(File.separator);
            String exePath = PopsGameManager.getEmulatorPathPS2().substring(0, idx);
            String exeName = PopsGameManager.getEmulatorPathPS2().substring(idx + 1);
            List<String> commands = new ArrayList<>();
            commands.add(exePath + File.separator + exeName);
            commands.add(g.getGamePath());
            if (PopsGameManager.getEmulatorFullScreenPS2()) { commands.add("--fullscreen"); }
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.directory(new File(exePath));
            try { pb.start().waitFor(); }
            catch (IOException | InterruptedException ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
        });
    }

    private void launchEmulatorPS1() {
        String mode = PopsGameManager.getCurrentMode();
        if (!("SMB".equals(mode) || "HDD_USB".equals(mode))) {
            error("PS1 emulator can only be used in \"SMB\" mode.", " Cannot Use Emulator!");
            return;
        }
        if (!(PopsGameManager.getEmulatorInUsePS1() && PopsGameManager.getEmulatorPathPS1() != null)) { return; }
        Game g = selectedGame();
        if (g == null) { return; }
        runBg(() -> {
            int idx = PopsGameManager.getEmulatorPathPS1().lastIndexOf(File.separator);
            String gameName = g.getGameName();
            String gameID = g.getGameID();
            String exePath = PopsGameManager.getEmulatorPathPS1().substring(0, idx);
            String exeName = PopsGameManager.getEmulatorPathPS1().substring(idx + 1);
            String gamePath = PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + gameName + "-" + gameID + ".VCD";
            File tempFolder = new File(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + "EMU_TEMP");
            tempFolder.mkdirs();
            if (!tempFolder.exists()) { return; }

            String appFolder = "windows";
            String appName = "pops2cue.exe";
            if (PopsGameManager.getOSType().equals("Linux") || PopsGameManager.getOSType().equals("Mac")) {
                appFolder = "linux";
                appName = "pops2cue";
            }
            List<String> commands = new ArrayList<>();
            commands.add(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "tools" + File.separator + appFolder + File.separator + appName);
            commands.add(gamePath);
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.directory(new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "tools" + File.separator + appFolder));
            try {
                pb.start().waitFor();
                Files.move(Paths.get(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + gameName + "-" + gameID + ".bin"),
                        Paths.get(tempFolder + File.separator + gameName + "-" + gameID + ".bin"));
                Files.move(Paths.get(PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator + gameName + "-" + gameID + ".cue"),
                        Paths.get(tempFolder + File.separator + gameName + "-" + gameID + ".cue"));
            } catch (IOException | InterruptedException ex) {
                PopsGameManager.displayErrorMessageDebug(ex.toString());
            }

            File binFile = new File(tempFolder + File.separator + gameName + "-" + gameID + ".bin");
            if (binFile.exists() && !binFile.isDirectory()) {
                List<String> emu = new ArrayList<>();
                emu.add(exePath + File.separator + exeName);
                emu.add("-nogui");
                emu.add("-cdfile");
                emu.add(binFile.getAbsolutePath());
                ProcessBuilder emuPb = new ProcessBuilder(emu);
                emuPb.directory(new File(exePath));
                try {
                    Process p = emuPb.start();
                    p.waitFor();
                } catch (IOException | InterruptedException ex) {
                    PopsGameManager.displayErrorMessageDebug(ex.toString());
                }
                deleteTree(tempFolder);
            }
        });
    }

    // ------------------------------------------------------- update check

    private void checkForUpdate() {
        BackendClient api = PopsGameManager.newBackendClient();
        String response = api.sendMessageToServer("VERSION");
        if (response == null || "NO_RESPONSE".equals(response)) {
            Platform.runLater(() -> warn("The server is currently not responding or the connection is being blocked by your firewall!", " Server Not Responding"));
            return;
        }
        String[] parts = response.contains(",") ? response.split(",") : null;
        if (parts == null || parts.length <= 1) { return; }
        String newVersion = parts[0];
        String newDate = parts[1];
        String current = PopsGameManager.getApplicationVersionNumber();
        if (current.equals(newVersion)) {
            Platform.runLater(() -> info("There are currently no updates available!  \n\nApplication Version : " + current + "\nServer Version : " + newVersion, " No Update Available"));
        } else if (!"NO_RESPONSE".equals(current)) {
            Platform.runLater(() -> {
                if (confirm("There is an update available for this software!  \n\nCurrent Version : " + current + "  -  (" + PopsGameManager.getApplicationReleaseDate() + ")  \nLatest Version   : " + newVersion + "  -  (" + newDate + ")  \n\nDo you want to download the update?", " Update Available")) {
                    runBg(() -> api.getJarFileFromServer(newVersion));
                }
            });
        }
    }

    // ------------------------------------------------------- long names

    private void checkLongGameNames() {
        List<Game> longNames = new ArrayList<>();
        List<Game> src = "PS1".equals(PopsGameManager.getCurrentConsole())
                ? GameListManager.getGameListPS1() : GameListManager.getGameListPS2();
        if (src != null) { for (Game g : src) { if (g.getGameName().length() > 32) { longNames.add(g); } } }
        if (!longNames.isEmpty()) { GameLongNameScreen.open(PopsGameManager.getCurrentConsole(), longNames); }
        else { info("You do not have any " + PopsGameManager.getCurrentConsole() + " games with names greater than 32 characters in length.", " No Games To Rename"); }
    }

    // ------------------------------------------------------------ helpers

    private void runBg(Runnable r) {
        Thread t = new Thread(() -> {
            try { r.run(); } catch (Exception ex) { PopsGameManager.displayErrorMessageDebug(ex.toString()); }
        }, "fx-main-bg");
        t.setDaemon(true);
        t.start();
    }

    private static void deleteTree(File root) {
        if (!root.isDirectory()) { return; }
        try {
            Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { Files.delete(dir); return FileVisitResult.CONTINUE; }
            });
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
    }

    private void saveSettings() {
        try { XMLFileManager.writeSettingsXML(); }
        catch (Exception ex) { PopsGameManager.displayErrorMessageDebug("Error saving the settings!\n\n" + ex.toString()); }
    }

    private void warn(String m, String t) { alert(Alert.AlertType.WARNING, m, t); }
    private void error(String m, String t) { alert(Alert.AlertType.ERROR, m, t); }
    private void info(String m, String t) { alert(Alert.AlertType.INFORMATION, m, t); }

    private void alert(Alert.AlertType type, String message, String title) {
        Alert a = new Alert(type, message);
        a.setHeaderText(null);
        a.setTitle(title);
        if (stage != null) { a.initOwner(stage); }
        a.showAndWait();
    }

    private boolean confirm(String message, String title) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(null);
        a.setTitle(title);
        if (stage != null) { a.initOwner(stage); }
        return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    // confirm() usable from a background thread (blocks it on an FX-thread dialog)
    private boolean confirmFx(String message, String title) {
        if (Platform.isFxApplicationThread()) { return confirm(message, title); }
        final boolean[] result = {false};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(() -> { result[0] = confirm(message, title); latch.countDown(); });
        try { latch.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        return result[0];
    }
}

package ps2gm.game.manager.fx;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import ps2gm.game.manager.BackendClient;
import ps2gm.game.manager.Console;
import ps2gm.game.manager.Game;
import ps2gm.game.manager.GameConfigFileManager;
import ps2gm.game.manager.GameListManager;
import ps2gm.game.manager.PopsGameManager;

/**
 * Controller for {@code GameConfigScreen.fxml} - the per-game OPL {@code .cfg}
 * editor. JavaFX replacement for the Swing {@code GameConfigScreen} (2975 LOC).
 *
 * The data model is unchanged: a 27-slot {@code newConfigData} array (index order
 * = {@link GameConfigFileManager}'s {@code MANAGED_KEYS}) plus a 6-slot
 * {@code newGlobalConfigData} for {@code conf_game.cfg}'s GSM/Cheat defaults. The
 * GSM / Cheat / PADEMU "Source" radios switch each feature's widgets between this
 * game's own values and the global file's, exactly as the Swing version did. The
 * custom 5-star rating widget and the 9 parental-rating systems are ported as
 * data tables (see {@code RATING_*} below) rather than the nested switches.
 *
 * The one deliberate behaviour change: the Swing screen disabled the Save button
 * immediately after loading an existing config (line 157), making it usable only
 * right after "New Config"; here Save stays enabled while the form is active.
 */
public class GameConfigController implements FxScreens.StageAware {

    private static String img(String name) {
        return PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data"
                + File.separator + "images" + File.separator + name;
    }
    private static String ratingImg(String name) { return img("rating" + File.separator + name); }

    private static final String STAR_ACTIVE = img("Star Active.png");
    private static final String STAR_INACTIVE = img("Star Inactive.png");
    private static final String NO_RATING = ratingImg("No_Rating.png");

    // system (combo label) -> image file per level (index 0 == level 1). Verbatim from updateParentalRatingImage().
    private static final Map<String, String[]> RATING_IMAGE = new LinkedHashMap<>();
    // system (lower-case) -> raw config value -> level. Verbatim from switchParentRatingValue().
    private static final Map<String, Map<String, Integer>> RATING_READ = new LinkedHashMap<>();
    // system (combo label) -> "Parental=sys/val" per level (index 0 == level 1). Verbatim from convertParentRatingValue().
    private static final Map<String, String[]> RATING_WRITE = new LinkedHashMap<>();
    static {
        RATING_IMAGE.put("BBFC", new String[] {"BBFC_U.png","BBFC_PG.png","BBFC_12.png","BBFC_15.png","BBFC_18.png"});
        RATING_IMAGE.put("CERO", new String[] {"CERO_A.png","CERO_B.png","CERO_C.png","CERO_D.png","CERO_Z.png","CERO_Demo.png","CERO_Pending.png"});
        RATING_IMAGE.put("DEJUS", new String[] {"DEJUS_L.png","DEJUS_10.png","DEJUS_12.png","DEJUS_14.png","DEJUS_16.png","DEJUS_18.png"});
        RATING_IMAGE.put("ELSPA", new String[] {"ELSPA_3.jpg","ELSPA_11.jpg","ELSPA_15.jpg","ELSPA_18.jpg"});
        RATING_IMAGE.put("ESRA", new String[] {"ESRA_3.png","ESRA_7.png","ESRA_12.png","ESRA_15.png","ESRA_18.png"});
        RATING_IMAGE.put("ESRB", new String[] {"ESRB_Early_Childhood.png","ESRB_Everyone.png","ESRB_Everyone_10+.png","ESRB_Teen.png","ESRB_Mature.png","ESRB_Adults_Only.png"});
        RATING_IMAGE.put("OFLC", new String[] {"OFLC_E.png","OFLC_G.png","OFLC_PG.png","OFLC_M.png","OFLC_15.png","OFLC_R18.png","OFLC_X18.png"});
        RATING_IMAGE.put("PEGI", new String[] {"PEGI_3.png","PEGI_7.png","PEGI_12.png","PEGI_16.png","PEGI_18.png"});
        RATING_IMAGE.put("USK", new String[] {"USK_0.png","USK_6.png","USK_12.png","USK_16.png","USK_18.png"});

        RATING_READ.put("bbfc", ratingRead("pg",1,"u",2,"12",3,"15",4,"16",5));
        RATING_READ.put("cero", ratingRead("a",1,"b",2,"c",3,"d",4,"z",5,"demo",6,"pending",7));
        RATING_READ.put("dejus", ratingRead("l",1,"10",2,"12",3,"14",4,"16",5,"18",6));
        RATING_READ.put("elspa", ratingRead("3",1,"3a",1,"11",2,"11a",2,"15",3,"15a",3,"18",4,"18a",4));
        RATING_READ.put("esra", ratingRead("3",1,"7",2,"12",3,"15",4,"18",5));
        RATING_READ.put("esrb", ratingRead("3",1,"e",2,"10",3,"teen",4,"17",5,"18",6,"m",6));
        RATING_READ.put("oflc", ratingRead("e",1,"g",2,"pg",3,"m",4,"ma",5,"r",6,"x",7));
        RATING_READ.put("pegi", ratingRead("3",1,"7",2,"12",3,"16",4,"18",5));
        RATING_READ.put("usk", ratingRead("0",1,"6",2,"12",3,"16",4,"18",5));

        RATING_WRITE.put("BBFC", parental("bbfc","pg","u","12","15","18"));
        RATING_WRITE.put("CERO", parental("cero","a","b","c","d","z","demo","pending"));
        RATING_WRITE.put("DEJUS", parental("dejus","l","10","12","14","16","18"));
        RATING_WRITE.put("ELSPA", parental("elspa","3","11","15","18"));
        RATING_WRITE.put("ESRA", parental("esra","3","7","12","15","18"));
        RATING_WRITE.put("ESRB", parental("esrb","3","e","10","teen","17","18"));
        RATING_WRITE.put("OFLC", parental("ofcl","e","g","pg","m","ma","r","x")); // "ofcl" typo kept - matches the file this app has always written
        RATING_WRITE.put("PEGI", parental("pegi","3","7","12","16","18"));
        RATING_WRITE.put("USK", parental("usk","0","6","12","16","18"));
    }
    private static Map<String, Integer> ratingRead(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) { m.put((String) kv[i], (Integer) kv[i + 1]); }
        return m;
    }
    private static String[] parental(String sys, String... vals) {
        String[] out = new String[vals.length];
        for (int i = 0; i < vals.length; i++) { out[i] = "Parental=" + sys + "/" + vals[i]; }
        return out;
    }

    private static final String[] GENRES = {"Action","Adventure","Arcade","Board","Cards","Compilation","Dance","Exercise","Fighting","First-Person Shooter","Horror","Hunting","Miscellaneous","Music","Open World","Pinball","Platform","Puzzle","Racing","RPG","Sci-Fi","Shooter","Simulation","Sport","Strategy","Survival","Third-Person Action","Third-Person Shooter","Turn-Based Strategy","Visual Novel"};
    private static final String[] PLAYERS = {"Not Set","1 Player","2 Player","3 Player","4 Player"};
    private static final String[] VMODES = {"Not Set","PAL","NTSC","MULTI"};
    private static final String[] ASPECTS = {"Not Set","Standard - (4:3)","Widescreen - (16:9)","Widescreen - (ps2rd Hack)","Widescreen - (HEX ISO HACK)"};
    private static final String[] SCANS = {"Not Set","240p - (Default For Some NTSC Games)","240p - (HEX ISO HACK)","480i - (Default For NTSC Games)","480p - (Can Be Set in Game's Settings)","480p - (Press Triangle and Cross)","480p - (Press Circle and Cross)","480p - (GSM Settings)","480p - (ps2rd Hack)","480p - (HEX ISO HACK)","576i - (Default For PAL Games)","576p - (GSM Setting)","720p - (GSM Setting)","1080i - (Can Be Set in Game's Settings)","1080i - (GSM Settings)","1080p - (GSM Settings)"};
    private static final String[] CHEATS = {" ","Codebreaker","ps2rd"};
    private static final String[] GSM_VMODES = {"NTSC","NTSC Non Interlaced","PAL","PAL Non Interlaced","PAL @60hz","PAL @60hz Non Interlaced","PS1 NTSC (HDTV 480p @60hz)","PS1 PAL (HDTV 576p @50hz)","HDTV 480p @60hz","HDTV 576p @50hz","HDTV 720p @60hz","HDTV 1080i @60hz","HDTV 1080i @60hz Non Interlaced","HDTV 1080p @60hz","VGA 640x480p @60hz","VGA 640x480p @72hz","VGA 640x480p @75hz","VGA 640x480p @85hz","VGA 640x480i @60hz"};
    private static final String[] RATING_SYSTEMS = {"BBFC","CERO","DEJUS","ELSPA","ESRA","ESRB","OFLC","PEGI","USK"};

    private static final String[] SCAN_CODES = {null,"240p","240p1","480i","480p","480p1","480p2","480p3","480p4","480p5","576i","576p","720p","1080i","1080i2","1080p"};
    private static final String[] ASPECT_CODES = {null,"s","w","w1","w2"};

    private static final String[] GLOBAL_CONFIG_KEYS = {"$EnableGSM","$GSMVMode","$GSMXOffset","$GSMYOffset","$GSMFIELDFix","$EnableCheat"};

    @FXML private TextField gameNumberField, gameTitleField, gameIdField, developerField, releaseDateField, notesField, vmc0Field, vmc1Field;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> genreCombo, playersCombo, vModeCombo, aspectRatioCombo, scanCombo, cheatCombo, gsmVModeCombo, ratingSystemCombo;
    @FXML private CheckBox vmc0Check, vmc1Check, cheatEnabledCheck, usbCheck, ethCheck, hddCheck,
            mode1Check, mode2Check, mode3Check, mode4Check, mode5Check, mode6Check, mode7Check, mode8Check,
            gsmEnabledCheck, gsmSkipVideosCheck;
    @FXML private Spinner<Integer> hPosSpinner, vPosSpinner;
    @FXML private RadioButton gsmPerGameRadio, gsmGlobalRadio, cheatPerGameRadio, cheatGlobalRadio, pademuPerGameRadio, pademuGlobalRadio;
    @FXML private ImageView star1, star2, star3, star4, star5, ratingImageView;
    @FXML private Label personalRatingLabel;
    @FXML private Button prevButton, nextButton, newConfigButton, downloadConfigButton, saveConfigButton, deleteConfigButton, ratingIncreaseButton, ratingDecreaseButton;

    private Stage stage;
    private final GameConfigFileManager configManager = new GameConfigFileManager();
    private final List<Game> gameList = new ArrayList<>();
    private int currentListIndex;

    private boolean guiActive = false;
    private boolean userRatingSet = false;
    private int userRatingValue = 0;
    private int parentalRating = 0;

    private final String[] newConfigData = new String[27];
    private final String[] newGlobalConfigData = new String[6];
    private String[] loadedGameConfigData = new String[27];
    private String[] loadedGlobalConfigData = new String[6];

    private ImageView[] stars;
    private Image starActiveImage, starInactiveImage;

    // ---------------------------------------------------------------- setup

    @FXML
    private void initialize() {
        genreCombo.setItems(FXCollections.observableArrayList(GENRES));
        playersCombo.setItems(FXCollections.observableArrayList(PLAYERS));
        vModeCombo.setItems(FXCollections.observableArrayList(VMODES));
        aspectRatioCombo.setItems(FXCollections.observableArrayList(ASPECTS));
        scanCombo.setItems(FXCollections.observableArrayList(SCANS));
        cheatCombo.setItems(FXCollections.observableArrayList(CHEATS));
        gsmVModeCombo.setItems(FXCollections.observableArrayList(GSM_VMODES));
        ratingSystemCombo.setItems(FXCollections.observableArrayList(RATING_SYSTEMS));
        genreCombo.getSelectionModel().selectFirst();
        playersCombo.getSelectionModel().selectFirst();
        vModeCombo.getSelectionModel().selectFirst();
        aspectRatioCombo.getSelectionModel().selectFirst();
        scanCombo.getSelectionModel().selectFirst();
        cheatCombo.getSelectionModel().selectFirst();
        gsmVModeCombo.getSelectionModel().selectFirst();
        ratingSystemCombo.getSelectionModel().selectFirst();

        hPosSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-4096, 4096, 0));
        vPosSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-4096, 4096, 0));

        ToggleGroup gsmSource = new ToggleGroup();
        gsmPerGameRadio.setToggleGroup(gsmSource);
        gsmGlobalRadio.setToggleGroup(gsmSource);
        ToggleGroup cheatSource = new ToggleGroup();
        cheatPerGameRadio.setToggleGroup(cheatSource);
        cheatGlobalRadio.setToggleGroup(cheatSource);
        ToggleGroup pademuSource = new ToggleGroup();
        pademuPerGameRadio.setToggleGroup(pademuSource);
        pademuGlobalRadio.setToggleGroup(pademuSource);

        // Flipping GSM/Cheat source re-shows the correct value set (this game's copy vs conf_game.cfg's).
        gsmSource.selectedToggleProperty().addListener((o, ov, nv) -> {
            if (gsmPerGameRadio.isSelected()) {
                populateGSMValues(loadedGameConfigData[18], loadedGameConfigData[19], loadedGameConfigData[20], loadedGameConfigData[21], loadedGameConfigData[22]);
            } else {
                populateGSMValues(loadedGlobalConfigData[0], loadedGlobalConfigData[1], loadedGlobalConfigData[2], loadedGlobalConfigData[3], loadedGlobalConfigData[4]);
            }
        });
        cheatSource.selectedToggleProperty().addListener((o, ov, nv) -> {
            cheatEnabledCheck.setSelected(cheatPerGameRadio.isSelected() ? loadedGameConfigData[12] != null : loadedGlobalConfigData[5] != null);
        });

        stars = new ImageView[] {star1, star2, star3, star4, star5};
        starActiveImage = loadImage(STAR_ACTIVE);
        starInactiveImage = loadImage(STAR_INACTIVE);
        for (int i = 0; i < 5; i++) {
            final int level = i + 1;
            ImageView s = stars[i];
            s.setImage(starInactiveImage);
            s.setOnMouseEntered(e -> mouseEnterStar(level));
            s.setOnMouseExited(e -> mouseExitStar(level));
            s.setOnMouseClicked(e -> mouseClickStar(level));
        }
    }

    @Override
    public void stageReady(Stage stage) {
        this.stage = stage;
        stage.setTitle(" Manage Game Config");
        stage.setOnCloseRequest(e -> {
            composeGameConfigData();
            Game g = gameList.get(currentListIndex);
            if (configManager.gameConfigExists(g.getGameID(), g.getGameName()) && !compareConfigData()) {
                if (confirm("The config data has been modified. \n\nDo you want to save the file?", " Config data not saved!")) {
                    writeGameConfigFile();
                }
            }
        });
        stage.setOnHidden(e -> PopsGameManager.callbackToUpdateGUIGameList(null, currentListIndex));
    }

    /** Called from the façade before the window is shown. */
    void init(int gameIndex) {
        List<Game> src = PopsGameManager.getCurrentConsole() == Console.PS1
                ? GameListManager.getGameListPS1() : GameListManager.getGameListPS2();
        if (src != null) { gameList.addAll(src); }
        Game g = gameList.get(Math.max(0, gameIndex));
        initialiseGUI(gameIndex, configManager.gameConfigExists(g.getGameID(), g.getGameName()));
    }

    // ------------------------------------------------------- initialise / clear

    private void initialiseGUI(int gameIndex, boolean configExists) {
        if (gameIndex < 0) { gameIndex = 0; }

        // conf_game.cfg is shared by every game - re-read it fresh each time.
        loadedGlobalConfigData = configManager.readGlobalConfig();

        currentListIndex = gameIndex;
        clearGUI();
        Game g = gameList.get(currentListIndex);
        gameTitleField.setText(" " + g.getGameName());
        gameIdField.setText(g.getGameID());
        gameNumberField.setText("[" + (currentListIndex + 1) + "/" + gameList.size() + "]");
        renderStars(0);

        if (configExists) {
            guiEnabled(true);
            try {
                displayGameConfigDetails(configManager.readGameConfigFormatted(g.getGameID(), g.getGameName()));
            } catch (IOException ex) {
                PopsGameManager.displayErrorMessageDebug("Error reading the game config file!\n\n" + ex.toString());
            }
        } else {
            guiEnabled(false);
        }
    }

    private void clearGUI() {
        vModeCombo.getSelectionModel().select(0);
        aspectRatioCombo.getSelectionModel().select(0);
        scanCombo.getSelectionModel().select(0);
        genreCombo.getSelectionModel().select(0);
        playersCombo.getSelectionModel().select(0);
        gsmVModeCombo.getSelectionModel().select(0);
        hPosSpinner.getValueFactory().setValue(0);
        vPosSpinner.getValueFactory().setValue(0);
        cheatCombo.getSelectionModel().select(0);
        developerField.setText("");
        releaseDateField.setText("");
        notesField.setText("");
        descriptionArea.setText("");
        vmc0Field.setText("");
        vmc1Field.setText("");
        for (CheckBox c : new CheckBox[] {mode1Check, mode2Check, mode3Check, mode4Check, mode5Check, mode6Check, mode7Check, mode8Check,
                vmc0Check, vmc1Check, ethCheck, usbCheck, hddCheck, gsmEnabledCheck, cheatEnabledCheck, gsmSkipVideosCheck}) {
            c.setSelected(false);
        }
        vmc0Field.setEditable(false);
        vmc1Field.setEditable(false);
        setRatingImage(NO_RATING);

        gsmGlobalRadio.setSelected(true);
        cheatGlobalRadio.setSelected(true);
        pademuGlobalRadio.setSelected(true);
        loadedGameConfigData = new String[27];
        parentalRating = 0;
        userRatingSet = false;
        userRatingValue = 0;
        personalRatingLabel.setText("Personal Rating: 0");
    }

    private void guiEnabled(boolean enabled) {
        for (javafx.scene.Node n : new javafx.scene.Node[] {
                vModeCombo, aspectRatioCombo, scanCombo, genreCombo, playersCombo, cheatCombo,
                developerField, releaseDateField, notesField, descriptionArea,
                mode1Check, mode2Check, mode3Check, mode4Check, mode5Check, mode6Check, mode7Check, mode8Check,
                vmc0Check, vmc1Check, ethCheck, usbCheck, hddCheck, saveConfigButton, deleteConfigButton,
                gsmEnabledCheck, ratingSystemCombo, ratingDecreaseButton, ratingIncreaseButton,
                cheatEnabledCheck, gsmVModeCombo, gsmSkipVideosCheck, hPosSpinner, vPosSpinner,
                gsmPerGameRadio, cheatPerGameRadio, pademuPerGameRadio,
                gsmGlobalRadio, cheatGlobalRadio, pademuGlobalRadio }) {
            n.setDisable(!enabled);
        }
        guiActive = enabled;
    }

    // ------------------------------------------------------ display (config -> UI)

    private void displayGameConfigDetails(String[] configData) {
        loadedGameConfigData = configData;

        if (configData[2] != null) { genreCombo.setValue(configData[2]); }
        if (configData[3] != null) { developerField.setText(configData[3]); }
        if (configData[4] != null) { releaseDateField.setText(configData[4]); }
        if (configData[5] != null) { selectIndex(playersCombo, Integer.parseInt(configData[5])); }

        if (configData[6] != null) { mouseClickStar(Integer.parseInt(configData[6])); } else { mouseClickStar(0); }

        if (configData[7] != null) { descriptionArea.setText(configData[7]); }
        if (configData[8] != null) { notesField.setText(configData[8]); }

        if (configData[9] != null) { vmc0Check.setSelected(true); vmc0Field.setText(configData[9]); }
        if (configData[10] != null) { vmc1Check.setSelected(true); vmc1Field.setText(configData[10]); }

        if (configData[11] != null) { cheatCombo.setValue(configData[11]); }

        boolean cheatPerGame = "1".equals(configData[25]);
        (cheatPerGame ? cheatPerGameRadio : cheatGlobalRadio).setSelected(true);
        cheatEnabledCheck.setSelected(cheatPerGame ? configData[12] != null : loadedGlobalConfigData[5] != null);

        if (configData[13] != null) { checkCompatibleDevices(configData[13]); }
        if (configData[14] != null) { vModeCombo.setValue(configData[14].toUpperCase(Locale.ENGLISH)); }
        if (configData[15] != null) { checkAspectRatio(configData[15]); }
        if (configData[16] != null) { checkScan(configData[16]); }
        if (configData[17] != null) { readCompatibilityMode(configData[17]); }

        boolean gsmPerGame = "1".equals(configData[24]);
        (gsmPerGame ? gsmPerGameRadio : gsmGlobalRadio).setSelected(true);
        if (gsmPerGame) {
            populateGSMValues(configData[18], configData[19], configData[20], configData[21], configData[22]);
        } else {
            populateGSMValues(loadedGlobalConfigData[0], loadedGlobalConfigData[1], loadedGlobalConfigData[2], loadedGlobalConfigData[3], loadedGlobalConfigData[4]);
        }

        (("1".equals(configData[26])) ? pademuPerGameRadio : pademuGlobalRadio).setSelected(true);

        if (configData[23] != null) {
            String[] parts = configData[23].split("/");
            String ratingSystem = parts[0];
            String ratingValue = parts[1];
            if (ratingSystem.equals("ofcl")) { ratingSystem = "oflc"; }
            ratingSystemCombo.setValue(ratingSystem.toUpperCase(Locale.ENGLISH));
            switchParentRatingValue(ratingSystem, ratingValue);
            updateParentalRatingImage();
        }
    }

    private void populateGSMValues(String enableGSM, String gsmVMode, String gsmXOffset, String gsmYOffset, String gsmFieldFix) {
        boolean on = enableGSM != null;
        gsmEnabledCheck.setSelected(on);
        gsmVModeCombo.setDisable(!on);
        gsmSkipVideosCheck.setDisable(!on);
        hPosSpinner.setDisable(!on);
        vPosSpinner.setDisable(!on);

        selectIndex(gsmVModeCombo, gsmVMode != null ? Integer.parseInt(gsmVMode) : 0);
        hPosSpinner.getValueFactory().setValue(gsmXOffset != null ? Integer.parseInt(gsmXOffset) : 0);
        vPosSpinner.getValueFactory().setValue(gsmYOffset != null ? Integer.parseInt(gsmYOffset) : 0);
        gsmSkipVideosCheck.setSelected(gsmFieldFix != null);
    }

    // ------------------------------------------------------ compose (UI -> config)

    private void composeGameConfigData() {
        Arrays.fill(newConfigData, null);
        Arrays.fill(newGlobalConfigData, null);

        Game game = gameList.get(currentListIndex);
        newConfigData[0] = "CfgVersion=5";
        newConfigData[1] = "Title=" + game.getGameName();

        if (genreCombo.getSelectionModel().getSelectedIndex() != 0 || !" ".equals(genreCombo.getValue())) {
            newConfigData[2] = "Genre=" + genreCombo.getValue();
        }
        if (!"".equals(developerField.getText())) { newConfigData[3] = "Developer=" + developerField.getText(); }
        if (!"".equals(releaseDateField.getText())) { newConfigData[4] = "Release=" + releaseDateField.getText(); }
        if (playersCombo.getSelectionModel().getSelectedIndex() != 0) {
            newConfigData[5] = "Players=players/" + truncate(playersCombo.getValue(), 1);
        }
        if (userRatingValue != 0) { newConfigData[6] = "Rating=rating/" + userRatingValue; }
        if (!"".equals(descriptionArea.getText())) { newConfigData[7] = "Description=" + descriptionArea.getText(); }
        if (!"".equals(notesField.getText())) { newConfigData[8] = "Notes=" + notesField.getText(); }
        if (vmc0Check.isSelected()) { newConfigData[9] = "$VMC_0=" + vmc0Field.getText(); }
        if (vmc1Check.isSelected()) { newConfigData[10] = "$VMC_1=" + vmc1Field.getText(); }

        if (cheatCombo.getSelectionModel().getSelectedIndex() != 0 || !" ".equals(cheatCombo.getValue())) {
            newConfigData[11] = "Cheat=" + cheatCombo.getValue();
        }

        boolean cheatPerGame = cheatPerGameRadio.isSelected();
        if (cheatPerGame) { newConfigData[25] = "$CheatsSource=1"; }
        String cheatEnabledLine = cheatEnabledCheck.isSelected() ? "$EnableCheat=1" : null;
        if (cheatPerGame) {
            newConfigData[12] = cheatEnabledLine;
            newGlobalConfigData[5] = preserveGlobalValue(5);
        } else {
            newGlobalConfigData[5] = cheatEnabledLine;
        }

        if (usbCheck.isSelected() || ethCheck.isSelected() || hddCheck.isSelected()) {
            newConfigData[13] = "Device=device/" + convertCompatibleDevices();
        }
        if (vModeCombo.getSelectionModel().getSelectedIndex() != 0) {
            newConfigData[14] = "Vmode=vmode/" + vModeCombo.getValue().toLowerCase(Locale.ENGLISH);
        }
        if (aspectRatioCombo.getSelectionModel().getSelectedIndex() != 0) {
            newConfigData[15] = "Aspect=aspect/" + ASPECT_CODES[aspectRatioCombo.getSelectionModel().getSelectedIndex()];
        }
        if (scanCombo.getSelectionModel().getSelectedIndex() != 0) {
            newConfigData[16] = "Scan=scan/" + SCAN_CODES[scanCombo.getSelectionModel().getSelectedIndex()];
        }
        if (compatibilityModeSelected()) { newConfigData[17] = "$Compatibility=" + convertCompatibilityMode(); }

        boolean gsmPerGame = gsmPerGameRadio.isSelected();
        if (gsmPerGame) { newConfigData[24] = "$GSMSource=1"; }
        String gsmEnabledLine = gsmEnabledCheck.isSelected() ? "$EnableGSM=1" : null;
        String gsmVModeLine = gsmVModeCombo.getSelectionModel().getSelectedIndex() != 0 ? "$GSMVMode=" + gsmVModeCombo.getSelectionModel().getSelectedIndex() : null;
        String gsmXOffsetLine = !hPosSpinner.getValue().toString().equals("0") ? "$GSMXOffset=" + hPosSpinner.getValue() : null;
        String gsmYOffsetLine = !vPosSpinner.getValue().toString().equals("0") ? "$GSMYOffset=" + vPosSpinner.getValue() : null;
        String gsmFieldFixLine = gsmSkipVideosCheck.isSelected() ? "$GSMFIELDFix=1" : null;
        if (gsmPerGame) {
            newConfigData[18] = gsmEnabledLine;
            newConfigData[19] = gsmVModeLine;
            newConfigData[20] = gsmXOffsetLine;
            newConfigData[21] = gsmYOffsetLine;
            newConfigData[22] = gsmFieldFixLine;
            for (int i = 0; i < 5; i++) { newGlobalConfigData[i] = preserveGlobalValue(i); }
        } else {
            newGlobalConfigData[0] = gsmEnabledLine;
            newGlobalConfigData[1] = gsmVModeLine;
            newGlobalConfigData[2] = gsmXOffsetLine;
            newGlobalConfigData[3] = gsmYOffsetLine;
            newGlobalConfigData[4] = gsmFieldFixLine;
        }

        if (pademuPerGameRadio.isSelected()) { newConfigData[26] = "$PADEMUSource=1"; }

        if (parentalRating != 0) { newConfigData[23] = convertParentRatingValue(); }
    }

    private String preserveGlobalValue(int index) {
        return loadedGlobalConfigData[index] != null ? GLOBAL_CONFIG_KEYS[index] + "=" + loadedGlobalConfigData[index] : null;
    }

    private void writeGameConfigFile() {
        Game g = gameList.get(currentListIndex);
        configManager.writeGameConfigFile(newConfigData, g.getGameID(), g.getGameName());
        configManager.writeGlobalConfig(newGlobalConfigData);
        checkForVMC();
    }

    private boolean compareConfigData() {
        boolean identical = false;
        try {
            Game g = gameList.get(currentListIndex);
            String[] stored = configManager.readGameConfigRaw(g.getGameID(), g.getGameName());
            if (stored != null && newConfigData.length == stored.length) {
                identical = configManager.compareGameConfig(newConfigData, stored);
            }
        } catch (IOException ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
        return identical && !globalConfigChanged();
    }

    private boolean globalConfigChanged() {
        for (int i = 0; i < newGlobalConfigData.length; i++) {
            String newLine = newGlobalConfigData[i];
            String newValue = newLine != null ? newLine.substring(GLOBAL_CONFIG_KEYS[i].length() + 1) : null;
            String oldValue = loadedGlobalConfigData[i];
            if (newValue == null ? oldValue != null : !newValue.equals(oldValue)) { return true; }
        }
        return false;
    }

    // ---------------------------------------------------------------- VMC

    private void checkForVMC() {
        String mc0 = null;
        String mc1 = null;
        for (String item : newConfigData) {
            if (item != null && item.length() > 7) {
                if (item.startsWith("$VMC_0")) { mc0 = item.substring(7); }
                if (item.startsWith("$VMC_1")) { mc1 = item.substring(7); }
            }
        }
        if (mc0 != null && !memoryCardExists(mc0)) { generateVMC(mc0); }
        if (mc1 != null && !memoryCardExists(mc1)) { generateVMC(mc1); }
    }

    private boolean memoryCardExists(String name) {
        String loc;
        if (PopsGameManager.getCurrentConsole() == Console.PS1) {
            loc = PopsGameManager.getOPLFolder() + File.separator + "POPS" + File.separator
                    + gameList.get(currentListIndex).getGameName() + File.separator + name + ".bin";
        } else {
            loc = PopsGameManager.getOPLFolder() + File.separator + "VMC" + File.separator + name + ".bin";
        }
        File f = new File(loc);
        return f.exists() && !f.isDirectory();
    }

    private void generateVMC(String vmcName) {
        if (PopsGameManager.getCurrentConsole() != Console.PS2) { return; }
        File vmcFolder = new File(PopsGameManager.getOPLFolder() + File.separator + "VMC");
        if (!vmcFolder.isDirectory()) { vmcFolder.mkdir(); }

        String appFolder = "windows";
        String appName = "genvmc.exe";
        if (PopsGameManager.getOSType().equals("Linux") || PopsGameManager.getOSType().equals("Mac")) {
            appFolder = "linux";
            appName = "genvmc";
        }
        List<String> commands = new ArrayList<>();
        commands.add(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "tools" + File.separator + appFolder + File.separator + appName);
        commands.add("8");
        commands.add(PopsGameManager.getOPLFolder() + File.separator + "VMC" + File.separator + vmcName + ".bin");
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.directory(new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "tools" + File.separator + appFolder));
        try {
            pb.start().waitFor();
        } catch (IOException | InterruptedException ex) {
            PopsGameManager.displayErrorMessageDebug("Error launching genvmc!\n\n" + ex.toString());
        }
    }

    // ----------------------------------------------------------- converters

    private String convertCompatibilityMode() {
        int[] bits = new int[8];
        CheckBox[] modes = {mode1Check, mode2Check, mode3Check, mode4Check, mode5Check, mode6Check, mode7Check, mode8Check};
        for (int i = 0; i < 8; i++) { bits[i] = modes[i].isSelected() ? 1 : 0; }
        StringBuilder binary = new StringBuilder();
        for (int b : bits) { binary.append(b); }
        int decimal = Integer.parseInt(binary.reverse().toString(), 2);
        return Integer.toString(decimal);
    }

    private boolean compatibilityModeSelected() {
        for (CheckBox c : new CheckBox[] {mode1Check, mode2Check, mode3Check, mode4Check, mode5Check, mode6Check, mode7Check, mode8Check}) {
            if (c.isSelected()) { return true; }
        }
        return false;
    }

    private void readCompatibilityMode(String compatibilityMode) {
        int mode = Integer.parseInt(compatibilityMode);
        String binary = new StringBuilder(Integer.toString(mode, 2)).reverse().toString();
        CheckBox[] modes = {mode1Check, mode2Check, mode3Check, mode4Check, mode5Check, mode6Check, mode7Check, mode8Check};
        for (int i = 0; i < binary.length() && i < 8; i++) {
            if (binary.charAt(i) == '1') { modes[i].setSelected(true); }
        }
    }

    private String convertCompatibleDevices() {
        boolean u = usbCheck.isSelected();
        boolean e = ethCheck.isSelected();
        boolean h = hddCheck.isSelected();
        if (u && e && h) { return "all"; }
        if (!u && !e && h) { return "6"; }
        if (!u && e && !h) { return "5"; }
        if (!u && e && h) { return "4"; }
        if (u && !e && h) { return "3"; }
        if (u && e && !h) { return "2"; }
        if (u && !e && !h) { return "1"; }
        return null;
    }

    private void checkCompatibleDevices(String code) {
        switch (code) {
            case "1": usbCheck.setSelected(true); break;
            case "2": usbCheck.setSelected(true); ethCheck.setSelected(true); break;
            case "3": usbCheck.setSelected(true); hddCheck.setSelected(true); break;
            case "4": hddCheck.setSelected(true); ethCheck.setSelected(true); break;
            case "5": ethCheck.setSelected(true); break;
            case "6": hddCheck.setSelected(true); break;
            case "all": usbCheck.setSelected(true); hddCheck.setSelected(true); ethCheck.setSelected(true); break;
            default: break;
        }
    }

    private void checkAspectRatio(String code) {
        for (int i = 1; i < ASPECT_CODES.length; i++) {
            if (ASPECT_CODES[i].equals(code)) { aspectRatioCombo.getSelectionModel().select(i); return; }
        }
    }

    private void checkScan(String code) {
        for (int i = 1; i < SCAN_CODES.length; i++) {
            if (SCAN_CODES[i].equals(code)) { scanCombo.getSelectionModel().select(i); return; }
        }
    }

    // --------------------------------------------------------- parental rating

    private void switchParentRatingValue(String ratingSystem, String ratingValue) {
        Map<String, Integer> table = RATING_READ.get(ratingSystem);
        if (table != null && table.containsKey(ratingValue)) { parentalRating = table.get(ratingValue); }
    }

    private String convertParentRatingValue() {
        String system = ratingSystemCombo.getValue();
        String[] table = RATING_WRITE.get(system);
        if (table != null && parentalRating >= 1 && parentalRating <= table.length) {
            return table[parentalRating - 1];
        }
        return "";
    }

    private void updateParentalRatingImage() {
        String[] images = RATING_IMAGE.get(ratingSystemCombo.getValue());
        if (images != null && parentalRating >= 1 && parentalRating <= images.length) {
            setRatingImage(ratingImg(images[parentalRating - 1]));
        }
    }

    private void setRatingImage(String path) {
        Image i = loadImage(path);
        if (i != null) { ratingImageView.setImage(i); }
    }

    // ----------------------------------------------------------- star widget

    private void renderStars(int n) {
        for (int i = 0; i < 5; i++) {
            stars[i].setImage(i < n ? starActiveImage : starInactiveImage);
        }
    }

    private void mouseEnterStar(int starNumber) {
        if (guiActive) { renderStars(starNumber); }
    }

    private void mouseExitStar(int starNumber) {
        if (guiActive) { renderStars(userRatingSet ? userRatingValue : 0); }
    }

    private void mouseClickStar(int starNumber) {
        if (guiActive) {
            renderStars(starNumber);
            personalRatingLabel.setText("Personal Rating: " + starNumber);
        }
        userRatingSet = starNumber != 0;
        userRatingValue = starNumber;
    }

    // ---------------------------------------------------------------- buttons

    @FXML
    private void onPrev() {
        composeGameConfigData();
        promptSaveIfModified();
        if (currentListIndex > 0) {
            currentListIndex--;
            Game g = gameList.get(currentListIndex);
            initialiseGUI(currentListIndex, configManager.gameConfigExists(g.getGameID(), g.getGameName()));
        }
    }

    @FXML
    private void onNext() {
        composeGameConfigData();
        promptSaveIfModified();
        if (currentListIndex < gameList.size() - 1) {
            currentListIndex++;
            Game g = gameList.get(currentListIndex);
            initialiseGUI(currentListIndex, configManager.gameConfigExists(g.getGameID(), g.getGameName()));
        }
    }

    private void promptSaveIfModified() {
        Game g = gameList.get(currentListIndex);
        if (configManager.gameConfigExists(g.getGameID(), g.getGameName()) && !compareConfigData()) {
            if (confirm("The config data has been modified. \n\nDo you want to save the file?", " Config data not saved!")) {
                writeGameConfigFile();
            }
        }
    }

    @FXML
    private void onSaveConfig() {
        composeGameConfigData();
        writeGameConfigFile();
    }

    @FXML
    private void onDownloadConfig() {
        Game g = gameList.get(currentListIndex);
        String region = PopsGameManager.determineGameRegion(g.getGameID().split("_")[0]);
        BackendClient api = PopsGameManager.newBackendClient();
        api.getConfigFromServer(region, g.getGameID(), g.getGameName(), false);
        initialiseGUI(currentListIndex, configManager.gameConfigExists(g.getGameID(), g.getGameName()));
    }

    @FXML
    private void onNewConfig() {
        Game g = gameList.get(currentListIndex);
        try (PrintWriter writer = new PrintWriter(PopsGameManager.getOPLFolder() + "CFG" + File.separator + g.getGameID() + ".cfg", "UTF-8")) {
            writer.println("CfgVersion=5");
            writer.println("Title=" + g.getGameName());
        } catch (Exception ex) {
            PopsGameManager.displayErrorMessageDebug(ex.toString());
        }
        guiEnabled(true);
    }

    @FXML
    private void onDeleteConfig() {
        if (!confirm("Are you sure that you want to delete the current config file?", " Delete Config!")) { return; }
        Game g = gameList.get(currentListIndex);
        new File(PopsGameManager.getOPLFolder() + "CFG" + File.separator + g.getGameID() + ".cfg").delete();
        clearGUI();
        initialiseGUI(currentListIndex, configManager.gameConfigExists(g.getGameID(), g.getGameName()));
    }

    @FXML
    private void onRatingSystemChanged() {
        parentalRating = 0;
        setRatingImage(NO_RATING);
    }

    @FXML
    private void onRatingIncrease() {
        String[] table = RATING_WRITE.get(ratingSystemCombo.getValue());
        if (table != null && parentalRating < table.length) { parentalRating++; }
        updateParentalRatingImage();
    }

    @FXML
    private void onRatingDecrease() {
        if (parentalRating > 1) { parentalRating--; }
        updateParentalRatingImage();
    }

    @FXML
    private void onVmc0() {
        if (PopsGameManager.getCurrentConsole() != Console.PS2) {
            vmc0Check.setSelected(false);
            alert(Alert.AlertType.ERROR, "This application cannot yet generate PS1 Virtual Memory Cards.", " Unable to Upload PS1 Cheat File!");
            return;
        }
        if (vmc0Check.isSelected()) {
            vmc0Field.setEditable(true);
            vmc0Field.setText(gameList.get(currentListIndex).getGameID() + "_0");
        } else {
            vmc0Field.setEditable(false);
            vmc0Field.setText("");
        }
    }

    @FXML
    private void onVmc1() {
        if (PopsGameManager.getCurrentConsole() != Console.PS2) {
            vmc1Check.setSelected(false);
            alert(Alert.AlertType.ERROR, "This application cannot yet generate PS1 Virtual Memory Cards.", " Unable to Upload PS1 Cheat File!");
            return;
        }
        if (vmc1Check.isSelected()) {
            vmc1Field.setEditable(true);
            vmc1Field.setText(gameList.get(currentListIndex).getGameID() + "_1");
        } else {
            vmc1Field.setEditable(false);
            vmc1Field.setText("");
        }
    }

    @FXML
    private void onGsmEnabled() {
        boolean on = gsmEnabledCheck.isSelected();
        gsmSkipVideosCheck.setDisable(!on);
        gsmVModeCombo.setDisable(!on);
        hPosSpinner.setDisable(!on);
        vPosSpinner.setDisable(!on);
    }

    // ---------------------------------------------------------------- helpers

    private static String truncate(String value, int length) {
        return value.length() > length ? value.substring(0, length) : value;
    }

    private static void selectIndex(ComboBox<String> combo, int index) {
        if (index >= 0 && index < combo.getItems().size()) { combo.getSelectionModel().select(index); }
    }

    private static Image loadImage(String path) {
        File f = new File(path);
        if (!f.isFile()) { return null; }
        try {
            return new Image(f.toURI().toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean confirm(String message, String title) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle(title);
        if (stage != null) { alert.initOwner(stage); }
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void alert(Alert.AlertType type, String message, String title) {
        Alert alert = new Alert(type, message);
        alert.setHeaderText(null);
        alert.setTitle(title);
        if (stage != null) { alert.initOwner(stage); }
        alert.showAndWait();
    }
}

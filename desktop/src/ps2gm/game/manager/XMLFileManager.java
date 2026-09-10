package ps2gm.game.manager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class XMLFileManager {
    
    private static final File settingsXMLFile = new File(PopsGameManager.getCurrentDirectory() + File.separator + "settings.xml");
    private static final File settingsFile = new File(PopsGameManager.getCurrentDirectory() + File.separator + "ps2gm-settings");
    private static final File keyFileSettings = new File(PopsGameManager.getCurrentDirectory() + File.separator + "lib" + File.separator + "data" + File.separator + "data_3");

    // One-time migration: the settings file used to be called "oplpops-settings". If an old one
    // is still sitting next to the app and the new "ps2gm-settings" isn't, rename it so upgraders
    // keep their saved mode/paths instead of having to set everything again.
    static {
        try {
            File legacySettings = new File(PopsGameManager.getCurrentDirectory() + File.separator + "oplpops-settings");
            if (legacySettings.isFile() && !settingsFile.exists()) { legacySettings.renameTo(settingsFile); }
        } catch (Exception ignored) { /* best-effort - a fresh setup just starts with no settings */ }
    }

    public  XMLFileManager() {}

    // Creates a text element under root, e.g. <tag>value</tag>
    private static void appendText(Document doc, Element root, String tag, String value) {
        Element el = doc.createElement(tag);
        el.appendChild(doc.createTextNode(value));
        root.appendChild(el);
    }

    // Reads a single text element's content, e.g. <tag>value</tag> -> "value"
    // Throws NullPointerException (caught by the caller) if the tag is missing
    private static String text(Element root, String tag) {
        return root.getElementsByTagName(tag).item(0).getTextContent();
    }

    // Applies a boolean element's value if it's exactly "true" or "false", otherwise
    // leaves the setting unchanged (matches how this file has always tolerated garbage)
    private static void applyBoolean(String text, Consumer<Boolean> setter) {
        if ("false".equals(text)) {setter.accept(false);}
        else if ("true".equals(text)) {setter.accept(true);}
    }

    // This writes the settings to the settings.xml file
    public static void writeSettingsXML() throws TransformerException, ParserConfigurationException{

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // Root elements
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("settings");
        doc.appendChild(rootElement);

        appendText(doc, rootElement, "consoleaddress", PopsGameManager.getPS2IP());
        appendText(doc, rootElement, "oplfolder", PopsGameManager.getOPLFolder());
        appendText(doc, rootElement, "currentconsole", PopsGameManager.getCurrentConsole().name());
        appendText(doc, rootElement, "currentmode", PopsGameManager.getCurrentMode().name());
        appendText(doc, rootElement, "useemulatorps2", PopsGameManager.getEmulatorInUsePS2().toString());
        appendText(doc, rootElement, "emulatorpathps2", PopsGameManager.getEmulatorPathPS2());
        appendText(doc, rootElement, "emulatorfullps2", PopsGameManager.getEmulatorFullScreenPS2().toString());
        appendText(doc, rootElement, "useemulatorps1", PopsGameManager.getEmulatorInUsePS1().toString());
        appendText(doc, rootElement, "emulatorpathps1", PopsGameManager.getEmulatorPathPS1());
        appendText(doc, rootElement, "emulatorfullps1", PopsGameManager.getEmulatorFullScreenPS1().toString());
        appendText(doc, rootElement, "remotevcdpath", PopsGameManager.getRemoteVCDPath());
        appendText(doc, rootElement, "remoteelfpath", PopsGameManager.getRemoteELFPath());
        appendText(doc, rootElement, "remoteoplpath", PopsGameManager.getRemoteOPLPath());
        appendText(doc, rootElement, "compatabilityps1", PopsGameManager.getGameCompatabilityPS1().toString());
        appendText(doc, rootElement, "splitgamesps2", PopsGameManager.getSplitGameDisplayPS2().toString());
        appendText(doc, rootElement, "darkmode", PopsGameManager.getDarkMode().toString()); // kept for older builds that still read it
        appendText(doc, rootElement, "theme", PopsGameManager.getThemeName());

        // Write the contents to the xml file
        try {
            Transformer tr = TransformerFactory.newInstance().newTransformer();
            tr.setOutputProperty(OutputKeys.INDENT, "yes");
            tr.setOutputProperty(OutputKeys.METHOD, "xml");
            tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            tr.setOutputProperty(OutputKeys.STANDALONE, "yes");
            tr.transform(new DOMSource(doc), new StreamResult(settingsXMLFile));
            
            encryptSettingsFile();
            
        } catch (TransformerException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
    }
    
    // This gets the values from the settings.xml file
    public static void readSettingsXML(){

        decryptSettingsFile();
        
        if (settingsXMLFile.exists() && settingsXMLFile.isFile()){
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = null;
            try {dBuilder = dbFactory.newDocumentBuilder();} 
            catch (ParserConfigurationException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}

            if (dBuilder != null) {

                Document doc = null;
                try {doc = dBuilder.parse(settingsXMLFile);} 
                catch (SAXException ex) {ps2gm.game.manager.fx.FxAlerts.showErrorBlocking("The ps2gm-settings file appears to have been modified or moved!\n\nYou will need to set the Mode again.", " Error Loading Settings!");}
                catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}

                if (doc == null){PopsGameManager.setFisrtLaunch(true);}
                else {
                    doc.getDocumentElement().normalize();
                    NodeList nList = doc.getElementsByTagName("settings");

                    if (nList != null){

                        for (int i = 0; i < nList.getLength(); i++) {
                            Node nNode = nList.item(i);

                            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element eElement = (Element) nNode;
                                try{
                                    // General settings
                                    PopsGameManager.setPS2IP(text(eElement, "consoleaddress"));
                                    PopsGameManager.setOPLFolder(text(eElement, "oplfolder"));
                                    PopsGameManager.setCurrentMode(parseMode(text(eElement, "currentmode")));
                                    PopsGameManager.setCurrentConsole(parseConsole(text(eElement, "currentconsole")));

                                    // PS2 emulator settings
                                    applyBoolean(text(eElement, "useemulatorps2"), PopsGameManager::setEmulatorInUsePS2);
                                    PopsGameManager.setEmulatorPathPS2(text(eElement, "emulatorpathps2"));
                                    applyBoolean(text(eElement, "emulatorfullps2"), PopsGameManager::setEmulatorFullScreenPS2);

                                    // PS1 emulator settings
                                    applyBoolean(text(eElement, "useemulatorps1"), PopsGameManager::setEmulatorInUsePS1);
                                    PopsGameManager.setEmulatorPathPS1(text(eElement, "emulatorpathps1"));
                                    applyBoolean(text(eElement, "emulatorfullps1"), PopsGameManager::setEmulatorFullScreenPS1);

                                    // Remote PS2 file paths
                                    PopsGameManager.setRemoteVCDPath(text(eElement, "remotevcdpath"));
                                    PopsGameManager.setRemoteELFPath(text(eElement, "remoteelfpath"));
                                    PopsGameManager.setRemoteOPLPath(text(eElement, "remoteoplpath"));

                                    // PS1 Compatibility mode
                                    applyBoolean(text(eElement, "compatabilityps1"), PopsGameManager::setGameCompatabilityPS1);

                                    // PS2 Split game highlight
                                    applyBoolean(text(eElement, "splitgamesps2"), PopsGameManager::setSplitGameDisplayPS2);

                                    // Dark mode (optional - missing from settings files written before this was added, defaults to light)
                                    Node darkModeNode = eElement.getElementsByTagName("darkmode").item(0);
                                    if (darkModeNode != null) {
                                        applyBoolean(darkModeNode.getTextContent(), PopsGameManager::setDarkMode);
                                    }

                                    // Theme (optional
                                    Node themeNode = eElement.getElementsByTagName("theme").item(0);
                                    if (themeNode != null && !themeNode.getTextContent().isBlank()) {
                                        PopsGameManager.setThemeName(themeNode.getTextContent());
                                    } else if (PopsGameManager.getDarkMode()) {
                                        PopsGameManager.setThemeName("Primer Dark");
                                    }
                                }
                                catch(NullPointerException ex){
                                    ps2gm.game.manager.fx.FxAlerts.showErrorBlocking("The ps2gm-settings file appears to have been modified or moved!\n\nYou will need to set the Mode again.", " Error Loading Settings!");
                                    PopsGameManager.setFisrtLaunch(true);
                                }
                            }
                        }
                    }
                }
            }
            encryptSettingsFile();
        }
        else {PopsGameManager.setFisrtLaunch(true);}
    }
    
    // Parses a <currentconsole> text value, treating missing/blank/unrecognized text as unset
    // rather than throwing - the file may be from an older build or hand-edited
    private static Console parseConsole(String text) {
        try {return (text == null || text.isBlank()) ? null : Console.valueOf(text.trim());}
        catch (IllegalArgumentException ex) {return null;}
    }

    // Parses a <currentmode> text value - see parseConsole above
    private static Mode parseMode(String text) {
        try {return (text == null || text.isBlank()) ? null : Mode.valueOf(text.trim());}
        catch (IllegalArgumentException ex) {return null;}
    }

    // Encrypt the setting .xml file
    private static void encryptSettingsFile(){
        try {
            List<String> list = Files.readAllLines(settingsXMLFile.toPath(), Charset.defaultCharset());
            // Encrypt 
            FileEncryptor encryptor = new FileEncryptor();
            encryptor.EncryptData(list, settingsFile.getAbsolutePath(), keyFileSettings.getAbsolutePath());
            if (settingsXMLFile.exists() && settingsXMLFile.isFile()) {settingsXMLFile.delete();}
        } catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
    }
    
    // Decrypt the settings .xml file
    private static void decryptSettingsFile(){
        
        // Ensure that the settings file exists
        if (new File(settingsFile.getAbsolutePath()).exists() && new File(settingsFile.getAbsolutePath()).isFile()){
            try {
                // Decrypt the encrypted settings file
                FileEncryptor encryptor = new FileEncryptor();
                List<String> list = encryptor.DecryptData(settingsFile.getAbsolutePath(), keyFileSettings.getAbsolutePath());
                Files.write(Paths.get(PopsGameManager.getCurrentDirectory() + File.separator + "settings.xml"),list,Charset.defaultCharset());
            } catch (IOException ex) {PopsGameManager.displayErrorMessageDebug(ex.toString());}
        }
    }
}
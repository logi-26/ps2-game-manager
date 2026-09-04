package tcpserver;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.text.SimpleDateFormat;
import java.util.Date;

// Worker thread for handling the TCP response
public class WorkerRunnable implements Runnable{

    protected Socket clientSocket = null;
    private static final String APP_NAME = "OPLPOPS-Manager_0.5.jar";
    private static final String CURRENT_VERSION_NUMBER = "0.5";
    private static final String BUILD_DATE = "05 April 2017";

    
    public WorkerRunnable(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }


    @Override
    public void run() {
        
        try {
            DataInputStream dataInputStream;
            try (InputStream inputStream = clientSocket.getInputStream()) {
                
                dataInputStream = new DataInputStream(inputStream);
                
                // Read the data into a buffer
                int fileLength = dataInputStream.readInt();
                byte[] data = new byte[fileLength];
                if (fileLength > 0) dataInputStream.readFully(data);
                
                // Handle the message
                byte [] bytearray = handleMessage(data);
                if (bytearray != null) sendBytes(bytearray);
            }
            
            dataInputStream.close();
            clientSocket.close(); 
            

        } catch (IOException ex) {}
    }
    

    // If the respond boolean does not equal true, no response is sent to the client - (This is used to get the size of the response buffer without responding)
    private byte[] handleMessage(byte[] data){
        
        // Get the first 2 bits of the recieved data to determine if it is a number
        String messageStart = "";
        if (data.length > 2) for (int i = 0; i<2; i++) messageStart += (char)(data[i]);
        
        InetAddress IPAddress = clientSocket.getInetAddress();
        int port = clientSocket.getLocalPort();
        String[] splitMessage;
        byte[] responseBuffer = null;
        byte[] fileInputBuffer = null;
        String message = new String(data);

        try{
            int num = Integer.parseInt(messageStart);                   // Try and convert the first 2 bits to an integer
            message = message.substring(3, num);                        // Remove the string data from the image data
            fileInputBuffer = new byte[data.length - num];              // A responseBuffer to store just the image data

            int counter = 0;
            for (int i = num; i <data.length; i++){
                fileInputBuffer[counter] = data[i];
                counter ++;
            }

        } catch (NumberFormatException e) {}
        
        
        // If the message is correctly formatted
        if (message.contains(",")){
            splitMessage = message.split(",");
            String clientIP = IPAddress.toString().substring(1);
            System.out.println("\n\n(" + getCurrentTime() + ")  Client Connected: "  + clientIP + " : (" + Integer.toString(port) +  ")  Message: " + splitMessage[0]);

            //System.out.println("Split Message 0: " + splitMessage[0]);
            //System.out.println("Split Message 1: " + splitMessage[1]);
            //System.out.println("Split Message 2: " + splitMessage[2]);
            //System.out.println("Split Message 3: " + splitMessage[3]);
            //System.out.println("Split Message 4: " + splitMessage[4]);

            // Determine the appropriate response based on the message that was recieved from the client
            switch (splitMessage[0]) {
                case "ART":
                    responseBuffer = downloadArt(clientIP, port, splitMessage);
                    break;
                case "ART_NUM":
                    responseBuffer = numberOfArtInDirectory(clientIP, port, splitMessage);
                    break;
                case "ART_LIST":
                    responseBuffer = downloadArtList(clientIP, port, splitMessage);
                    break;
                case "UPLOAD_ART":
                    responseBuffer = uploadArt(clientIP, port, splitMessage, fileInputBuffer);
                    break;
                case "UPLOAD_CFG":
                    responseBuffer = uploadConfig(clientIP, port, splitMessage, fileInputBuffer);
                    break;
                case "UPLOAD_VMC":
                    responseBuffer = uploadVMC(clientIP, port, splitMessage, fileInputBuffer);
                    break;  
                case "CONFIG":
                    responseBuffer = downloadConfig(clientIP, port, splitMessage);
                    break;
                case "CHEAT":
                    responseBuffer = downloadCheat(clientIP, port, splitMessage);
                    break;
                case "CHEAT_LIST":
                    responseBuffer = downloadCheatList(clientIP, port, splitMessage);
                    break;
                case "CONFIG_LIST":
                    responseBuffer = downloadConfigList(clientIP, port, splitMessage);
                    break; 
                case "VMC_LIST":
                    responseBuffer = downloadVMCList(clientIP, port, splitMessage);
                    break;   
                case "VMC":
                    responseBuffer = downloadVMC(clientIP, port, splitMessage);
                    break;  
                case "UPDATE":
                    responseBuffer = downloadUpdate(clientIP, port, splitMessage[1], splitMessage[2], splitMessage[3]);
                    break;
                case "CUE2POPS":
                    responseBuffer = downloadCue2Pops(clientIP, port, splitMessage[1], splitMessage[2], splitMessage[3]);
                    break; 
                case "REPORT":
                    responseBuffer = "RESPONSE".getBytes();
                    reportFile(clientIP, splitMessage);
                    break;
                case "VERSION":
                    // This returns the latest build version, it is used for checking if an update is available
                    String latestBuildInfo = CURRENT_VERSION_NUMBER + "," + BUILD_DATE;
                    responseBuffer = latestBuildInfo.getBytes();
                    break;
                case "RESPOND":
                    responseBuffer = "RESPONSE".getBytes();
                    break;
            } 
        }
        else {
            // If the message from the client is not in the correct format (Possible Hacker)
            writeToErrorFile(IPAddress.toString().substring(1), Integer.toString(port), message, "CLIENT");
        }
        
        return responseBuffer;
    }

    
    // This returns the selected image file to the client
    private byte[] downloadArt(String IPAddress, int port, String[] splitMessage){
        
        byte[] responseBuffer;
        String selectedFilePath;
        
        // Only the _ICO images are png format, all other images on the server are jpg
        if (splitMessage[3].equals("_ICO")) selectedFilePath = getCurrentDirectory() + "/Covers/" + splitMessage[1] + "/" +  splitMessage[2] + "/" + splitMessage[3] + "/" + splitMessage[4] + "/" + splitMessage[4] + splitMessage[5] + splitMessage[3] + ".png";
        else selectedFilePath = getCurrentDirectory() + "/Covers/" + splitMessage[1] + "/" +  splitMessage[2] + "/" + splitMessage[3] + "/" + splitMessage[4] + "/" + splitMessage[4] + splitMessage[5] + splitMessage[3] + ".jpg";

        // Try and find the selected file, if the files does not exist on the server, respond with a string
        File selectedFile = new File(selectedFilePath);
        if(selectedFile.exists() && !selectedFile.isDirectory()) {
            responseBuffer = new byte [(int)selectedFile.length()];
            sendFileToClient(selectedFile);
        }
        else {responseBuffer = "NO_IMAGE".getBytes();}

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[6], splitMessage[7], splitMessage[8], selectedFilePath.replace(getCurrentDirectory(), ""), selectedFile.exists() && !selectedFile.isDirectory());
        return responseBuffer;
    }
   

    // This returns the number of image files in the directory
    private byte[] numberOfArtInDirectory(String IPAddress, int port, String[] splitMessage){
        
        byte[] responseBuffer;
        String selectedFilePath;

        // Get the path to the file
        selectedFilePath = getCurrentDirectory() + "/Covers/" + splitMessage[1] + "/" +  splitMessage[2] + "/" + splitMessage[3] + "/" + splitMessage[4];
        
        System.out.println(selectedFilePath);
        
        if (new File(selectedFilePath).exists() && new File(selectedFilePath).isDirectory()){
            
            // Get the number of files in the directory and place it in the response buffer
            String numberOfFiles = Integer.toString(new File(selectedFilePath).listFiles().length);
            responseBuffer = numberOfFiles.getBytes();
        }
        else {responseBuffer = "0".getBytes();}
        
        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[5], splitMessage[6], splitMessage[7], selectedFilePath.replace(getCurrentDirectory(), ""), new File(selectedFilePath).exists() && new File(selectedFilePath).isDirectory());
        return responseBuffer;
    }
    
    
    // This returns the art list to the client
    private byte[] downloadArtList(String IPAddress, int port, String[] splitMessage){
        
        byte[] responseBuffer;
        
        // This returns the art list, if the list does not exist on the server, respond with a string
        String selectedFilePath = getCurrentDirectory() + "/Lists/" + splitMessage[1] + "_All_Covers.txt";
        File selectedFile = new File(selectedFilePath);

        if(selectedFile.exists() && !selectedFile.isDirectory()) {
            responseBuffer = new byte [(int)selectedFile.length()];
            sendFileToClient(selectedFile);
        }
        else {responseBuffer = "NO_ART_LIST".getBytes();}

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[2], splitMessage[3], splitMessage[4], selectedFilePath.replace(getCurrentDirectory(), ""), selectedFile.exists() && !selectedFile.isDirectory());
        return responseBuffer;
    }
    
    // This recieves an image file from the client
    private byte[] uploadArt(String IPAddress, int port, String[] splitMessage, byte[] imageBuffer){
        
        byte[] responseBuffer;
        String selectedFilePath;
        
        // This is used to recieve art files from the users. The files are stored on the server in the uploads folder
        String clientMACAddress = splitMessage[5];
        
        new File(getCurrentDirectory() + "/Uploads/Covers/" + splitMessage[1] + "/" + splitMessage[2] + "/" + splitMessage[3] + "/" + clientMACAddress + "/").mkdirs();
        if (splitMessage[3].equals("_ICO")) selectedFilePath = getCurrentDirectory() + "/Uploads/Covers/" + splitMessage[1] + "/" + splitMessage[2] + "/" + splitMessage[3] + "/" + clientMACAddress + "/"  + splitMessage[4] + splitMessage[3] + ".png";
        else selectedFilePath = getCurrentDirectory() + "/Uploads/Covers/" + splitMessage[1] + "/" + splitMessage[2] + "/" + splitMessage[3] + "/" + clientMACAddress + "/"  + splitMessage[4] + splitMessage[3] + ".jpg";

        // Inform the client that the image file was recieved
        responseBuffer = "IMAGE_RECIEVED".getBytes();

        if (imageBuffer != null){

            FileOutputStream fileOutStream = null;
            try {fileOutStream = new FileOutputStream(selectedFilePath);} catch (FileNotFoundException ex) {writeToErrorFile(IPAddress, Integer.toString(port), ex.toString(), "SYSTEM");}

            if (fileOutStream != null){
                try {fileOutStream.write(imageBuffer);} catch (IOException ex) {writeToErrorFile(IPAddress, Integer.toString(port), ex.toString(), "SYSTEM");}
                try {fileOutStream.flush();} catch (IOException ex) {writeToErrorFile(IPAddress, Integer.toString(port), ex.toString(), "SYSTEM");}
                try {fileOutStream.close();} catch (IOException ex) {writeToErrorFile(IPAddress, Integer.toString(port), ex.toString(), "SYSTEM");}
                System.out.println("New file written to server: " + selectedFilePath.replace(getCurrentDirectory(), ""));
            } 
        }

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[5], splitMessage[6], splitMessage[7], selectedFilePath.replace(getCurrentDirectory(), ""), false);
        return responseBuffer;
    }

    
    // This recieves a config file from the client
    private byte[] uploadConfig(String IPAddress, int port, String[] splitMessage, byte[] configBuffer){
        
        byte[] responseBuffer;
        String selectedFilePath;
        
        // This is used to recieve config files from the users. The files are stored on the server in the uploads folder
        String clientMACAddress = splitMessage[4];
        
        new File(getCurrentDirectory() + "/Uploads/Configs/" + splitMessage[1] + "/" + splitMessage[2] + "/" + splitMessage[3] + "/" + clientMACAddress + "/").mkdirs();
        selectedFilePath = getCurrentDirectory() + "/Uploads/Configs/" + splitMessage[1] + "/" + splitMessage[2] + "/" + splitMessage[3] + "/" + clientMACAddress + "/" + splitMessage[3] + ".cfg";

        // Inform the client that the config file was recieved
        responseBuffer = "CONFIG_RECIEVED".getBytes();

        if (configBuffer != null){
            try {Files.write(new File(selectedFilePath).toPath(), configBuffer);} catch (IOException ex) {}
            System.out.println("New file written to server: " + selectedFilePath.replace(getCurrentDirectory(), ""));
        }

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[4], splitMessage[5], splitMessage[6], selectedFilePath.replace(getCurrentDirectory(), ""), false);
        return responseBuffer;
    }
    
    
    // This recieves a VMC file from the client
    private byte[] uploadVMC(String IPAddress, int port, String[] splitMessage, byte[] vmcBuffer){
        
        byte[] responseBuffer;
        String vmcFilePath = "";
        String vmcDescriptionFilePath;
        //byte[] vmcDescriptionBuffer = new byte[splitMessage[4].length()];
        byte[] vmcDescriptionBuffer = splitMessage[4].getBytes();
        
        // This is used to recieve VMC files from the users. The files are stored on the server in the uploads folder
        String clientMACAddress = splitMessage[5];
        
        new File(getCurrentDirectory() + "/Uploads/MemoryCards/" + splitMessage[1] + "/" + splitMessage[2] + "/" + splitMessage[3] + "/" + clientMACAddress + "/").mkdirs();
        if (splitMessage[1].equals("PS1")) {vmcFilePath = getCurrentDirectory() + "/Uploads/MemoryCards/" + splitMessage[1] + "/" + splitMessage[2] + "/" + splitMessage[3] + "/" + clientMACAddress + "/" + splitMessage[3] + ".VMC";}
        else if (splitMessage[1].equals("PS2")) {vmcFilePath = getCurrentDirectory() + "/Uploads/MemoryCards/" + splitMessage[1] + "/" + splitMessage[2] + "/" + splitMessage[3] + "/" + clientMACAddress + "/" + splitMessage[3] + ".bin";}
        
        vmcDescriptionFilePath = getCurrentDirectory() + "/Uploads/MemoryCards/" + splitMessage[1] + "/" + splitMessage[2] + "/" + splitMessage[3] + "/" + clientMACAddress + "/" + splitMessage[3] + ".txt";
        
        // Inform the client that the config file was recieved
        responseBuffer = "VMC_RECIEVED".getBytes();

        if (vmcBuffer != null){
            try {Files.write(new File(vmcFilePath).toPath(), vmcBuffer);} catch (IOException ex) {}
            try {Files.write(new File(vmcDescriptionFilePath).toPath(), vmcDescriptionBuffer);} catch (IOException ex) {}
            System.out.println("New file written to server: " + vmcFilePath.replace(getCurrentDirectory(), ""));
        }

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[5], splitMessage[6], splitMessage[7], vmcFilePath.replace(getCurrentDirectory(), ""), false);
        return responseBuffer;
    }
    
    
    // This returns the selected config file to the client
    private byte[] downloadConfig(String IPAddress, int port, String[] splitMessage){
        
        byte[] responseBuffer;
        
        // This is used to return a config file for a specific game
        String selectedFilePath = getCurrentDirectory() + "/Configs/" + splitMessage[1] + "/" +  splitMessage[2] + "/"  + splitMessage[3] + ".cfg";
        File selectedFile = new File(selectedFilePath);

        if(selectedFile.exists() && !selectedFile.isDirectory()) {
            responseBuffer = new byte [(int)selectedFile.length()];
            sendFileToClient(selectedFile);
        }
        else {responseBuffer = "NO_CONFIG".getBytes();}

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[4], splitMessage[5], splitMessage[6], selectedFilePath.replace(getCurrentDirectory(), ""), selectedFile.exists() && !selectedFile.isDirectory());
        return responseBuffer;
    }
    
    
    // This returns the selected vmc file to the client
    private byte[] downloadVMC(String IPAddress, int port, String[] splitMessage){
        
        byte[] responseBuffer;
        String selectedFilePath = "";
        
        // This is used to return a vmc file for a specific game
        if (splitMessage[1].equals("PS1")) {selectedFilePath = getCurrentDirectory() + "/MemoryCards/" + splitMessage[1] + "/" +  splitMessage[2] + "/"  + splitMessage[3] + ".VMC";}
        else if (splitMessage[1].equals("PS2")) {selectedFilePath = getCurrentDirectory() + "/MemoryCards/" + splitMessage[1] + "/" +  splitMessage[2] + "/"  + splitMessage[3] + ".bin";}
        File selectedFile = new File(selectedFilePath);

        if(selectedFile.exists() && !selectedFile.isDirectory()) {
            responseBuffer = new byte [(int)selectedFile.length()];
            sendFileToClient(selectedFile);
        }
        else {responseBuffer = "NO_VMC".getBytes();}

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[4], splitMessage[5], splitMessage[6], selectedFilePath.replace(getCurrentDirectory(), ""), selectedFile.exists() && !selectedFile.isDirectory());
        return responseBuffer;
    }
    
    
    // This returns the selected cheat file to the client
    private byte[] downloadCheat(String IPAddress, int port, String[] splitMessage){
        
        byte[] responseBuffer;

        // This is used to return the cheat information for a specific game
        String selectedFilePath = getCurrentDirectory() + "/Cheats/" + splitMessage[1] + "/" +  splitMessage[2] + "/"  + splitMessage[3] + ".cht";
        File selectedFile = new File(selectedFilePath);

        if(selectedFile.exists() && !selectedFile.isDirectory()) {
            Path path = Paths.get(selectedFilePath);
            responseBuffer = null;
            try {responseBuffer = Files.readAllBytes(path);} catch (IOException ex) {writeToErrorFile(IPAddress, Integer.toString(port), ex.toString(), "SYSTEM");}
        }
        else {responseBuffer = "NO_CHEAT".getBytes();}

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[4], splitMessage[5], splitMessage[6], selectedFilePath.replace(getCurrentDirectory(), ""), selectedFile.exists() && !selectedFile.isDirectory());
        return responseBuffer;
    }
    
    
    // This returns the cheat list to the client
    private byte[] downloadCheatList(String IPAddress, int port, String[] splitMessage){
        
        byte[] responseBuffer;

        // This returns the list of cheats
        String selectedFilePath = getCurrentDirectory() + "/Lists/" + splitMessage[1] + "_All_Cheats.txt";
        File selectedFile = new File(selectedFilePath);

        if(selectedFile.exists() && !selectedFile.isDirectory()) {
            sendFileToClient(selectedFile);
            responseBuffer = "LIST_SENT".getBytes();
        }
        else {responseBuffer = "NO_LIST".getBytes();}

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[2], splitMessage[3], splitMessage[4], selectedFilePath.replace(getCurrentDirectory(), ""), selectedFile.exists() && !selectedFile.isDirectory());
        return responseBuffer;
    }
    
    
    // This returns the config list to the client
    private byte[] downloadConfigList(String IPAddress, int port, String[] splitMessage){
        
        byte[] responseBuffer;

        // This returns the list of configs
        String selectedFilePath = getCurrentDirectory() + "/Lists/" + splitMessage[1] + "_All_Configs.txt";
        File selectedFile = new File(selectedFilePath);

        if(selectedFile.exists() && !selectedFile.isDirectory()) {
            sendFileToClient(selectedFile);
            responseBuffer = "LIST_SENT".getBytes();
        }
        else {responseBuffer = "NO_LIST".getBytes();}

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[2], splitMessage[3], splitMessage[4], selectedFilePath.replace(getCurrentDirectory(), ""), selectedFile.exists() && !selectedFile.isDirectory());
        return responseBuffer;
    }
    
    
    // This returns the vmc list to the client
    private byte[] downloadVMCList(String IPAddress, int port, String[] splitMessage){
        
        byte[] responseBuffer;

        // This returns the list of vmc's
        String selectedFilePath = getCurrentDirectory() + "/Lists/" + splitMessage[1] + "_All_VMCs.txt";
        File selectedFile = new File(selectedFilePath);

        if(selectedFile.exists() && !selectedFile.isDirectory()) {
            sendFileToClient(selectedFile);
            responseBuffer = "LIST_SENT".getBytes();
        }
        else {responseBuffer = "NO_LIST".getBytes();}

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), splitMessage[2], splitMessage[3], splitMessage[4], selectedFilePath.replace(getCurrentDirectory(), ""), selectedFile.exists() && !selectedFile.isDirectory());
        return responseBuffer;
    }
    
    
    // This returns the latest .jar file to the client
    private byte[] downloadUpdate(String IPAddress, int port, String MACAddress, String clientVersionNumber, String clientOS){
        
        byte[] responseBuffer;

        // This is used for updates, it returns the latest .jar file from the server
        File selectedFile = new File (getCurrentDirectory() + "/Application/" + APP_NAME);
        if(selectedFile.exists() && !selectedFile.isDirectory()) {
            responseBuffer = new byte [(int)selectedFile.length()];
            sendFileToClient(selectedFile);
        }
        else {responseBuffer = "UPDATE_ERROR".getBytes();}

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), MACAddress, clientVersionNumber, clientOS, selectedFile.getAbsolutePath().replace(getCurrentDirectory(), ""), selectedFile.exists() && !selectedFile.isDirectory());
        return responseBuffer;
    }
    

    // This returns the latest cue2pops file to the client
    private byte[] downloadCue2Pops(String IPAddress, int port, String MACAddress, String clientVersionNumber, String clientOS){
        
        byte[] responseBuffer;

        // This is used for updating cue2pops, it returns the latest cue2pops file from the server
        File selectedFile = null;
        if (clientOS.contains("Windows")) {selectedFile = new File (getCurrentDirectory() + "/Application/cue2pops/windows/cue2pops.exe");}
        else {selectedFile = new File (getCurrentDirectory() + "/Application/cue2pops/linux/cue2pops");}
        
        // Return the cue2pops file to the client
        if(selectedFile.exists() && !selectedFile.isDirectory()) {
            responseBuffer = new byte [(int)selectedFile.length()];
            sendFileToClient(selectedFile);
        }
        else {responseBuffer = "CUE2POPS_ERROR".getBytes();}

        // Write to log file
        writeToLogFile(IPAddress, Integer.toString(port), MACAddress, clientVersionNumber, clientOS, selectedFile.getAbsolutePath().replace(getCurrentDirectory(), ""), selectedFile.exists() && !selectedFile.isDirectory());
        return responseBuffer;
    }

    
    // This creates a bad file report on the server, sent from the client
    private void reportFile(String IPAddress, String[] splitMessage){

        // Ensure that the message is the correct size
        if (splitMessage.length == 8){
            String console = null;
            String rootFolder = null;
            String fileExtension = null;
            String MACAddress = "(" +splitMessage[5] + ")";

            switch (splitMessage[2]) {
                case "CFG":
                    rootFolder = "Configs";
                    fileExtension = ".cfg";
                    break;
                case "CHT":
                    rootFolder = "Cheats";
                    fileExtension = ".cht";
                    break;
                case "VMC":
                    rootFolder = "MemoryCards";
                    fileExtension = ".VMC";
                    break;
                case "COV":
                case "COV2":
                case "SCR":
                case "SCR2":
                case "BG":
                    rootFolder = "Covers";
                    fileExtension = "_" + splitMessage[2] + ".jpg";
                    break;
                case "ICO":
                    rootFolder = "Covers";
                    fileExtension = "_ICO.png";
                    break;
            } 

            if (rootFolder != null){
                
                if (rootFolder.equals("Covers")){
                    if (new File(getCurrentDirectory() + "/" + rootFolder + "/PS1/" + splitMessage[3] + "/_" + splitMessage[2] + "/" + splitMessage[1] + fileExtension).exists()){console = "PS1";}
                    else if (new File(getCurrentDirectory() + "/" + rootFolder + "/PS2/" + splitMessage[3] + "/_" + splitMessage[2] + "/" + splitMessage[1] + fileExtension).exists()){console = "PS2";}
                    
                    System.out.println("Checking: " + getCurrentDirectory() + "/" + rootFolder + "/" + console + "/" + splitMessage[3] + "/_" + splitMessage[2] + "/" + splitMessage[1] + fileExtension);
                    System.out.println("Exists: " + new File(getCurrentDirectory() + "/" + rootFolder + "/" + console + "/" + splitMessage[3] + "/_" + splitMessage[2] + "/" + splitMessage[1] + fileExtension).exists());
                    System.out.println("");
                }
                else {
                    if (new File(getCurrentDirectory() + "/" + rootFolder + "/PS1/" + splitMessage[3] + "/" + splitMessage[1] + fileExtension).exists()){console = "PS1";}
                    else if (new File(getCurrentDirectory() + "/" + rootFolder + "/PS2/" + splitMessage[3] + "/" + splitMessage[1] + fileExtension).exists()){console = "PS2";}
                    
                    System.out.println("Checking: " + getCurrentDirectory() + "/" + rootFolder + "/" + console + "/" + splitMessage[3] + "/" + splitMessage[1] + fileExtension);
                    System.out.println("Exists: " + new File(getCurrentDirectory() + "/" + rootFolder + "/" + console + "/" + splitMessage[3] + "/" + splitMessage[1] + fileExtension).exists());
                    System.out.println("");
                }
                
                if (console != null){

                    File reportFile;

                    if (rootFolder.equals("Covers")){reportFile = new File(getCurrentDirectory() + "/Reports/" + console + "/" + splitMessage[3] + "/_" + splitMessage[2] + "/" + splitMessage[1] + "--" + MACAddress + "--(" + IPAddress + ")" + ".txt");}
                    else {reportFile = new File(getCurrentDirectory() + "/Reports/" + console + "/" + splitMessage[3] + "/" + splitMessage[2] + "/" + splitMessage[1] + "--" + MACAddress + "--(" + IPAddress + ")" + ".txt");}
                    
                    System.out.println(reportFile.getAbsolutePath());

                    try{
                        if(!reportFile.exists()) {reportFile.createNewFile();}
                        FileWriter fileWriter = new FileWriter(reportFile, true);
                        try (BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {bufferedWriter.write(splitMessage[4] + "\n\n");}

                    } catch(IOException ex) {System.out.println("*****SYSTEM ERROR*****\nMessage: " + ex + "\n\n");}  
                } 
            }
        }
    }
    
    
    //**********************************************************************************************************************************************************
    //**********************************************************************************************************************************************************
    // Other functions
    
    // This returns the current directory of the .Jar file
    private String getCurrentDirectory(){
        
        CodeSource codeSource = WorkerRunnable.class.getProtectionDomain().getCodeSource();
        String jarDirectory = null;
        try {
            File jarFile = new File(codeSource.getLocation().toURI().getPath());
            jarDirectory = jarFile.getParentFile().getPath();
        } 
        catch (URISyntaxException ex) {}  
        
      return jarDirectory;
    }
    
    // These functions send the array of bytes to the client
    public void sendBytes(byte[] myByteArray) {
        try {sendBytes(myByteArray, 0, myByteArray.length);} catch (IOException ex) {}
    }
    
    public void sendBytes(byte[] myByteArray, int start, int len) throws IOException {
        
        if (len < 0) throw new IllegalArgumentException("Negative length not allowed");
        if (start < 0 || start >= myByteArray.length) throw new IndexOutOfBoundsException("Out of bounds: " + start);

        OutputStream out = clientSocket.getOutputStream(); 
        DataOutputStream dos = new DataOutputStream(out);

        dos.writeInt(len);
        if (len > 0) {dos.write(myByteArray, start, len);}
    }

    // This sends a specific file to the client
    private void sendFileToClient(File transferFile) {

        if (transferFile.length() > 0){
            FileInputStream fin = null;
            try {
                byte [] fileBuffer = new byte [(int)transferFile.length()];
                fin = new FileInputStream(transferFile);
                BufferedInputStream bin = new BufferedInputStream(fin);
                bin.read(fileBuffer,0,fileBuffer.length);
                sendBytes(fileBuffer);
                System.out.println("File sent to client - " + transferFile.getAbsolutePath().replace(getCurrentDirectory(), ""));
            } catch (FileNotFoundException ex) {
                writeToErrorFile(null, null, ex.toString(), "SYSTEM");
            } catch (IOException ex) {
                writeToErrorFile(null, null, ex.toString(), "SYSTEM");
            } finally {
                try {
                    if (fin != null) {fin.close();}
                } catch (IOException ex) {
                    writeToErrorFile(null, null, ex.toString(), "SYSTEM");
                }
            }
        }
    }

    // This gets the current time formatted
    private String getCurrentTime(){
        
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy h:mm:ss a");
        String formattedDate = sdf.format(date);
        
        return formattedDate;
    }

    // This logs data to a text file for analysis
    private void writeToLogFile(String clientIP, String clientPort, String clientMac, String clientVersionNumber, String clientOS, String fileName, boolean fileFound){
        
        File trafficLogFile = new File(getCurrentDirectory() + "/Log/traffic_log.txt");

        try{
            if(!trafficLogFile.exists()) {trafficLogFile.createNewFile();}
            FileWriter fileWriter = new FileWriter(trafficLogFile, true);
            try (BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {bufferedWriter.write("Client Connected: " + clientIP + " : (" + clientPort + ") -- (" + clientMac + ")\nClient Version Number = " + clientVersionNumber + " - (" + clientOS + ")\nDate/Time: " + getCurrentTime() + "\nFile Requested: " + fileName + "   (File Available: " + fileFound + ")\n\n");}
            
        } catch(IOException ex) {System.out.println("*****SYSTEM ERROR*****\nMessage: " + ex + "\n\n");}  
    }   

    // This logs errors to the console and to a text file for analysis
    private void writeToErrorFile(String clientIP, String clientPort, String message, String messageType){

        File errorLogFile = new File(getCurrentDirectory() + "/Log/error_log.txt");

        try{
            if(!errorLogFile.exists()) {errorLogFile.createNewFile();}
            FileWriter fileWriter = new FileWriter(errorLogFile, true);
            try (BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {
                if (messageType.equals("CLIENT")){
                    bufferedWriter.write("Client Connected: " + clientIP + " : (" + clientPort + ")\n" +"Date/Time: " + getCurrentTime() + "\nMESSAGE RECIEVED: " + message + "\n\n");
                    System.out.println("\n\n(" + getCurrentTime() + ")  Client Connected: "  + clientIP + " : (" + clientPort +  ")  *****BAD REQUEST*****\nMessage: " + message);
                }
                else if (messageType.equals("SYSTEM")){
                    bufferedWriter.write("Client Connected: " + clientIP + " : (" + clientPort + ")\n" +"Date/Time: " + getCurrentTime() + "\nSYSTEM ERROR: " + message + "\n\n");
                    System.out.println("\n\n(" + getCurrentTime() + ")  Client Connected: "  + clientIP + " : (" + clientPort +  ")  *****SYSTEM ERROR*****\nMessage: " + message);
                }
            }
            
        } catch(IOException ex) {System.out.println("*****SYSTEM ERROR*****\nMessage: " + ex + "\n\n");}  
    }   
}
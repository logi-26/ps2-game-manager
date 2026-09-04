package tcpserver;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

public class TCPServer implements Runnable {
    
    
    //*************************************************
    protected static String serverInfo = "OPLPOPS TCP LIVE Server - Build Date (05/04/2017)";
    protected static int serverPort = 6789;
    //protected static int serverPort = 9876;
    //*************************************************
    
    protected ServerSocket serverSocket = null;
    protected boolean isStopped = false;
    protected Thread runningThread = null;
    
    public TCPServer(int port){
        this.serverPort = port;
    }
            
    @Override
    public void run(){
        
        synchronized(this){this.runningThread = Thread.currentThread();}
        openServerSocket();
        
        while(!isStopped()){
            Socket clientSocket = null;
            
            try {clientSocket = this.serverSocket.accept();} 
            catch (IOException e) {
                
                if(isStopped()) {
                    System.out.println("Server Stopped.");
                    return;
                }
                throw new RuntimeException("Error accepting client connection", e);
            }
            
            // Start a thread to handle the response
            new Thread(new WorkerRunnable(clientSocket)).start();
        }
        System.out.println("Server Stopped.");
    }
    
    // Determine if the server is stopped
    private synchronized boolean isStopped() {
        return this.isStopped;
    }
    
    // Stop the server
    public synchronized void stop(){
        
        this.isStopped = true;
        try {this.serverSocket.close();} 
        catch (IOException e) {throw new RuntimeException("Error closing server", e);}
    }

    // Open server socket
    private void openServerSocket() {
        
        try {this.serverSocket = new ServerSocket(this.serverPort);} 
        catch (IOException e) {throw new RuntimeException("Cannot open port " + serverPort, e);}
    }
    
    // Main    
    public static void main(String[] args) throws Exception {
 
        TCPServer server = new TCPServer(serverPort);
        new Thread(server).start();
        System.out.println("\n\n*****************************************************\nRunning " + serverInfo + " - Port: " + serverPort + "\n*****************************************************");
    }
}

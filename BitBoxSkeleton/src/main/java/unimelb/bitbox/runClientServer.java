package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;

import javax.crypto.SecretKey;
import javax.net.ServerSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

public class runClientServer extends Thread{
    private ArrayList<HostPort> peerList;
    private Socket socket;
    private HostPort myPort;
    private HashMap<Socket, BufferedWriter> socketWriter;
    private HashMap<Socket, BufferedReader> socketReader;
    private HashMap<String, String> keymap;
    private Logger log = Logger.getLogger(ServerMain.class.getName());
    private SecretKey secretKey;
    private ExecutorService tpool;
    private FileSystemManager fileSystemManager;
    private int maxConnection;
    private boolean isFirst;
    private ServerMain serverMain;
    private int ClientPort;
    runClientServer(HostPort myPort,
                    HashMap<Socket, BufferedWriter> socketWriter,
                    HashMap<Socket, BufferedReader> socketReader,
                    ArrayList<HostPort> peerList, HashMap<String, String> keymap, ExecutorService tpool,
                    FileSystemManager fileSystemManager, ServerMain serverMain, int maxConnection, boolean isFirst, int ClientPort){
        this.myPort = myPort;
        this.peerList = peerList;
        this.socketReader = socketReader;
        this.socketWriter = socketWriter;
        this.keymap = keymap;
        this.tpool = tpool;
        this.maxConnection = maxConnection;
        this.fileSystemManager = fileSystemManager;
        this.isFirst = isFirst;
        this.serverMain = serverMain;
        this.ClientPort = ClientPort;
    }

    public void run(){

        ServerSocketFactory factory = ServerSocketFactory.getDefault();
        ServerSocket socket = null;
        try {
            socket = factory.createServerSocket(ClientPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info("ClientServer Listening at " + ClientPort);

        while (true) {
            Socket client = null;
            try {
                client = socket.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }
            ClientServerLogic CSL = new ClientServerLogic(client, myPort,
                    socketWriter, socketReader, peerList, keymap, tpool, serverMain.fileSystemManager, serverMain, maxConnection, false);
            CSL.start();
        }



    }
}

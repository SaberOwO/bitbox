package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.HostPort;
import javax.net.ServerSocketFactory;
import java.net.DatagramSocket;


public class Peer 
{
	private static Logger log = Logger.getLogger(Peer.class.getName());
	private static String localIp = Configuration.getConfigurationValue("advertisedName");
	private static int localPort = Integer.valueOf(Configuration.getConfigurationValue("port"));
	private static int maxConnection = Integer.valueOf(Configuration.getConfigurationValue("maximumIncommingConnections"));
	private static ArrayList<HostPort> peerList = new ArrayList<>();
	private static HashMap<Socket, BufferedWriter> socketWriter= new HashMap<>();
    private static HashMap<Socket, BufferedReader> socketReader= new HashMap<>();

	public static void main( String[] args ) throws IOException, NumberFormatException, NoSuchAlgorithmException {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
        log.info("BitBox Peer starting...");
        ServerMain serverMain = new ServerMain(socketWriter);
        ServerMain udpServerMain = new ServerMain();
        String[] peersInfo = Configuration.getConfigurationValue("peers").split(",");

        ExecutorService tpool = Executors.newFixedThreadPool(maxConnection * 3);

        if (Configuration.getConfigurationValue("mode").equals("tcp")) {
            if (Configuration.getConfigurationValue("peers").equals("")) {
                log.info("First Peer In The CLUSTER");
                runServer(tpool, serverMain);
            } else {
                boolean flag = false;
                for (String peerInfo : peersInfo) {
                    HostPort hostPort = new HostPort(peerInfo);
                    try {
                        runClient(hostPort, tpool, serverMain);
                        flag = true;
                    } catch (IOException e) {
                        log.info("HostPort invalid!");
                    }

                }
                if (!flag) {
                    log.info("First Peer In The CLUSTER");
                    runServer(tpool, serverMain);
                }
            }
        }else {
            if (Configuration.getConfigurationValue("peers").equals("")) {
                log.info("First Peer In The CLUSTER");
                runUDPServer(tpool, udpServerMain);
            }else {
                boolean flag = false;
                for (String peerInfo : peersInfo) {
                    HostPort hostPort = new HostPort(peerInfo);
                    runUDPClient(hostPort,tpool,udpServerMain);
                    flag = true;
                }
                if (!flag) {
                    log.info("First Peer In The CLUSTER");
                    runUDPServer(tpool, udpServerMain);
                }
            }
        }
    }
    private static void runClient(HostPort hostPort, ExecutorService tpool, ServerMain sm) throws IOException {
	    Socket client = new Socket(hostPort.host, hostPort.port);
	    tpool.execute(new PeerLogic(client, new HostPort(localIp, localPort),
                socketWriter, socketReader, sm.fileSystemManager, sm, true, peerList, maxConnection));
    }

    private static void runServer(ExecutorService tpool, ServerMain sm) throws IOException {
        ServerSocketFactory factory = ServerSocketFactory.getDefault();
        ServerSocket socket = factory.createServerSocket(localPort);
        log.info("Listening at " + localPort);
        while (true) {
            Socket client = socket.accept();
            tpool.execute(new PeerLogic(client, new HostPort(localIp, localPort),
                    socketWriter, socketReader, sm.fileSystemManager, sm, false, peerList, maxConnection));
        }
    }


    private static void runUDPClient(HostPort hostPort,ExecutorService tpool,ServerMain serverMain) throws IOException{

	    DatagramSocket datagramSocket = new DatagramSocket();
	    tpool.execute(new PeerUDPLogic(datagramSocket,serverMain.fileSystemManager,
                serverMain, true,peerList,maxConnection,hostPort));
    }

    private static void runUDPServer(ExecutorService tpool,ServerMain serverMain) throws IOException{
	    DatagramSocket datagramSocket = new DatagramSocket(localPort);
	    log.info("Listening at "+localPort);
            tpool.execute(new PeerUDPLogic(datagramSocket,serverMain.fileSystemManager,
                    serverMain, false,peerList,maxConnection, new HostPort(localIp, localPort)));
    }
}

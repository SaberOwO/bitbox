package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.HostPort;
import javax.net.ServerSocketFactory;

public class Peer 
{
	private static Logger log = Logger.getLogger(Peer.class.getName());
	private static String localIp = Configuration.getConfigurationValue("advertisedName");
	private static int localPort = Integer.valueOf(Configuration.getConfigurationValue("port"));
	private static int maxConnection = Integer.valueOf(Configuration.getConfigurationValue("maximumIncommingConnections"));
	private static ArrayList<HostPort> peerList = new ArrayList<>();
	private static HashMap<Socket, BufferedWriter> socketWriter= new HashMap<>();
    private static HashMap<Socket, BufferedReader> socketReader= new HashMap<>();

	public static void main( String[] args ) throws IOException, NumberFormatException, NoSuchAlgorithmException
    {
    	System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
        log.info("BitBox Peer starting...");
        ServerMain sm = new ServerMain(socketWriter);

        String[] peersInfo = Configuration.getConfigurationValue("peers").split(",");

        // make sure that i can handle 20 more request at same time
        ExecutorService tpool = Executors.newFixedThreadPool(maxConnection + peersInfo.length + 20);

        if (Configuration.getConfigurationValue("peers").equals("")) {
            log.info("First Peer In The CLUSTER");
            runServer(tpool, sm);
        } else {
            boolean flag = false;
            for (String peerInfo: peersInfo) {
                HostPort hostPort = new HostPort(peerInfo);
                runClient(hostPort, tpool, sm);
                flag = true;
            }
            if (flag == false) {
                log.info("First Peer In The CLUSTER");
                runServer(tpool, sm);
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

}

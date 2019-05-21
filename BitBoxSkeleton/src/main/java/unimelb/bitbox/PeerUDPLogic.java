package unimelb.bitbox;


import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.logging.Logger;


public class PeerUDPLogic extends Thread {
    private static Logger log = Logger.getLogger(PeerUDPLogic.class.getName());
    private DatagramSocket datagramSocket;
    private DatagramPacket datagramPacket;
    private FileSystemManager fileSystemManager;
    private ServerMain serverMain;
    private boolean isFirst;
    private ArrayList<HostPort> peerList;
    private int maxConnection;
    private HostPort hostPort;
    private LinkedList<Document> messageList;
    private static String localIp = Configuration.getConfigurationValue("advertisedName");
    private static int localPort = Integer.valueOf(Configuration.getConfigurationValue("port"));
    private static int syncInterval = Integer.valueOf(Configuration.getConfigurationValue("syncInterval"));


    public PeerUDPLogic(DatagramSocket datagramSocket,FileSystemManager fileSystemManager,
                        ServerMain serverMain, boolean isFirst, ArrayList<HostPort> peerList,
                        int maxConnection,HostPort hostPort) {
        this.datagramSocket = datagramSocket;
        this.fileSystemManager = fileSystemManager;
        this.serverMain = serverMain;
        this.isFirst = isFirst;
        this.peerList = peerList;
        this.maxConnection = maxConnection;
        this.hostPort = hostPort;
    }


    public void run(){
        if (isFirst){
            sendHandShakeRequest(datagramSocket,hostPort);
        }

        while (true) {
            byte[] data = new byte[8192];
            DatagramPacket receivedPacket = new DatagramPacket(data,data.length);
            try {
                datagramSocket.receive(receivedPacket);
                handleLogic(datagramSocket,receivedPacket);
             //   syncTimer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    private void handleLogic(DatagramSocket datagramSocket,DatagramPacket receivedPacket){
        log.info("handshake info:"+new String(receivedPacket.getData()));
        try {
        String receivedData = new String(receivedPacket.getData(),0, receivedPacket.getLength(),"UTF-8");
        Document message = Document.parse(receivedData);
        log.info("Input: " + message.toJson());

        switch(message.getString("command")){
            case "INVALID_PROTOCOL":
                log.info("INVALID_PROTOCOL");
                log.info(message.toJson());
                datagramSocket.close();
                break;

            case "CONNECTION_REFUSED":
                log.info("CONNECTION_REFUSED");
                log.info(message.toJson());
                handleHandShakeRefuse(datagramSocket,message);
                datagramSocket.close();
                break;

            case "HANDSHAKE_REQUEST":
                log.info("HANDSHAKE_REQUEST");
                log.info(message.toJson());
                handleHandShakeRequest(datagramSocket,message);
                break;

            case"HANDSHAKE_RESPONSE":
                log.info("HANDSHAKE_RESPONSE");
                log.info(message.toJson());
                handleHandShakeResponse(message);
                break;

            case"DIRECTORY_CREATE_REQUEST":
                log.info("DIRECTORY_CREATE_REQUEST");
                log.info(message.toJson());
                handleDirectoryCreateRequest(datagramSocket,message,receivedPacket);
                break;

            case"DIRECTORY_CREATE_RESPONSE":
                log.info("DIRECTORY_CREATE_RESPONSE");
                log.info(message.toJson());
                break;
        }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void sendHandShakeRequest(DatagramSocket datagramSocket,HostPort hostPort){
        try {
            String localHost = InetAddress.getLocalHost().getHostAddress();
            HostPort localHostPort =new HostPort(localHost,
                    datagramSocket.getLocalPort());
            log.info("handshake comes from"+localHost);
            Document message = constructHandShakeRequest(localHostPort);
            sendInfo(datagramSocket,message,hostPort);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private Document constructHandShakeRequest(HostPort hostPort){
        //construct message in document
        Document request = new Document();
        request.append("command", "HANDSHAKE_REQUEST");
        Document hostinfo = new Document();
        hostinfo.append("host", hostPort.host);
        hostinfo.append("port", hostPort.port);
        request.append("hostPort", hostinfo);
        return request;
    }

    private void sendInvalidProtocol(DatagramSocket datagramSocket,HostPort remoteHostPort){
        Document message = constructInvalidProtocol();
        sendResponse(datagramSocket,message,remoteHostPort);
    }

    private Document constructInvalidProtocol(){
        Document response = new Document();
        response.append("command", "INVALID_PROTOCOL");
        response.append("message", "message must contain a command field as string");
        return response;
    }

    private void handleHandShakeResponse(Document message) {
        log.info("Handshake finished");
        HostPort remoteHostPort = new HostPort((Document) message.get("hostPort"));
        if (peerList.contains(remoteHostPort)) {
            return;
        }
        peerList.add(remoteHostPort);
        serverMain.updatePeerList(peerList);
        syncTimer();
    }

    private void handleHandShakeRefuse(DatagramSocket datagramSocket, Document message){
        log.info("Connection refused, trying to connect backup peer!");
        ArrayList<Document> peerList = (ArrayList<Document>) message.get("peers");
        for(Document peer:peerList){
            sendHandShakeRequest(datagramSocket,new HostPort(peer));
        }
    }

    private void handleHandShakeRequest(DatagramSocket datagramSocket,Document message){
        try {
            HostPort remoteHostPort = new HostPort((Document) message.get("hostPort"));
            if (peerList.contains(remoteHostPort)) {
                return;
            } else if (peerList.size() >= maxConnection) {
                sendHandShakeRefuse(datagramSocket,remoteHostPort);
            } else {
                sendHandShakeResponse(datagramSocket,remoteHostPort);
                peerList.add(remoteHostPort);
                serverMain.updatePeerList(peerList);
                System.out.println("Output Successful");
            }
        } catch (Exception e) {
            HostPort remoteHostPort = new HostPort((Document) message.get("hostPort"));
            sendInvalidProtocol(datagramSocket,remoteHostPort);
        }
    }

    private void sendHandShakeResponse(DatagramSocket datagramSocket,HostPort remoteHostPort){
        Document response = new Document();
        response.append("command", "HANDSHAKE_RESPONSE");
        response.append("hostPort",new HostPort(localIp,localPort).toDoc());
        sendResponse(datagramSocket,response,remoteHostPort);
    }

    private void sendHandShakeRefuse(DatagramSocket datagramSocket,HostPort remoteHostPort){
        Document response = new Document();
        response.append("command", "CONNECTION_REFUSED");
        response.append("message", "connection limit reached");
        ArrayList<Document> peers = new ArrayList<>();
        for (HostPort peer: peerList) {
            peers.add(peer.toDoc());
        }
        response.append("peers", peers);
        sendResponse(datagramSocket,response,remoteHostPort);
    }

    public void handleDirectoryCreateRequest(DatagramSocket datagramSocket,Document message,DatagramPacket receivedPacket){
        Document response = new Document();
        response.append("command", "DIRECTORY_CREATE_RESPONSE");
        String pathName = message.getString("pathName");
        response.append("pathName", pathName);
        boolean flag = fileSystemManager.dirNameExists(pathName);
        if (flag == true) {
            response.append("message", "pathname already exists");
            response.append("status", false);
            sendInfo(datagramSocket,receivedPacket,response);
            return ;
        }
        flag = fileSystemManager.isSafePathName(pathName);
        if (flag == false) {
            response.append("message", "unsafe pathname given");
            response.append("status", false);
            sendInfo(datagramSocket,receivedPacket,response);
            return ;
        }
        flag = fileSystemManager.makeDirectory(pathName);
        if(flag == false) {
            response.append("message", "there was a problem creating the directory");
            response.append("status",false);
            sendInfo(datagramSocket,receivedPacket,response);
        }else{
            response.append("message","directory created");
            response.append("status",true);
            sendInfo(datagramSocket,receivedPacket,response);
        }
    }


    private void syncTimer() {
        Runnable runnable = ()-> {
            while(true) {
                syncIt();
                try {
                    Thread.sleep(syncInterval*1000);
                }catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }


    private void syncIt() {
        ArrayList<FileSystemManager.FileSystemEvent> eventList = fileSystemManager.generateSyncEvents();
        for (FileSystemManager.FileSystemEvent event: eventList) {
            serverMain.processFileSystemEvent(event);
        }
    }

    //send response of file or document request
   private void sendInfo(DatagramSocket datagramSocket,DatagramPacket datagramPacket,Document info){
       byte[] message = new byte[8192];
       try {
           message = info.toJson().getBytes("UTF-8");
       } catch (UnsupportedEncodingException e) {
           log.info("message is not in UTF8");
       }
       DatagramPacket sendPacket = new DatagramPacket(message,message.length, datagramPacket.getAddress(),datagramPacket.getPort());
       try {
           datagramSocket.send(sendPacket);
       } catch (IOException e) {
           e.printStackTrace();
       }
   }

   //handshake related response
   private void sendResponse(DatagramSocket datagramSocket,Document response,HostPort remoteHostPort){
       byte[] message = new byte[8192];

       try {
           message = response.toJson().getBytes("UTF-8");
       } catch (UnsupportedEncodingException e) {
           log.info("message is not in UTF8");
       }

       try {
           InetAddress remotehostAddress = InetAddress.getByName(remoteHostPort.host);
           DatagramPacket datagramPacket = new DatagramPacket(message,message.length,remotehostAddress,remoteHostPort.port);
           try {
               datagramSocket.send(datagramPacket);
           } catch (IOException e) {
               e.printStackTrace();
           }
       } catch (UnknownHostException e) {
           e.printStackTrace();
       }

   }

   //send handshake request info
    private void sendInfo(DatagramSocket datagramSocket, Document info,HostPort hostPort){
        byte[] message = new byte[8192];
        try {
            message = info.toJson().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.info("message is not in UTF8");
        }
        try {
            InetAddress remoteHost = InetAddress.getByName(hostPort.host);
            log.info("handshake send to"+remoteHost.toString()+":"+hostPort.port);
            DatagramPacket datagramPacket = new DatagramPacket(message,message.length,remoteHost,hostPort.port);

                DatagramPacket receivePacket = new DatagramPacket(new byte[8192],8192);
                boolean receivedResponse = false;
                int tryTimes = 0;
                while (!receivedResponse&&tryTimes<3) {
                    try {
                        datagramSocket.send(datagramPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        tryTimes += 1;
                        datagramSocket.setSoTimeout(9000);
                        datagramSocket.receive(receivePacket);
                        if (receivePacket.getAddress().equals(remoteHost)) {
                            receivedResponse = true;
                            handleLogic(datagramSocket,datagramPacket);
                            datagramSocket.setSoTimeout(0);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            if(receivedResponse==false) {
                try {
                    datagramSocket.setSoTimeout(0);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

    }
}


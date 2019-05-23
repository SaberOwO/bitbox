package unimelb.bitbox;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Logger;

import unimelb.bitbox.util.*;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
    private static Logger log = Logger.getLogger(ServerMain.class.getName());
    protected FileSystemManager fileSystemManager;
    private HashMap<Socket, BufferedWriter> socketWriter;
    private static String mode = Configuration.getConfigurationValue("mode");
    private static int timeout = Integer.valueOf(Configuration.getConfigurationValue("timeout"));
    private static int packageSize = Integer.valueOf(Configuration.getConfigurationValue("packetSize"));
    private DatagramSocket datagramSocket;
    private ArrayList<HostPort> tempPeerList;
    public HashMap<DatagramSocket, ArrayList<HostPort>> peersMap;


    public ServerMain(HashMap<Socket, BufferedWriter> socketWriter) throws NumberFormatException, IOException, NoSuchAlgorithmException {
        fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
        this.socketWriter = socketWriter;
    }

    public ServerMain(HashMap<DatagramSocket, ArrayList<HostPort>> peersMap, String mode) throws IOException, NoSuchAlgorithmException {
        fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
        this.peersMap = peersMap;
        this.mode = mode;
    }

    public void updateTempPeerList(ArrayList<HostPort> peerList) {
        this.tempPeerList = peerList;
    }

//    public void processFileSystemEvent(FileSystemEvent fileSystemEvent, ArrayList<HostPort> peerList,
//                                       DatagramSocket datagramSocket) {
//        try {
//            switch (fileSystemEvent.event) {
//                case FILE_CREATE:
//                    sendUDP(constructCreateFileJson(fileSystemEvent), peerList, datagramSocket);
//                    log.info("FILE_CREATE message sent!");
//                    break;
//
//                case FILE_DELETE:
//                    sendUDP(constructDeleteFileJson(fileSystemEvent), peerList, datagramSocket);
//                    log.info("FILE_DELETE message sent!");
//                    break;
//                case FILE_MODIFY:
//                    sendUDP(constructModifyFileJson(fileSystemEvent), peerList, datagramSocket);
//                    log.info("FILE_MODIFY message sent!");
//                    break;
//                case DIRECTORY_CREATE:
//                    sendUDP(constructCreateDirectory(fileSystemEvent), peerList, datagramSocket);
//                    log.info("DIRECTORY_CREATE message sent!");
//                    break;
//                case DIRECTORY_DELETE:
//                    sendUDP(constructDeleteDirectory(fileSystemEvent), peerList, datagramSocket);
//                    log.info("DIRECTORY_DELETE message sent!");
//                    break;
//                default:
//                    log.warning("Wrong request");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    // By analyze the information of fileSystemEvent to construct and send the request
    public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
        try {
            switch (fileSystemEvent.event) {
                case FILE_CREATE:
                    if (mode.equals("tcp")) {
                        sendIt(constructCreateFileJson(fileSystemEvent));
                        log.info("FILE_CREATE message sent!");
                    } else {
                        sendUDP(constructCreateFileJson(fileSystemEvent),peersMap);
                        log.info("FILE_CREATE message sent!");
                    }
                    break;

                case FILE_DELETE:
                    if (mode.equals("tcp")) {
                        sendIt(constructDeleteFileJson(fileSystemEvent));
                        log.info("FILE_DELETE message sent!");
                    } else {
                        sendUDP(constructDeleteFileJson(fileSystemEvent), peersMap);
                        log.info("FILE_DELETE message sent!");
                    }
                    break;
                case FILE_MODIFY:
                    if (mode.equals("tcp")) {
                        sendIt(constructModifyFileJson(fileSystemEvent));
                        log.info("FILE_MODIFY message sent!");
                    } else {
                        sendUDP(constructModifyFileJson(fileSystemEvent),peersMap);
                        log.info("FILE_MODIFY message sent!");
                    }
                    break;
                case DIRECTORY_CREATE:
                    if (mode.equals("tcp")) {
                        sendIt(constructCreateDirectory(fileSystemEvent));
                        log.info("DIRECTORY_CREATE message sent!");
                    } else {
                        sendUDP(constructCreateDirectory(fileSystemEvent), peersMap);
                        log.info("DIRECTORY_CREATE message sent!");
                    }
                    break;
                case DIRECTORY_DELETE:
                    if (mode.equals("tcp")) {
                        sendIt(constructDeleteDirectory(fileSystemEvent));
                        log.info("DIRECTORY_DELETE message sent!");
                    } else {
                        sendUDP(constructDeleteDirectory(fileSystemEvent),peersMap);
                        log.info("DIRECTORY_DELETE message sent!");
                    }
                    break;
                default:
                    log.warning("Wrong request");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // send the information
    private void sendIt(String message) {
        for (Socket socket : socketWriter.keySet()) {
            BufferedWriter out = socketWriter.get(socket);
            try {
                out.write(message);
                out.newLine();
                out.flush();
            } catch (IOException e) {
                log.warning("something wrong");
                e.printStackTrace();
            }
        }
    }

    // construct the create file json
    private String constructCreateFileJson(FileSystemEvent fileSystemEvent) {
        Document request = new Document();
        request.append("command", "FILE_CREATE_REQUEST");
        request.append("fileDescriptor", constructFileDescriptor(fileSystemEvent));
        request.append("pathName", fileSystemEvent.pathName);
        return request.toJson();
    }

    // construct the delete file json
    private String constructDeleteFileJson(FileSystemEvent fileSystemEvent) {
        Document request = new Document();
        request.append("command", "FILE_DELETE_REQUEST");
        request.append("fileDescriptor", constructFileDescriptor(fileSystemEvent));
        request.append("pathName", fileSystemEvent.pathName);
        return request.toJson();
    }

    // construct the modify file json
    private String constructModifyFileJson(FileSystemEvent fileSystemEvent) {
        Document request = new Document();
        request.append("command", "FILE_MODIFY_REQUEST");
        request.append("fileDescriptor", constructFileDescriptor(fileSystemEvent));
        request.append("pathName", fileSystemEvent.pathName);
        return request.toJson();
    }

    // construct the create directory json
    private String constructCreateDirectory(FileSystemEvent fileSystemEvent) {
        Document request = new Document();
        request.append("command", "DIRECTORY_CREATE_REQUEST");
        request.append("pathName", fileSystemEvent.pathName);
        return request.toJson();
    }

    // construct the delete directory json
    private String constructDeleteDirectory(FileSystemEvent fileSystemEvent) {
        Document request = new Document();
        request.append("command", "DIRECTORY_DELETE_REQUEST");
        request.append("pathName", fileSystemEvent.pathName);
        return request.toJson();
    }

    // construct the file descriptor json
    private Document constructFileDescriptor(FileSystemEvent fileSystemEvent) {
        Document fileDescriptor = new Document();
        FileSystemManager.FileDescriptor descriptor = fileSystemEvent.fileDescriptor;
        fileDescriptor.append("md5", descriptor.md5);
        fileDescriptor.append("lastModified", descriptor.lastModified);
        fileDescriptor.append("fileSize", descriptor.fileSize);
        return fileDescriptor;
    }

    public void sendUDP(String message, HashMap<DatagramSocket, ArrayList<HostPort>> peersMap) {
        // .DS_Store filter

//        try {
//            JSONParser parser = new JSONParser();
//            JSONObject message_json = (JSONObject) parser.parse(message);
//            System.out.println(message_json.get("pathName"));
//            if(message_json.get("pathName").equals(".DS_Store")){
//                return;
//            }
//        } catch (ParseException e) {
//            e.printStackTrace();
//        }


        for (DatagramSocket datagramSocket : peersMap.keySet()) {
            ArrayList<HostPort> peerList = peersMap.get(datagramSocket);
            for (HostPort peer : peerList) {
                byte[] sendMessage = new byte[packageSize];
                try {
                    sendMessage = message.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    log.info("message is not in UTF8");
                }
                try {
                    InetAddress remoteHost = InetAddress.getByName(peer.host);
                    log.info("udpPacket send to " + remoteHost.toString() + ":" + peer.port);
                    log.info("Packet content: " + message);

                    DatagramPacket datagramPacket = new DatagramPacket(sendMessage, sendMessage.length, remoteHost, peer.port);
                    DatagramPacket receivePacket = new DatagramPacket(new byte[packageSize], packageSize);

                    boolean receivedResponse = false;
                    int tryTimes = 0;
                    while (!receivedResponse && tryTimes < 3) {
                        try {
                            datagramSocket.send(datagramPacket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        try {
                            tryTimes += 1;
                            datagramSocket.setSoTimeout(timeout * 1000);
                            datagramSocket.receive(receivePacket);
                            if (receivePacket.getAddress().equals(remoteHost)) {
                                receivedResponse = true;
                                System.out.println("###########################");
                                System.out.println("Message Content: " + message);
                                System.out.println("(OK) The message has got response in time.");
                                System.out.println("###########################");
                                datagramSocket.setSoTimeout(0);
                            }
                        } catch (IOException e) {
                            System.out.println("###########################");
                            System.out.println("Message Content: " + message);
                            System.out.println("Retry time: " + tryTimes);
                            System.out.println("(ERROR) The message does not get response in time.");
                            System.out.println("###########################");
                        }
                    }
                    if (!receivedResponse) {
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

    }
}

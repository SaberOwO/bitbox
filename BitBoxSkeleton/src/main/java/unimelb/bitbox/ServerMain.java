package unimelb.bitbox;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import unimelb.bitbox.util.*;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
    private static Logger log = Logger.getLogger(ServerMain.class.getName());
    protected FileSystemManager fileSystemManager;
    private HashMap<Socket, BufferedWriter> socketWriter;
    private static String mode = Configuration.getConfigurationValue("mode");
    private DatagramSocket datagramSocket;
    private ArrayList<HostPort> peerList;


    public ServerMain(HashMap<Socket, BufferedWriter> socketWriter) throws NumberFormatException, IOException, NoSuchAlgorithmException {
        fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
        this.socketWriter = socketWriter;
    }

    public ServerMain(DatagramSocket datagramSocket, ArrayList<HostPort> peerList) throws IOException, NoSuchAlgorithmException {
        fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
        this.datagramSocket = datagramSocket;
        this.peerList = peerList;
    }

    public void updatePeerList(ArrayList<HostPort> peerList) {
        this.peerList = peerList;
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
                    if (mode == "tcp") {
                        sendIt(constructCreateFileJson(fileSystemEvent));
                        log.info("FILE_CREATE message sent!");
                    } else {
                        sendUDP(constructCreateFileJson(fileSystemEvent), peerList, datagramSocket);
                        log.info("FILE_CREATE message sent!");
                    }
                    break;

                case FILE_DELETE:
                    if (mode == "tcp") {
                        sendIt(constructDeleteFileJson(fileSystemEvent));
                        log.info("FILE_DELETE message sent!");
                    } else {
                        sendUDP(constructDeleteFileJson(fileSystemEvent), peerList, datagramSocket);
                        log.info("FILE_DELETE message sent!");
                    }
                    break;
                case FILE_MODIFY:
                    if (mode == "tcp") {
                        sendIt(constructModifyFileJson(fileSystemEvent));
                        log.info("FILE_MODIFY message sent!");
                    } else {
                        sendUDP(constructModifyFileJson(fileSystemEvent), peerList, datagramSocket);
                        log.info("FILE_MODIFY message sent!");
                    }
                    break;
                case DIRECTORY_CREATE:
                    if (mode == "tcp") {
                        sendIt(constructCreateDirectory(fileSystemEvent));
                        log.info("DIRECTORY_CREATE message sent!");
                    } else {
                        sendUDP(constructCreateDirectory(fileSystemEvent), peerList, datagramSocket);
                        log.info("DIRECTORY_CREATE message sent!");
                    }
                    break;
                case DIRECTORY_DELETE:
                    if (mode == "tcp") {
                        sendIt(constructDeleteDirectory(fileSystemEvent));
                        log.info("DIRECTORY_DELETE message sent!");
                    } else {
                        sendUDP(constructDeleteDirectory(fileSystemEvent), peerList, datagramSocket);
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

    public void sendUDP(String message, ArrayList<HostPort> peerList, DatagramSocket datagramSocket) {
        for (HostPort peer : peerList) {
            byte[] sendMessage = new byte[8192];
            try {
                sendMessage = message.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                log.info("message is not in UTF8");
            }
            try {
                InetAddress remoteHost = InetAddress.getByName(peer.host);
                log.info("handshake send to" + remoteHost.toString() + ":" + peer.port);
                DatagramPacket datagramPacket = new DatagramPacket(sendMessage, sendMessage.length, remoteHost, peer.port);
                DatagramPacket receivePacket = new DatagramPacket(new byte[8192], 8192);
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
                        datagramSocket.setSoTimeout(9000);
                        datagramSocket.receive(receivePacket);
                        if (receivePacket.getAddress().equals(remoteHost)) {
                            receivedResponse = true;
                            log.info("the message has been received");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (receivedResponse == false) {
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

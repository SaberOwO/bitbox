package unimelb.bitbox;


import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
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
    private static int blockSize = Integer.valueOf(Configuration.getConfigurationValue("blockSize"));
    private static int packageSize = Integer.valueOf(Configuration.getConfigurationValue("packetSize"));
    private static int syncInterval = Integer.valueOf(Configuration.getConfigurationValue("syncInterval"));
    private static int timeout = Integer.valueOf(Configuration.getConfigurationValue("timeout"));


    public PeerUDPLogic(DatagramSocket datagramSocket, FileSystemManager fileSystemManager,
                        ServerMain serverMain, boolean isFirst, ArrayList<HostPort> peerList,
                        int maxConnection, HostPort hostPort) {
        this.datagramSocket = datagramSocket;
        this.fileSystemManager = fileSystemManager;
        this.serverMain = serverMain;
        this.isFirst = isFirst;
        this.peerList = peerList;
        this.maxConnection = maxConnection;
        this.hostPort = hostPort;
    }


    public void run() {
        if (isFirst) {
            sendHandShakeRequest(datagramSocket, hostPort);
        }

        while (true) {
            byte[] data = new byte[packageSize];
            DatagramPacket receivedPacket = new DatagramPacket(data, data.length);
            try {
                datagramSocket.receive(receivedPacket);
                if (!receivedPacket.getAddress().equals(InetAddress.getByName(localIp))) {
                    handleLogic(datagramSocket, receivedPacket);
                    //   syncTimer();
                }
            } catch (IOException e) {
                // e.printStackTrace();
            }
        }
    }


    private void handleLogic(DatagramSocket datagramSocket, DatagramPacket receivedPacket) {
        try {
            String receivedData = new String(receivedPacket.getData(), 0, receivedPacket.getLength(), "UTF-8");
            Document message = Document.parse(receivedData);
            log.info("Received Message: " + message.toJson());
            switch (message.getString("command")) {
                case "INVALID_PROTOCOL":
                    log.info("INVALID_PROTOCOL has been received");
                    datagramSocket.close();
                    break;

                case "CONNECTION_REFUSED":
                    log.info("CONNECTION_REFUSED has been received");
                    handleHandShakeRefuse(datagramSocket, message);
                    datagramSocket.close();
                    break;

                case "HANDSHAKE_REQUEST":
                    log.info("HANDSHAKE_REQUEST has been received");
                    handleHandShakeRequest(datagramSocket, message);
                    break;

                case "HANDSHAKE_RESPONSE":
                    log.info("HANDSHAKE_RESPONSE has been received");
                    handleHandShakeResponse(datagramSocket, message);
                    break;

                case "FILE_CREATE_REQUEST":
                    log.info("FILE_CREATE_REQUEST");
                    handleFileCreateRequest(datagramSocket, message, receivedPacket);
                    break;

                case "FILE_CREATE_RESPONSE":
                    log.info("FILE_CREATE_RESPONSE");
                    break;

                case "FILE_DELETE_REQUEST":
                    log.info("FILE_DELETE_REQUEST has been received");
                    handleFileDeleteRequest(datagramSocket, message, receivedPacket);
                    break;

                case "FILE_DELETE_RESPONSE":
                    log.info("FILE_DELETE_RESPONSE has been received");
                    break;

                case "FILE_MODIFY_REQUEST":
                    log.info("FILE_MODIFY_REQUEST");
                    handleFileModifyRequest(datagramSocket, message, receivedPacket);
                    break;

                case "FILE_MODIFY_RESPONSE":
                    log.info("FILE_MODIFY_RESPONSE");
                    // Nothing needs to handle
                    break;

                case "FILE_BYTES_REQUEST":
                    log.info("FILE_BYTES_REQUEST");
                    handleFileBytesRequest(datagramSocket, receivedPacket, message);
                    break;

                case "FILE_BYTES_RESPONSE":
                    log.info("FILE_BYTES_RESPONSE");
                    handleFileBytesResponse(datagramSocket, receivedPacket, message);
                    break;

                case "DIRECTORY_CREATE_REQUEST":
                    log.info("DIRECTORY_CREATE_REQUEST has been received");
                    handleDirectoryCreateRequest(datagramSocket, message, receivedPacket);
                    break;

                case "DIRECTORY_CREATE_RESPONSE":
                    log.info("DIRECTORY_CREATE_RESPONSE has been received");
                    break;

                case "DIRECTORY_DELETE_REQUEST":
                    log.info("DIRECTORY_DELETE_REQUEST has been received");
                    handleDirectoryDeleteRequest(datagramSocket, message, receivedPacket);
                    break;

                case "DIRECTORY_DELETE_RESPONSE":
                    log.info("DIRECTORY_DELETE_RESPONSE has been received");
                    break;


            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    // (OK) Handle Handshake Response
    private void handleHandShakeResponse(DatagramSocket datagramSocket, Document message) {
        log.info("Handshake finished");
        HostPort remoteHostPort = new HostPort((Document) message.get("hostPort"));
        if (peerList.contains(remoteHostPort)) {
            return;
        }
        peerList.add(remoteHostPort);
        serverMain.peersMap.put(datagramSocket, peerList);
        //   syncTimer();
    }

    // (OK) Handle Handshake Refuse
    private void handleHandShakeRefuse(DatagramSocket datagramSocket, Document message) {
        log.info("Connection refused, trying to connect backup peer!");
        ArrayList<Document> peerList = (ArrayList<Document>) message.get("peers");
        for (Document peer : peerList) {
            sendHandShakeRequest(datagramSocket, new HostPort(peer));
        }
    }

    // (OK) Handle Handshake Request
    private void handleHandShakeRequest(DatagramSocket datagramSocket, Document message) {
        try {
            HostPort remoteHostPort = new HostPort((Document) message.get("hostPort"));
            if (peerList.contains(remoteHostPort)) {
                return;
            } else if (peerList.size() >= maxConnection) {
                sendHandShakeRefuse(datagramSocket, remoteHostPort);
            } else {
                sendHandShakeResponse(datagramSocket, remoteHostPort);
                peerList.add(remoteHostPort);
                serverMain.peersMap.put(datagramSocket, peerList);

                //    syncTimer();
            }
        } catch (Exception e) {
            HostPort remoteHostPort = new HostPort((Document) message.get("hostPort"));
            sendInvalidProtocol(datagramSocket, remoteHostPort);
        }
    }

    // (OK) Handle File Create Request
    private void handleFileCreateRequest(DatagramSocket datagramSocket, Document message, DatagramPacket receivedPacket) {
        try {
            Document FILE_CREATE_RESPONSE = constructFileCreateResponse(message);
            sendNormalResponse(datagramSocket, receivedPacket, FILE_CREATE_RESPONSE);

            if ((boolean) FILE_CREATE_RESPONSE.get("status")) {
                boolean flag_of_shortcut = fileSystemManager.checkShortcut((String) FILE_CREATE_RESPONSE.get("pathname"));
                if (flag_of_shortcut) {
                    log.info("File copied from local");
                } else {
                    HostPort remoteHostPort = new HostPort(receivedPacket.getAddress().getHostName(), receivedPacket.getPort());
                    Document FIRST_FILE_BYTE_RESPONSE = constructFileByteRequest(message, 0, blockSize);
                    sendRequest(datagramSocket, FIRST_FILE_BYTE_RESPONSE, remoteHostPort);
                }
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            System.out.println(e);
        }
    }

    private void handleFileDeleteRequest(DatagramSocket datagramSocket, Document message, DatagramPacket receivedPacket) {
        Document response = new Document();
        response.append("command", "FILE_DELETE_RESPONSE");
        response.append("fileDescriptor", constructFileDescriptor(message));
        String pathName = message.getString("pathName");
        Document descriptor = constructFileDescriptor(message);
        response.append("pathName", pathName);

        boolean flag = fileSystemManager.fileNameExists(pathName);
        if (!flag) {
            response.append("message", "path name does not exist");
            response.append("status", false);
            sendNormalResponse(datagramSocket, receivedPacket, response);
        }

        flag = fileSystemManager.isSafePathName(pathName);
        if (!flag) {
            response.append("message", "unsafe path name given");
            response.append("status", false);
            sendNormalResponse(datagramSocket, receivedPacket, response);
        }

        flag = fileSystemManager.deleteFile(pathName, descriptor.getLong("lastModified"), descriptor.getString("md5"));
        if (!flag) {
            response.append("message", "there was a problem deleting the file");
            response.append("status", false);
            sendNormalResponse(datagramSocket, receivedPacket, response);
        } else {
            response.append("message", "file deleted");
            response.append("status", true);
            sendNormalResponse(datagramSocket, receivedPacket, response);
        }
    }

    private void handleFileModifyRequest(DatagramSocket datagramSocket, Document message, DatagramPacket receivedPacket) {
        try {
            Document FILE_MODIFY_RESPONSE = constructFileModifyResponse(message);
            sendNormalResponse(datagramSocket, receivedPacket, FILE_MODIFY_RESPONSE);

            System.out.println(FILE_MODIFY_RESPONSE.toJson());

            if ((boolean) FILE_MODIFY_RESPONSE.get("status")) {
                HostPort remoteHostPort = new HostPort(receivedPacket.getAddress().getHostName(), receivedPacket.getPort());
                Document FIRST_FILE_BYTE_REQUEST = constructFileByteRequest(message, 0, blockSize);
                sendRequest(datagramSocket, FIRST_FILE_BYTE_REQUEST, remoteHostPort);
            }

        } catch (IOException | NoSuchAlgorithmException e) {
            System.out.println(e);
        }
    }

    private void handleDirectoryCreateRequest(DatagramSocket datagramSocket, Document message, DatagramPacket receivedPacket) {
        Document response = new Document();
        response.append("command", "DIRECTORY_CREATE_RESPONSE");
        String pathName = message.getString("pathName");
        response.append("pathName", pathName);
        boolean flag = fileSystemManager.dirNameExists(pathName);
        if (flag == true) {
            response.append("message", "pathname already exists");
            response.append("status", false);
            sendNormalResponse(datagramSocket, receivedPacket, response);
            return;
        }
        flag = fileSystemManager.isSafePathName(pathName);
        if (flag == false) {
            response.append("message", "unsafe pathname given");
            response.append("status", false);
            sendNormalResponse(datagramSocket, receivedPacket, response);
            return;
        }
        flag = fileSystemManager.makeDirectory(pathName);
        if (flag == false) {
            response.append("message", "there was a problem creating the directory");
            response.append("status", false);
            sendNormalResponse(datagramSocket, receivedPacket, response);
        } else {
            response.append("message", "directory created");
            response.append("status", true);
            sendNormalResponse(datagramSocket, receivedPacket, response);
        }
    }

    private void handleDirectoryDeleteRequest(DatagramSocket datagramSocket, Document message, DatagramPacket receivedPacket) {
        Document response = new Document();
        response.append("command", "DIRECTORY_DELETE_RESPONSE");
        String pathName = message.getString("pathName");
        response.append("pathName", pathName);
        boolean flag = fileSystemManager.dirNameExists(pathName);

        if (flag == false) {
            response.append("message", "path name does not exist");
            response.append("status", false);
            sendNormalResponse(datagramSocket, receivedPacket, response);
            return;
        }
        flag = fileSystemManager.isSafePathName(pathName);
        if (flag == false) {
            response.append("message", "unsafe path name given");
            response.append("status", false);
            sendNormalResponse(datagramSocket, receivedPacket, response);
            return;
        }
        flag = fileSystemManager.deleteDirectory(pathName);
        if (flag == false) {
            response.append("message", "there was a problem deleting the directory");
            response.append("status", false);
            sendNormalResponse(datagramSocket, receivedPacket, response);
        } else {
            response.append("message", "directory deleted");
            response.append("status", true);
            sendNormalResponse(datagramSocket, receivedPacket, response);
        }
    }

    private void handleFileBytesRequest(DatagramSocket datagramSocket, DatagramPacket receivedPacket, Document message) {
        try {
//            HostPort remoteHostPort = new HostPort(receivedPacket.getAddress().getHostName(), receivedPacket.getPort());
//            sendNormalResponse(datagramSocket, constructFileByteResponse(message), remoteHostPort);
            sendNormalResponse(datagramSocket, receivedPacket, constructFileByteResponse(message));
        } catch (IOException | NoSuchAlgorithmException e) {
            System.out.println(e);
        }
    }

    private void handleFileBytesResponse(DatagramSocket datagramSocket, DatagramPacket receivedPacket, Document message) {
        try {
            String encode_content = (String) message.get("content");
            ByteBuffer decode_content = ByteBuffer.wrap(Base64.getDecoder().decode(encode_content.getBytes()));
            Document file_bytes_fileDescriptor = (Document) message.get("fileDescriptor");
            String file_bytes_pathName = message.get("pathName").toString();

            long file_bytes_startPosition = (long) message.get("position");
            long content_length = (long) message.get("length");

            boolean flag_of_write = fileSystemManager.writeFile(file_bytes_pathName, decode_content, file_bytes_startPosition);
            boolean flag_of_complete = fileSystemManager.checkWriteComplete(file_bytes_pathName);

            if (!flag_of_complete) {
                HostPort remoteHostPort = new HostPort(receivedPacket.getAddress().getHostName(), receivedPacket.getPort());
                sendRequest(datagramSocket, constructFileByteRequest(message, file_bytes_startPosition + content_length, blockSize), remoteHostPort);
            } else {
                fileSystemManager.scanDirectoryTree(file_bytes_pathName);
                log.info("FILE READ SUCCESSFUL!");
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            System.out.println(e);
        }
    }

    private Document constructFileDescriptor(Document message) {
        return (Document) message.get("fileDescriptor");
    }

    private Document constructInvalidProtocol() {
        Document response = new Document();
        response.append("command", "INVALID_PROTOCOL");
        response.append("message", "message must contain a command field as string");
        return response;
    }

    private Document constructHandShakeRequest(HostPort hostPort) {
        //construct message in document
        Document request = new Document();
        request.append("command", "HANDSHAKE_REQUEST");
        Document hostinfo = new Document();
        hostinfo.append("host", hostPort.host);
        hostinfo.append("port", hostPort.port);
        request.append("hostPort", hostinfo);
        return request;
    }

    private Document constructFileCreateResponse(Document message) throws IOException, NoSuchAlgorithmException {
        Document response = new Document();
        String file_pathName = (String) message.get("pathName");
        Document file_create_fileDescriptor = constructFileDescriptor(message);
        String file_md5 = (String) file_create_fileDescriptor.get("md5");
        long file_create_fileSize = (long) file_create_fileDescriptor.get("fileSize");
        long file_create_lastModified = (long) file_create_fileDescriptor.get("lastModified");

        response.append("command", "FILE_CREATE_RESPONSE");
        response.append("fileDescriptor", file_create_fileDescriptor);
        response.append("pathName", file_pathName);

        boolean SF_flag = fileSystemManager.isSafePathName(file_pathName);
        boolean FN_flag = fileSystemManager.fileNameExists(file_pathName);
        boolean FC_flag = fileSystemManager.fileNameExists(file_pathName, file_md5);

        if (SF_flag) {
            boolean File_create_loder_flag = false;
            boolean File_modify_loder_flag = false;

            if (!FN_flag) {
                File_create_loder_flag = fileSystemManager.createFileLoader(file_pathName, file_md5, file_create_fileSize, file_create_lastModified);
            } else if (!FC_flag) {
                File_modify_loder_flag = fileSystemManager.modifyFileLoader(file_pathName, file_md5, file_create_lastModified);
            }

            if (File_create_loder_flag | File_modify_loder_flag) {
                response.append("status", true);
                response.append("message", "File create loader ready!");
                return response;
            } else {
                response.append("status", false);
                response.append("message", "File name already exists!");
                return response;
            }
        } else {
            response.append("status", false);
            response.append("message", "Unsafe path given!");
            return response;
        }
    }

    private Document constructFileModifyResponse(Document message) throws IOException, NoSuchAlgorithmException {
        Document response = new Document();

        String file_pathName = (String) message.get("pathName");
        Document file_create_fileDescriptor = constructFileDescriptor(message);
        String file_md5 = (String) file_create_fileDescriptor.get("md5");
        long file_create_lastModified = (long) file_create_fileDescriptor.get("lastModified");

        response.append("command", "FILE_MODIFY_RESPONSE");
        response.append("fileDescriptor", file_create_fileDescriptor);
        response.append("pathName", file_pathName);

        boolean SF_flag = fileSystemManager.isSafePathName(file_pathName);
//        boolean FN_flag = fileSystemManager.fileNameExists(file_pathName);
//        boolean FC_flag = fileSystemManager.fileNameExists(file_pathName, file_md5);


        if (SF_flag) {
            boolean File_modify_loder_flag;

            File_modify_loder_flag = fileSystemManager.modifyFileLoader(file_pathName, file_md5, file_create_lastModified);

            if (File_modify_loder_flag) {
                response.append("status", true);
                response.append("message", "File modify loader ready!");
                return response;
            } else {
                response.append("status", false);
                response.append("message", "there was a problem modifying the file!");
                return response;
            }

        } else {
            response.append("status", false);
            response.append("message", "Unsafe path given!");
            return response;
        }
    }

    private Document constructFileByteRequest(Document message, long position, long blockSize) {
        Document response = new Document();
        Document file_bytes_fileDescriptor = (Document) message.get("fileDescriptor");
        long fileSize = (long) file_bytes_fileDescriptor.get("fileSize");
        long length = fileSize - position;
        if (length > blockSize) length = blockSize;

        response.append("command", "FILE_BYTES_REQUEST");
        response.append("fileDescriptor", file_bytes_fileDescriptor);
        response.append("pathName", (String) message.get("pathName"));
        response.append("position", position);
        response.append("length", length);

        return response;
    }

    private Document constructFileByteResponse(Document requestBody) throws IOException, NoSuchAlgorithmException {
        Document response = new Document();

        Document file_bytes_fileDescriptor = (Document) requestBody.get("fileDescriptor");

        String file_bytes_md5 = (String) file_bytes_fileDescriptor.get("md5");
        String file_bytes_pathName = (String) requestBody.get("pathName");
        long file_bytes_startPosition = (long) requestBody.get("position");
        long file_bytes_length = (long) requestBody.get("length");

        response.append("command", "FILE_BYTES_RESPONSE");
        response.append("pathName", file_bytes_pathName);
        response.append("fileDescriptor", file_bytes_fileDescriptor);
        response.append("position", file_bytes_startPosition);
        response.append("length", file_bytes_length);

        // Read file
        ByteBuffer content = null;
        content = fileSystemManager.readFile(file_bytes_md5, file_bytes_startPosition, file_bytes_length);

        if (content == null) {
            response.append("message", "unsuccessful read");
            response.append("status", false);
            return response;
        } else {
            String encoded = Base64.getEncoder().encodeToString(content.array());
            response.append("content", encoded);
            response.append("message", "successful read");
            response.append("status", true);
            return response;
        }
    }

    private void syncTimer() {
        Runnable runnable = () -> {
            while (true) {
                syncIt();
                try {
                    Thread.sleep(syncInterval * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private void syncIt() {
        ArrayList<FileSystemManager.FileSystemEvent> eventList = fileSystemManager.generateSyncEvents();
        for (FileSystemManager.FileSystemEvent event : eventList) {
            serverMain.processFileSystemEvent(event);
        }
    }

    private void sendHandShakeRequest(DatagramSocket datagramSocket, HostPort hostPort) {
        try {
            String localHost = InetAddress.getLocalHost().getHostAddress();
            HostPort localHostPort = new HostPort(localHost,
                    datagramSocket.getLocalPort());
            Document message = constructHandShakeRequest(localHostPort);
            log.info("handshake request has been sent" + message.toString());
            sendRequest(datagramSocket, message, hostPort);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private void sendInvalidProtocol(DatagramSocket datagramSocket, HostPort hostPort) {
        Document message = constructInvalidProtocol();
        sendHandshakeResponse(datagramSocket, message, hostPort);
    }

    private void sendHandShakeResponse(DatagramSocket datagramSocket, HostPort remoteHostPort) {
        Document response = new Document();
        response.append("command", "HANDSHAKE_RESPONSE");
        response.append("hostPort", new HostPort(localIp, localPort).toDoc());
        System.out.println("Handshake response Sent: " + response.toJson());
        sendHandshakeResponse(datagramSocket, response, remoteHostPort);
    }

    private void sendHandShakeRefuse(DatagramSocket datagramSocket, HostPort remoteHostPort) {
        Document response = new Document();
        response.append("command", "CONNECTION_REFUSED");
        response.append("message", "connection limit reached");
        ArrayList<Document> peers = new ArrayList<>();
        for (HostPort peer : peerList) {
            peers.add(peer.toDoc());
        }
        response.append("peers", peers);
        sendHandshakeResponse(datagramSocket, response, remoteHostPort);
    }

    //send response of file or document request
    private void sendNormalResponse(DatagramSocket datagramSocket, DatagramPacket datagramPacket, Document info) {
        byte[] message = new byte[packageSize];
        try {
            message = info.toJson().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.info("message is not in UTF8");
        }
        DatagramPacket sendPacket = new DatagramPacket(message, message.length, datagramPacket.getAddress(), datagramPacket.getPort());
        try {
            log.info("Response send to " + datagramPacket.getAddress() + ":" + datagramPacket.getPort());
            log.info("Response Content: " + info.toJson());
            datagramSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //handshake related response
    private void sendHandshakeResponse(DatagramSocket datagramSocket, Document response, HostPort remoteHostPort) {
        byte[] message = new byte[packageSize];

        try {
            message = response.toJson().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.info("message is not in UTF8");
        }

        try {
            InetAddress remotehostAddress = InetAddress.getByName(remoteHostPort.host);
            DatagramPacket datagramPacket = new DatagramPacket(message, message.length, remotehostAddress, remoteHostPort.port);
            try {
                datagramSocket.send(datagramPacket);
                log.info("the handshake response is already sent");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    //send handshake request info
    private void sendRequest(DatagramSocket datagramSocket, Document info, HostPort hostPort) {
        byte[] message = new byte[packageSize];
        try {
            message = info.toJson().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.info("message is not in UTF8");
        }
        try {
            InetAddress remoteHost = InetAddress.getByName(hostPort.host);
            log.info("Request send to " + remoteHost.toString() + ":" + hostPort.port);
            log.info("Request Content: " + info.toJson());

            DatagramPacket datagramPacket = new DatagramPacket(message, message.length, remoteHost, hostPort.port);
            DatagramPacket receivePacket = new DatagramPacket(new byte[packageSize], packageSize);
            boolean receivedResponse = false;
            int tryTimes = 0;
            while (!receivedResponse && tryTimes < 5) {
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
                        datagramSocket.setSoTimeout(0);
                        handleLogic(datagramSocket, receivePacket);
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


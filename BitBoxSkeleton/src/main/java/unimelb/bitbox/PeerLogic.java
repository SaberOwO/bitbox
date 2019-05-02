package unimelb.bitbox;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

public class PeerLogic extends Thread {
    private Socket socket;
    private HostPort myPort;
    private HostPort otherPort;
    private boolean isFirst;
    private HashMap<Socket, BufferedWriter> socketWriter;
    private HashMap<Socket, BufferedReader> socketReader;
    private FileSystemManager fileSystemManager;
    private ServerMain serverMain;
    private static Logger log = Logger.getLogger(ServerMain.class.getName());
    private ArrayList<HostPort> peerList;
    private int maxConnection;

    public PeerLogic(Socket socket, HostPort myPort,
                     HashMap<Socket, BufferedWriter> socketWriter,
                     HashMap<Socket, BufferedReader> socketReader,
                     FileSystemManager fileSystemManager, ServerMain serverMain,
                     boolean isFirst, ArrayList<HostPort> peerList, int maxConnection) {
        this.socket = socket;
        this.myPort = myPort;
        this.socketWriter = socketWriter;
        this.socketReader = socketReader;
        this.fileSystemManager = fileSystemManager;
        this.serverMain = serverMain;
        this.isFirst = isFirst;
        this.serverMain = serverMain;
        this.fileSystemManager = fileSystemManager;
        this.peerList = peerList;
        this.maxConnection = maxConnection;
    }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            socketReader.put(socket, in);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            socketWriter.put(socket, out);
            if (isFirst) {
                sendHandShakeRequest(myPort, out);
            }
            handleLogic(in, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // send handshake request if it is run as client
    private void sendHandShakeRequest(HostPort myPort, BufferedWriter out) {
        sendInfo(constructHandShakeRequestJson(myPort), out);
    }

    private Document constructHandShakeRequestJson(HostPort myPort) {
        Document request = new Document();
        request.append("command", "HANDSHAKE_REQUEST");
        Document hostPort = new Document();
        hostPort.append("host", myPort.host);
        hostPort.append("port", myPort.port);
        request.append("hostPort", hostPort);
        return request;
    }

    private void handleLogic(BufferedReader in, BufferedWriter out) throws IOException {
        String tempo;
        while((tempo = in.readLine()) != null) {
            Document message = Document.parse(tempo);
            switch(message.getString("command")) {
                case "INVALID_PROTOCOL":
                    log.info(message.getString("message"));
                    // 不确定
                    socket.close();
                    break;

                case "CONNECTION_REFUSED":
                    handleHandShakeRefuse(message, out);
                    // 不确定
                    socket.close();
                    break;

                case "HANDSHAKE_RESPONSE":
                    handleHandShakeResponse();
                    break;

                case "HANDSHAKE_REQUEST":
                    log.info(message.toJson());
                    handleHandShakeRequest(message, out);
                    break;

                case "FILE_CREATE_REQUEST":
                    break;

                case "FILE_CREATE_RESPONSE":
                    log.info(message.getString("message"));
                    break;

                case "FILE_BYTES_REQUEST":
                    break;

                case "FILE_BYTES_RESPONSE":
                    break;

                case "FILE_DELETE_REQUEST":
                    log.info("handle request");
                    log.info(message.toJson());
                    handleFileDeleteRequest(message, out);
                    break;

                case "FILE_DELETE_RESPONSE":
                    log.info(message.getString("message"));
                    break;

                case "FILE_MODIFY_REQUEST":
                    break;

                case "FILE_MODIFY_RESPONSE":
                    //不确定
                    log.info(message.getString("message"));
                    break;

                case "DIRECTORY_CREATE_REQUEST":
                    break;

                case "DIRECTORY_CREATE_RESPONSE":
                    log.info(message.getString("message"));
                    break;

                case "DIRECTORY_DELETE_REQUEST":
                    log.info("handle request");
                    log.info(message.toJson());
                    handleDirectoryDeleteRequest(message, out);
                    break;

                case "DIRECTORY_DELETE_RESPONSE":
                    log.info(message.getString("message"));
                    break;
            }
        }
    }

    // sync the folder
    private void syncIt() {
        ArrayList<FileSystemManager.FileSystemEvent> eventList = fileSystemManager.generateSyncEvents();
        for (FileSystemManager.FileSystemEvent event: eventList) {
            serverMain.processFileSystemEvent(event);
        }
    }

    // handle the refuse situation
    private void handleHandShakeRefuse(Document message, BufferedWriter out) {
        log.info("Connection refused, trying to connect backup peer!");
        ArrayList<Document> peerList = (ArrayList<Document>) message.get("peers");
        for (Document peer: peerList) {
            sendHandShakeRequest(new HostPort(peer), out);
        }
    }

    // handle the hand shake response
    private void handleHandShakeResponse() {
        log.info("Handshake finished");
        syncIt();
    }

    // handle the hand shake request
    private void handleHandShakeRequest(Document message, BufferedWriter out) {
        try {
            HostPort newOne = new HostPort((Document) message.get("hostPort"));
            if (peerList.contains(newOne)) {
                return;
            } else if (peerList.size() >= maxConnection) {
                constructConnectionRefuse(out);
            } else {
                constructHandShakeResponse(out);
                peerList.add(newOne);
                syncIt();
            }
        } catch (Exception e) {
            constructInvalidProtocol(out);
        }
    }

    // handle the file delete request
    private void handleFileDeleteRequest(Document message, BufferedWriter out) {
        Document response = new Document();
        response.append("command", "FILE_DELETE_RESPONSE");
        response.append("fileDescriptor", constructFileDescriptor(message));
        String pathName = message.getString("message");
        Document descriptor = constructFileDescriptor(message);
        response.append("pathName", pathName);
        boolean flag = fileSystemManager.fileNameExists(pathName);
        if (flag == false) {
            response.append("message", "path name does not exist");
            response.append("status", false);
            sendInfo(response, out);
        }
        flag = fileSystemManager.isSafePathName(pathName);
        if (flag == false) {
            response.append("message", "unsafe path name given");
            response.append("status", false);
            sendInfo(response, out);
        }
        flag = fileSystemManager.deleteFile(pathName, descriptor.getLong("lastModified"),
                descriptor.getString("md5"));
        if (flag == false) {
            response.append("message", "there was a problem deleting the file");
            response.append("status", false);
            sendInfo(response, out);
        } else {
            response.append("message", "file deleted");
            response.append("status", true);
            sendInfo(response, out);
        }
    }

    // handle the directory delete request
    private void handleDirectoryDeleteRequest(Document message, BufferedWriter out) {
        Document response = new Document();
        response.append("command", "DIRECTORY_DELETE_RESPONSE");
        String pathName = message.getString("pathName");
        response.append("pathName", pathName);
        boolean flag = fileSystemManager.dirNameExists(pathName);
        if (flag == false) {
            response.append("message", "path name does not exist");
            response.append("status", false);
            sendInfo(response, out);
        }
        flag = fileSystemManager.isSafePathName(pathName);
        if (flag == false) {
            response.append("message", "unsafe path name given");
            response.append("status", false);
            sendInfo(response, out);
        }
        flag = fileSystemManager.deleteDirectory(pathName);
        if (flag == false) {
            response.append("message", "there was a problem deleting the directory");
            response.append("status", false);
            sendInfo(response, out);
        } else {
            response.append("message", "directory deleted");
            response.append("status", true);
            sendInfo(response, out);
        }
    }

    // invalid response
    private void constructInvalidProtocol(BufferedWriter out) {
        Document response = new Document();
        response.append("command", "INVALID_PROTOCOL");
        response.append("message", "message must contain a command field as string");
        sendInfo(response, out);
    }

    // connection refuse response
    private void constructConnectionRefuse(BufferedWriter out) {
        Document response = new Document();
        response.append("command", "CONNECTION_REFUSED");
        response.append("message", "connection limit reached");
        ArrayList<Document> peers = new ArrayList<>();
        for (HostPort peer: peerList) {
            peers.add(peer.toDoc());
        }
        response.append("peers", peers);
        sendInfo(response, out);
    }

    // connection success
    private void constructHandShakeResponse(BufferedWriter out) {
        Document response = new Document();
        response.append("command", "HANDSHAKE_RESPONSE");
        Document hostPort = new Document();
        hostPort.append("host", myPort.toDoc());
        response.append("hostPort", hostPort);
        sendInfo(response, out);
    }

    // construct file descriptor
    private Document constructFileDescriptor(Document message) {
        Document fileDescriptor = (Document) message.get("fileDescriptor");
        return fileDescriptor;
    }

    // send the information
    private void sendInfo(Document info, BufferedWriter out) {
        try {
            out.write(info.toJson());
            out.newLine();
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

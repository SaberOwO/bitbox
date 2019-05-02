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

    public PeerLogic(Socket socket, HostPort myPort,
                     HashMap<Socket, BufferedWriter> socketWriter,
                     HashMap<Socket, BufferedReader> socketReader,
                     FileSystemManager fileSystemManager, ServerMain serverMain,
                     boolean isFirst) {
        this.socket = socket;
        this.myPort = myPort;
        this.socketWriter = socketWriter;
        this.socketReader = socketReader;
        this.fileSystemManager = fileSystemManager;
        this.serverMain = serverMain;
        this.isFirst = isFirst;
        this.serverMain = serverMain;
        this.fileSystemManager = fileSystemManager;
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
                    break;

                case "CONNECTION_REFUSED":
                    handleHandShakeRefuse(message, out);
                    break;

                case "HANDSHAKE_RESPONSE":
                    handleHandShakeResponse();
                    break;

                case "HANDSHAKE_REQUEST":
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
                    break;

                case "FILE_MODIFY_REQUEST":
                    break;

                case "FILE_MODIFY_RESPONSE":
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

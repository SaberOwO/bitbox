package unimelb.bitbox;

import org.json.simple.JSONObject;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import unimelb.bitbox.util.Configuration;
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
    private static int blockSize = Integer.valueOf(Configuration.getConfigurationValue("blockSize"));
    private static int syncInterval = Integer.valueOf(Configuration.getConfigurationValue("syncInterval"));

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
            try {
                handleLogic(in, out);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
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

    private void handleLogic(BufferedReader in, BufferedWriter out) throws IOException, NoSuchAlgorithmException{
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
                    Document FILE_CREATE_RESPONSE = constructFileCreateResponse(message);
                    sendInfo(FILE_CREATE_RESPONSE, out);

                    if((boolean)FILE_CREATE_RESPONSE.get("status")){
                        boolean flag_of_shortcut = fileSystemManager.checkShortcut((String)FILE_CREATE_RESPONSE.get("pathname"));
                        if (flag_of_shortcut){
                            log.info("File copied from local");
                        }
                        else{
                            Document FIRST_FILE_BYTE_RESPONSE = constructFileByteRequest(message, 0, blockSize);
                            sendInfo(FIRST_FILE_BYTE_RESPONSE, out);
                        }
                    }
                    break;

                case "FILE_CREATE_RESPONSE":
                    log.info(message.getString("message"));
                    break;

                case "FILE_BYTES_REQUEST":
                    log.info("FILE_BYTES_REQUEST");
                    log.info(message.toJson());
                    sendInfo(constructFileByteResponse(message), out);
                    break;

                case "FILE_BYTES_RESPONSE":
                    log.info("FILE_BYTES_RESPONSE");

                    ByteBuffer bb = (ByteBuffer) message.get("content");
                    String file_bytes_pathName = message.get("pathName").toString();
                    Document file_bytes_fileDescriptor = (Document) message.get("fileDescriptor");
                    long file_bytes_startPosition =  (long) file_bytes_fileDescriptor.get("position");
                    long content_length =  (long) file_bytes_fileDescriptor.get("length");

                    boolean flag_of_write = fileSystemManager.writeFile(file_bytes_pathName, bb, file_bytes_startPosition);
                    boolean flag_of_complete = fileSystemManager.checkWriteComplete(file_bytes_pathName);

                    if (!flag_of_complete) {
                        sendInfo(constructFileByteRequest(message, file_bytes_startPosition + content_length, blockSize), out);
                    }
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
                    log.info("handle request");
                    log.info(message.toJson());
                    handleDirectoryCreateRequest(message, out);
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
//        Runnable runnable = ()-> {
//            while(true) {
//                syncIt();
//                try {
//                    Thread.sleep(syncInterval);
//                }catch(InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        };
//        Thread thread = new Thread(runnable);
//        thread.start();
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
//                Runnable runnable = ()-> {
//                    while(true) {
//                        syncIt();
//                        try {
//                            Thread.sleep(syncInterval);
//                        }catch(InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                };
//                Thread thread = new Thread(runnable);
//                thread.start();
            }
        } catch (Exception e) {
            constructInvalidProtocol(out);
        }
    }

    private Document constructFileCreateResponse(Document message) throws IOException, NoSuchAlgorithmException{
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

        if (SF_flag){
            boolean File_create_loder_flag = false;
            boolean File_modify_loder_flag = false;

            if (!FN_flag){
                File_create_loder_flag = fileSystemManager.createFileLoader(file_pathName, file_md5, file_create_fileSize, file_create_lastModified);
            }
            else if (!FC_flag){
                File_modify_loder_flag = fileSystemManager.modifyFileLoader(file_pathName, file_md5, file_create_lastModified);
            }

            if (File_create_loder_flag | File_modify_loder_flag){
                response.append("status", true);
                response.append("message", "File create loader ready!");
                return response;
            }
            else{
                response.append("status", false);
                response.append("message", "File loader cannot create!");
                return response;
            }
        }
        else{
            response.append("status", false);
            response.append("message", "Unsafe path given!");
            return response;
        }
    }

    private Document constructFileByteRequest(Document message, long position, long blockSize) throws IOException, NoSuchAlgorithmException{
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
        String file_bytes_pathName = (String) file_bytes_fileDescriptor.get("pathName");
        long file_bytes_startPosition =  (long) file_bytes_fileDescriptor.get("position");
        long file_bytes_fileSize = (long)file_bytes_fileDescriptor.get("fileSize");

        response.append("command", "FILE_BYTES_RESPONSE");
        response.append("pathName", file_bytes_pathName);
        response.append("fileDescriptor", file_bytes_fileDescriptor);
        response.append("position", file_bytes_startPosition);
        response.append("length", file_bytes_fileSize);

        // Read file
        ByteBuffer content = null;
        content = fileSystemManager.readFile(file_bytes_md5, file_bytes_startPosition, file_bytes_fileSize);

        if (content == null){
            response.append("message", "unsuccessful read");
            response.append("status", false);
            return response;
        }
        else {
            String encoded = Base64.getEncoder().encodeToString(content.array());
            response.append("content", encoded);
            response.append("message", "successful read");
            response.append("status", true);
            return response;
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

    private void handleDirectoryCreateRequest(Document message,BufferedWriter out) {
        Document response = new Document();
        response.append("command", "DIRECTORY_CREATE_RESPONSE");
        String pathName = message.getString("pathName");
        response.append("pathName", pathName);
        boolean flag = fileSystemManager.dirNameExists(pathName);
        if (flag == true) {
            response.append("message", "pathname already exists");
            response.append("status", false);
            sendInfo(response, out);
            return ;
        }
        flag = fileSystemManager.isSafePathName(pathName);
        if (flag == false) {
            response.append("message", "unsafe pathname given");
            response.append("status", false);
            sendInfo(response, out);
            return ;
        }
        flag = fileSystemManager.makeDirectory(pathName);
        if(flag == false) {
            response.append("message", "there was a problem creating the directory");
            response.append("status",false);
            sendInfo(response, out);
        }else{
            response.append("message","directory created");
            response.append("status",true);
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
            return ;
        }
        flag = fileSystemManager.isSafePathName(pathName);
        if (flag == false) {
            response.append("message", "unsafe path name given");
            response.append("status", false);
            sendInfo(response, out);
            return ;
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

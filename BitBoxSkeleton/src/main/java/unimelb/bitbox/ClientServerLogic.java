package unimelb.bitbox;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public class ClientServerLogic extends Thread {
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
    ClientServerLogic(Socket socket, HostPort myPort,
                      HashMap<Socket, BufferedWriter> socketWriter,
                      HashMap<Socket, BufferedReader> socketReader,
                      ArrayList<HostPort> peerList, HashMap<String, String> keymap, ExecutorService tpool,
                      FileSystemManager fileSystemManager, ServerMain serverMain,int maxConnection,  boolean isFirst){
        this.socket = socket;
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
    }

    public void run() {

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "utf-8"));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "utf-8"));
            try {
                handleLogic(in, out);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleLogic(BufferedReader in, BufferedWriter out) throws Exception, IOException, NoSuchAlgorithmException {
        String originalMessage;
        while ((originalMessage = in.readLine()) != null) {
            System.out.println("haha");
            Document message = Document.parse(originalMessage);
            if (message.containsKey("command")){
                log.info("AUTH_REQUEST");
                log.info(message.toJson());
                handleAuth_Response(message, out);
            }
            else{
                log.info("PayLoad");
                Cipher cipher = Cipher.getInstance("AES");
                System.out.println(secretKey);
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
                String encrypted = (String)message.get("payload");

                System.out.println( Base64.getEncoder().encodeToString(secretKey.getEncoded()));
                System.out.println(encrypted);
                byte[] aaa = Base64.getDecoder().decode(encrypted);
                System.out.println(aaa.length);
                String secretMessage = new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
                Document decryptDoc = Document.parse(secretMessage);
                switch (decryptDoc.getString("command")) {
                    case "LIST_PEERS_REQUEST":
                        log.info("LIST_PEERS_REQUEST");
                        log.info(decryptDoc.toJson());
                        handleListPeers_Response(decryptDoc, out);
                        break;
                    case "CONNECT_PEER_REQUEST":
                        log.info("CONNECT_PEER_REQUEST");
                        log.info(decryptDoc.toJson());
                        handleConnectPeer_Response(decryptDoc, out);
                        break;
                    case "DISCONNECT_PEER_REQUEST":
                        log.info("DISCONNECT_PEER_REQUEST");
                        log.info(decryptDoc.toJson());
                        handleDisConnectPeer_Response(decryptDoc, out);
                        break;
                }
            }

        }
    }

    private void handleAuth_Response(Document message, BufferedWriter out) throws IOException, Exception, InvalidKeySpecException, NoSuchAlgorithmException {
        String identity = (String) message.get("identity");
        Document response = new Document();
        response.append("command", "AUTH_RESPONSE");
        if (keymap.containsKey(identity)){
            response.append("status", true);
            response.append("message", "public key found");
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            secretKey = keyGen.generateKey();
            byte[] encodedSKey = secretKey.getEncoded();
            PublicKey pk = getPublicKey(keymap.get(identity));
            String content = new String(encodedSKey, ISO_8859_1);
            System.out.println( Base64.getEncoder().encodeToString(encodedSKey));
            byte[] encoded = Base64.getEncoder().encode(publicEnrypy(content, pk));
            String finencoded = new String(encoded, ISO_8859_1);
//            System.out.println(encoded);

            response.append("AES128",finencoded);
            sendInfo(response, out);
        }
        else{
            response.append("status", false);
            response.append("message", "public key not found");
            sendInfo(response, out);
            socket.close();
        }
    }
    private void handleListPeers_Response(Document message, BufferedWriter out) throws IOException, Exception, InvalidKeySpecException, NoSuchAlgorithmException {
        Document response = new Document();
        response.append("command", "LIST_PEERS_RESPONSE");
        ArrayList<Document> peers = new ArrayList<>();
        for (HostPort peer: peerList) {
            peers.add(peer.toDoc());
        }
        response.append("peers", peers);
        sendInfo(AESEncryption(response), out);
    }
    private void handleConnectPeer_Response(Document message, BufferedWriter out) throws IOException, Exception, InvalidKeySpecException, NoSuchAlgorithmException {
        String host = message.getString("host");
        long port = message.getLong("port");
        HostPort hp = new HostPort(host,(int)port);
        Document response = new Document();
        response.append("command", "CONNECT_PEER_RESPONSE");
        response.append("host", host);
        response.append("port", port);
        if (peerList.contains(hp)){
            response.append("status", false);
            response.append("message", "connection already exists");
            sendInfo(AESEncryption(response), out);
            return;
        }
        peerList.add(hp);
        Socket newPeer = new Socket(host, (int)port);
        PeerLogic pl = new PeerLogic(newPeer, myPort,
                socketWriter, socketReader, serverMain.fileSystemManager, serverMain, true, peerList, maxConnection);
        pl.start();
        sleep(2000);

        if (peerList.contains(hp)){
            response.append("status", true);
            response.append("message", "connected to peer");
        }
        else{
            response.append("status", false);
            response.append("message", "connection failed");
        }
        sendInfo(AESEncryption(response), out);
    }
    private void handleDisConnectPeer_Response(Document message, BufferedWriter out) throws IOException, Exception, InvalidKeySpecException, NoSuchAlgorithmException {
        System.out.println(message.toJson());
        String host = message.getString("host");
        long port = message.getLong("port");
        Document response = new Document();
        response.append("command", "DISCONNECT_PEER_RESPONSE");
        response.append("host", host);
        response.append("port", port);
        HostPort hp = new HostPort(host,(int)port);
        if (peerList.contains(hp)){
            peerList.remove(hp);
            response.append("status", true);
            response.append("message", "disconnected from peer");
        }
        else{
            response.append("status", false);
            response.append("message", "connection not active");
        }
        sendInfo(AESEncryption(response), out);
    }
    public static byte[] publicEnrypy(String express,PublicKey pub) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, pub);// 设置为加密模式
        byte[] result = cipher.doFinal(express.getBytes(ISO_8859_1));// 对数据进行加密
        return result;//返回密文
    }

    private void sendInfo(Document info, BufferedWriter out) {
        try {
            out.write(info.toJson());
            out.newLine();
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private PublicKey getPublicKey(String identity)throws Exception{
        byte[] load = Base64.getDecoder().decode(identity.getBytes());
        byte[] exponentByt = subByte(load,15,3);
        byte[] moduleLengthByt = subByte(load,18,4);

        int moduleLengthInt = Integer.parseInt(bytes2HexString(moduleLengthByt),16);
        BigInteger exponent = new BigInteger(bytes2HexString(exponentByt), 16);
        byte[] moduleByt = subByte(load,22,moduleLengthInt);
        BigInteger module = new BigInteger(bytes2HexString(moduleByt),16);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey pk = keyFactory.generatePublic(new RSAPublicKeySpec(module, exponent));
        return pk;
    }

    private byte[] subByte(byte[] b,int off,int length){
        byte[] b1 = new byte[length];
        System.arraycopy(b, off, b1, 0, length);
        return b1;
    }

    private String bytes2HexString(byte[] b) {
        String r = "";

        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            r += hex.toUpperCase();
        }

        return r;
    }
    private Document AESEncryption(Document response)throws Exception{
        Document finalResponse = new Document();
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        String secretMessage = new String(cipher.doFinal(response.toJson().getBytes(ISO_8859_1)),ISO_8859_1);
        String encoded = new String(Base64.getEncoder().encode(secretMessage.getBytes(ISO_8859_1)),ISO_8859_1);


        System.out.println(cipher.doFinal(response.toJson().getBytes(ISO_8859_1)).length);
        System.out.println(secretMessage);
        System.out.println(secretMessage.length());
        System.out.println(encoded);
        System.out.println(encoded.length());
        finalResponse.append("payload",encoded);
        return finalResponse;
    }

}



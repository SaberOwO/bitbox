package unimelb.bitbox;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import unimelb.bitbox.util.ArgsReader;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.PrivateKeyReader;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.logging.Logger;

public class Client {

    private static Logger log = Logger.getLogger(Client.class.getName());
    private static final int MAX_DECRYPT_BLOCK = 512;
    public static void main(String[] args) {
        //Object that will store the parsed command line arguments
        ArgsReader argsBean = new ArgsReader();
        //Parser provided by args4j
        CmdLineParser parser = new CmdLineParser(argsBean);
        try {

            //Parse the arguments
            parser.parseArgument(args);
            //After parsing, the fields in argsBean have been updated with the given
            //command line arguments
            String request = constructRequestJSON(argsBean);
            HostPort serverPort = new HostPort(argsBean.getServerPort());
            // get the private key
            try {
                PrivateKey myPrivateKey = new PrivateKeyReader("D:\\Study\\S2\\COMP90015 Distributed Systems\\BITBOX\\bitbox\\BitBoxSkeleton\\src\\main\\resources\\private_key.pem").generatePrivateKey();
                runClient(serverPort, request, myPrivateKey, argsBean);
            } catch (IOException e) {
                log.warning("fail to get the private key");
                e.printStackTrace();
            }

        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            //Print the usage to help the user understand the arguments expected
            //by the program
            parser.printUsage(System.err);
        }
    }

    private static void runClient(HostPort serverPort, String request, PrivateKey privateKey, ArgsReader argsBean) {
        try {
            SecretKey sessionKey;
            Socket socket = new Socket(serverPort.host, serverPort.port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "utf-8"));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "utf-8"));
            String authRequest = constructAuthRequestJSON(argsBean);
            sendIt(authRequest, out);
            sessionKey = handleAuthLogic(in, privateKey);
            if (sessionKey != null) {
                String businessRequest = constructPayload(request, sessionKey);
                sendIt(businessRequest, out);
                log.info("business request sent");
                log.info(businessRequest);
                handleBusinessLogic(sessionKey, in);
            } else {
                log.warning("wrong session key");
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleBusinessLogicHelper(String message) {
        Document document = Document.parse(message);
        String command = document.getString("command");
        switch (command) {
            case "LIST_PEERS_RESPONSE":
                handleListPeersResponse(document);
                break;
            case "CONNECT_PEER_RESPONSE":
                handleConnectPeerResponse(document);
                break;
            case "DISCONNECT_PEER_RESPONSE":
                handleDisconnectPeerResponse(document);
                break;
        }
    }

    public static void handleListPeersResponse(Document document) {
        ArrayList<Document> peers = (ArrayList<Document>) document.get("peers");
        System.out.println("peers list: ");
        for (Document peer: peers) {
            HostPort hostPort = new HostPort(peer);
            System.out.println(hostPort.toString());
        }
    }

    public static void handleConnectPeerResponse(Document document) {
        System.out.println(document.getString("message"));
    }

    public static void handleDisconnectPeerResponse(Document document) {
        System.out.println(document.getString("message"));
    }

    public static void handleBusinessLogic(SecretKey sessionKey, BufferedReader in) {
        try {
            String responseMessage = in.readLine();
            System.out.println(responseMessage);
            Document response = Document.parse(responseMessage);
            String payload = response.getString("payload");
            System.out.println(payload);
            log.info("received response " + payload);
            //System.out.println(Base64.getDecoder().decode(payload).length);
            byte[] hehe = Base64.getDecoder().decode(payload);
            //String decodedResponse = new String(Base64.getDecoder().decode(payload), "ISO_8859_1");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, sessionKey);
            //byte[] tempo = cipher.doFinal(decodedResponse.getBytes("ISO_8859_1"));
            byte[] tempo = cipher.doFinal(hehe);
            String message = new String(tempo, "ISO_8859_1");
            System.out.println(message);
            //Document document = Document.parse(message);
            handleBusinessLogicHelper(message);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
    }

    public static String constructPayload(String request, SecretKey sessionKey) throws UnsupportedEncodingException {
        Document payload = new Document();
        //Key secretKey = new SecretKeySpec(sessionKey.getBytes("ISO_8859_1"), "AES");
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey);
            String secretMessage = new String(cipher.doFinal(request.getBytes("ISO_8859_1")), "ISO_8859_1");
            payload.append("payload", Base64.getEncoder().encodeToString(secretMessage.getBytes("ISO_8859_1")));
        } catch (NoSuchAlgorithmException e) {
            log.warning("payload construction error");
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            log.warning("payload construction error");
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            log.warning("payload construction error");
            e.printStackTrace();
        } catch (BadPaddingException e) {
            log.warning("payload construction error");
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            log.warning("payload construction error");
            e.printStackTrace();
        }
        return payload.toJson();
    }

    private static SecretKey handleAuthLogic(BufferedReader in, PrivateKey privateKey) throws IOException {
        String responseMessage = in.readLine();
        SecretKey sessionKey;
        Document message = Document.parse(responseMessage);
        if (message.getBoolean("status") == false) {
            log.info(message.getString("message"));
            return null;
        } else {
            byte[] sessionByte = Base64.getDecoder().decode(message.getString("AES128"));
            sessionKey = decryptBytesByPrivateKey(sessionByte, privateKey);
            return sessionKey;
        }
    }

    private static SecretKey decryptBytesByPrivateKey(byte[] tempo, PrivateKey privateKey) {
        SecretKey key = null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int offSet = 0;
            byte[] cache;
            int i = 0;
            int inputLen = tempo.length;
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            while (inputLen - offSet > 0) {
                if (inputLen - offSet > MAX_DECRYPT_BLOCK) {
                    cache = cipher.doFinal(tempo, offSet, MAX_DECRYPT_BLOCK);
                } else {
                    cache = cipher.doFinal(tempo, offSet, inputLen - offSet);
                }
                out.write(cache, 0, cache.length);
                i++;
                offSet = i * MAX_DECRYPT_BLOCK;
            }
            byte[] decryptedData = out.toByteArray();
            //System.out.println(new String(decryptedData, "ISO_8859_1"));
            //System.out.println(decryptedData.length);
            //log.info("The session key is " + decryptedData.length);
            //log.info(Base64.getEncoder().encodeToString(decryptedData));
            //sessionKey = new String(decryptedData, "ISO_8859_1");
            log.info(Base64.getEncoder().encodeToString(decryptedData));
            key = new SecretKeySpec(decryptedData, 0, decryptedData.length, "AES");
            out.close();
        } catch (Exception e) {
            log.warning("cannot decode it");
            e.printStackTrace();
        }
        return key;
    }

    public static String constructRequestJSON(ArgsReader argsBean) {
        Document request = new Document();
        String command  = argsBean.getCommand();
        HostPort clientPort;
        switch (command) {
            case "list_peers":
                request.append("command", "LIST_PEERS_REQUEST");
                break;
            case "connect_peer":
                clientPort = new HostPort(argsBean.getClientPort());
                request.append("command", "CONNECT_PEER_REQUEST");
                request.append("host", clientPort.host);
                request.append("port", clientPort.port);
                break;
            case "disconnect_peer":
                clientPort = new HostPort(argsBean.getClientPort());
                request.append("command", "DISCONNECT_PEER_REQUEST");
                request.append("host", clientPort.host);
                request.append("port", clientPort.port);
                break;
            default:
                log.warning("wrong command");
                break;
        }
        return request.toJson();
    }

    public static String constructAuthRequestJSON(ArgsReader argsBean) {
        Document request  = new Document();
        request.append("command", "AUTH_REQUEST");
        request.append("identity", argsBean.getClientId());
        return request.toJson();
    }

    public static void sendIt(String message, BufferedWriter out) {
        try {
            out.write(message);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            log.warning("There are some problems in sending message");
            e.printStackTrace();
        }
    }
}

package unimelb.bitbox;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.logging.Logger;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	private HashMap<Socket, BufferedWriter> socketWriter;

	public ServerMain(HashMap<Socket, BufferedWriter> socketWriter) throws NumberFormatException, IOException,
			NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
		this.socketWriter = socketWriter;
	}

	// By analyze the information of fileSystemEvent to construct and send the request
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		try {
			switch (fileSystemEvent.event) {
				case FILE_CREATE:
					sendIt(constructCreateFileJson(fileSystemEvent));
					log.info("FILE_CREATE message sent!");
					break;

				case FILE_DELETE:
					sendIt(constructDeleteFileJson(fileSystemEvent));
					log.info("FILE_DELETE message sent!");
					break;

				case FILE_MODIFY:
					sendIt(constructModifyFileJson(fileSystemEvent));
					log.info("FILE_MODIFY message sent!");
					break;

				case DIRECTORY_CREATE:
					sendIt(constructCreateDirectory(fileSystemEvent));
					log.info("DIRECTORY_CREATE message sent!");
					break;

				case DIRECTORY_DELETE:
					sendIt(constructDeleteDirectory(fileSystemEvent));
					log.info("DIRECTORY_DELETE message sent!");
					break;

				default:
					log.warning("Wrong request");
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}

	// send the information
	private void sendIt(String message) {
		for (Socket socket: socketWriter.keySet()) {
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
}

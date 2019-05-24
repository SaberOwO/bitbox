package unimelb.bitbox.util;

import org.kohsuke.args4j.Option;

public class ArgsReader {

    // get the host ip address and port number
    @Option(required = true, name = "-s", usage = "Hostname + Portnumber")
    private String serverPort;

    // get the identification of client
    @Option(required = true, name = "-i", usage = "identifiable client")
    private String clientId;

    // get the command input by client
    @Option(required = true, name = "-c", usage = "Command")
    private String command;

    @Option(required = false, name = "-p", usage = "")
    private String clientPort = "";

    public String getServerPort() {
        return serverPort;
    }

    public String getCommand() {
        return command;
    }

    public String getClientPort() {
        return clientPort;
    }

    public String getClientId() {return  clientId;}
}


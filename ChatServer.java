import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Distributed Chat Server Node
 * Supports: multiple clients, message broadcasting, peer-to-peer server relay,
 *           message persistence (logging), and fault-tolerant reconnection.
 */
public class ChatServer {
    private final int port;
    private final String serverName;
    private final int peerPort;

    // Thread-safe set of connected clients
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    // Logging (message persistence)
    private PrintWriter logWriter;

    // Peer server connection state
    private Socket peerSocket;
    private PrintWriter peerOut;
    private BufferedReader peerIn;
    private volatile boolean peerConnected = false;

    public ChatServer(int port, String serverName) {
        this.port = port;
        this.serverName = serverName;
        // If we're on 5000, peer is on 5001; if on 5001, peer is on 5000
        this.peerPort = (port == 5000) ? 5001 : 5000;
    }

    public void start() {
        try {
            // Open log file for message persistence
            logWriter = new PrintWriter(
                new FileWriter("chat_log_" + port + ".txt", true), true);
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("[" + serverName + "] Listening on port " + port);
            log("Server " + serverName + " started on port " + port);

            // Only the server on the LOWER port initiates the peer connection.
            // This prevents duplicate peer links when both servers start together.
            if (port < peerPort) {
                new Thread(this::connectToPeer).start();
            }

            // Accept loop
            while (true) {
                Socket socket = serverSocket.accept();
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
                String firstLine = in.readLine();

                if (firstLine == null) {
                    socket.close();
                    continue;
                }

                if (firstLine.equals("PEER_HELLO")) {
                    // Incoming peer server connection
                    handleIncomingPeer(socket, in);
                } else {
                    // Regular client connection: first line = username
                    ClientHandler handler = new ClientHandler(
                        socket, this, firstLine, in);
                    clients.add(handler);
                    new Thread(handler).start();
                    broadcast("[" + firstLine + "] joined the chat.", null);
                }
            }
        } catch (IOException e) {
            System.err.println("[" + serverName + "] Fatal: " + e.getMessage());
        }
    }

    // ---------- Peer-to-peer relay ----------

    private void connectToPeer() {
        while (!peerConnected) {
            try {
                peerSocket = new Socket("localhost", peerPort);
                peerOut = new PrintWriter(peerSocket.getOutputStream(), true);
                peerOut.println("PEER_HELLO");          // handshake
                peerIn = new BufferedReader(
                    new InputStreamReader(peerSocket.getInputStream()));
                peerConnected = true;
                System.out.println("[" + serverName + "] Connected to peer on port " + peerPort);

                new Thread(() -> readFromPeer(peerIn)).start();
                return;
            } catch (IOException e) {
                // Peer not up yet — retry every 2 seconds
                try { Thread.sleep(2000); } catch (InterruptedException ie) { return; }
            }
        }
    }

    private void handleIncomingPeer(Socket socket, BufferedReader in) {
        try {
            peerSocket = socket;
            peerOut = new PrintWriter(socket.getOutputStream(), true);
            peerIn = in;
            peerConnected = true;
            System.out.println("[" + serverName + "] Peer handshake accepted");
            new Thread(() -> readFromPeer(in)).start();
        } catch (IOException e) {
            System.err.println("[" + serverName + "] Peer setup failed");
        }
    }

    private void readFromPeer(BufferedReader in) {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                // Relay peer messages to local clients only (no loop back)
                for (ClientHandler c : clients) c.sendMessage(msg);
            }
        } catch (IOException e) {
            System.out.println("[" + serverName + "] Peer connection lost");
        } finally {
            peerConnected = false;
            // Attempt to re-establish from the connector side
            if (port < peerPort) new Thread(this::connectToPeer).start();
        }
    }

    // ---------- Broadcasting ----------

    /** Broadcast to all local clients (except sender) and to peer server. */
    public synchronized void broadcast(String message, ClientHandler sender) {
        log(message);
        for (ClientHandler c : clients) {
            if (c != sender) c.sendMessage(message);
        }
        if (peerConnected && peerOut != null) peerOut.println(message);
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        broadcast("[" + client.getClientName() + "] left the chat.", client);
    }

    // ---------- Persistence ----------

    private synchronized void log(String message) {
        if (logWriter != null) {
            logWriter.println("[" + new java.util.Date() + "] " + message);
        }
    }

    // ---------- Entry point ----------

    public static void main(String[] args) {
        int port     = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        String name  = args.length > 1 ? args[1] : "Server-" + port;
        new ChatServer(port, name).start();
    }
}

/**
 * Handles one client connection (thread-per-connection model).
 */
class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private final String clientName;
    private final BufferedReader in;
    private PrintWriter out;

    public ClientHandler(Socket socket, ChatServer server,
                         String name, BufferedReader in) {
        this.socket = socket;
        this.server = server;
        this.clientName = name;
        this.in = in;
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getClientName() { return clientName; }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                server.broadcast("[" + clientName + "]: " + line, this);
            }
        } catch (IOException e) {
            System.out.println("Client " + clientName + " dropped: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignore) {}
            server.removeClient(this);
        }
    }

    public void sendMessage(String msg) {
        if (out != null) out.println(msg);
    }
}

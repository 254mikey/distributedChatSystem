import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

/**
 * Distributed Chat Client with Swing GUI.
 * Features: automatic failover between servers, status bar, chat history.
 */
public class ChatClient extends JFrame {
    // ---- GUI ----
    private final JTextArea chatArea = new JTextArea();
    private final JTextField messageField = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JLabel statusLabel = new JLabel(" Disconnected");

    // ---- Networking ----
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private String currentHost;
    private int currentPort;
    private volatile boolean connected = false;
    private volatile boolean reconnecting = false;

    // Server pool (for failover)
    private static final String[] SERVERS = {"localhost", "localhost"};
    private static final int[]    PORTS   = {5000, 5001};

    public ChatClient() {
        setTitle("Distributed Chat Application");
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        chatArea.setEditable(false);
        chatArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        chatArea.setBackground(new Color(245, 245, 245));
        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Chat Messages"));
        add(scroll, BorderLayout.CENTER);

        JPanel input = new JPanel(new BorderLayout(5, 5));
        input.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        messageField.setFont(new Font("Arial", Font.PLAIN, 14));
        sendButton.setBackground(new Color(0, 120, 215));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Arial", Font.BOLD, 12));
        input.add(messageField, BorderLayout.CENTER);
        input.add(sendButton, BorderLayout.EAST);
        add(input, BorderLayout.SOUTH);

        statusLabel.setForeground(Color.RED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(statusLabel, BorderLayout.NORTH);

        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        SwingUtilities.invokeLater(this::connectToServer);
    }

    // ---------- Connection & Failover ----------

    private void connectToServer() {
        username = JOptionPane.showInputDialog(this,
            "Enter your username:", "Login", JOptionPane.PLAIN_MESSAGE);
        if (username == null || username.trim().isEmpty()) System.exit(0);

        for (int i = 0; i < PORTS.length; i++) {
            if (tryConnect(SERVERS[i], PORTS[i])) return;
        }
        chatArea.append("*** No servers available. Start a server first. ***\n");
    }

    private boolean tryConnect(String host, int port) {
        try {
            socket = new Socket(host, port);
            currentHost = host;
            currentPort = port;
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(username);                  // login handshake
            connected = true;
            updateStatus();
            chatArea.append("*** Connected to " + host + ":" + port + " ***\n");
            new Thread(this::receiveLoop).start();
            return true;
        } catch (IOException e) {
            chatArea.append("*** Could not connect to " + host + ":" + port + " ***\n");
            return false;
        }
    }

    private void receiveLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                chatArea.append(line + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
        } catch (IOException e) {
            if (!reconnecting) {
                chatArea.append("*** Connection lost. Reconnecting... ***\n");
                connected = false;
                updateStatus();
                attemptReconnect();
            }
        }
    }

    private void attemptReconnect() {
        reconnecting = true;
        for (int i = 0; i < PORTS.length; i++) {
            if (tryConnect(SERVERS[i], PORTS[i])) {
                reconnecting = false;
                return;
            }
        }
        chatArea.append("*** Reconnection failed. ***\n");
        reconnecting = false;
    }

    private void sendMessage() {
        if (!connected) { chatArea.append("*** Not connected ***\n"); return; }
        String msg = messageField.getText().trim();
        if (!msg.isEmpty()) {
            out.println(msg);
            messageField.setText("");
            messageField.requestFocus();
        }
    }

    private void updateStatus() {
        if (connected) {
            statusLabel.setText(" Connected | " + currentHost + ":" + currentPort +
                                " | User: " + username);
            statusLabel.setForeground(new Color(0, 128, 0));
        } else {
            statusLabel.setText(" Disconnected");
            statusLabel.setForeground(Color.RED);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClient().setVisible(true));
    }
}

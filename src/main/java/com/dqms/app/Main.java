package com.dqms.app;

import com.dqms.db.DatabaseManager;
import com.dqms.model.Message;
import com.dqms.model.NodeInfo;
import com.dqms.network.TCPClient;
import com.dqms.network.TCPServer;
import com.dqms.network.UDPDiscoveryService;
import com.dqms.queue.QueueManager;
import com.dqms.ui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class Main extends Application {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static QueueManager queueManager;
    public static String       nodeId;
    public static int          tcpPort;
    public static boolean      isAdmin;

    @Override
    public void start(Stage stage) throws Exception {
        var params = getParameters().getRaw();
        tcpPort = params.size() > 0 ? Integer.parseInt(params.get(0)) : 5001;
        nodeId  = params.size() > 1 ? params.get(1) : "NODE_001";

        LOG.info("=== Starting DQMS Node: " + nodeId + " on port " + tcpPort + " ===");

        // ── 1. Init core components ──────────────────────────────────────────
        DatabaseManager db     = new DatabaseManager(nodeId);
        TCPClient       client = new TCPClient();
        Map<String, NodeInfo> peers = new ConcurrentHashMap<>();

        queueManager = new QueueManager(nodeId, tcpPort, db, client, peers, isAdmin);
        queueManager.loadFromDatabase();

        // ── 3. Start TCP server ──────────────────────────────────────────────
        TCPServer server = new TCPServer(tcpPort, queueManager);
        Thread serverThread = new Thread(server, "tcp-server");
        serverThread.setDaemon(true);
        serverThread.start();

        UDPDiscoveryService discovery = new UDPDiscoveryService(
                nodeId, tcpPort, isAdmin, peers,
                peer -> {
                    // Called when a NEW peer is discovered for the first time
                    LOG.info("New peer found: " + peer + " — sending SYNC_REQUEST");
                    Message response = client.requestSync(peer, nodeId);
                    if (response != null && response.getTicketList() != null) {
                        queueManager.applySyncResponse(response.getTicketList());
                    }
                }
        );
        Thread discoveryThread = new Thread(discovery, "udp-discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();

        // ── 5. Wait for peer discovery ───────────────────────────────────────
        LOG.info("Waiting 3s for peer discovery...");
        Thread.sleep(3000);

        // ── 6. Launch JavaFX UI ──────────────────────────────────────────────
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/dqms/ui/main.fxml"));
        Scene scene = new Scene(loader.load(), 860, 620);

        // Apply stylesheet
        scene.getStylesheets().add(
                getClass().getResource("/com/dqms/ui/style.css").toExternalForm());

        MainController controller = loader.getController();
        controller.init(queueManager);

        stage.setTitle("DQMS — " + nodeId);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        launch(args);
    }
}

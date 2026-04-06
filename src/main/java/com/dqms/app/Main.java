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

        isAdmin = params.stream().anyMatch(p -> "ADMIN".equalsIgnoreCase(p));
        if (!isAdmin && params.isEmpty()) {
            isAdmin = "NODE_001".equalsIgnoreCase(nodeId);
        }

        LOG.info("Starting DQMS Node: " + nodeId + " (Admin: " + isAdmin + ") on port " + tcpPort);

        DatabaseManager db = new DatabaseManager(nodeId);
        TCPClient client = new TCPClient();
        client.setMyTcpPort(tcpPort);
        Map<String, NodeInfo> peers = new ConcurrentHashMap<>();

        queueManager = new QueueManager(nodeId, tcpPort, db, client, peers, isAdmin);
        queueManager.loadFromDatabase();

        new Thread(new TCPServer(tcpPort, queueManager), "tcp-server").start();

        UDPDiscoveryService discovery = new UDPDiscoveryService(
                nodeId, tcpPort, isAdmin, peers,
                peer -> {
                    Message response = client.requestSync(peer, nodeId, isAdmin);
                    if (response != null && response.getTicketList() != null) {
                        queueManager.applySyncResponse(response.getTicketList());
                    }
                }
        );
        new Thread(discovery, "udp-discovery").start();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/dqms/ui/dqms-main.fxml"));
        Scene scene = new Scene(loader.load(), 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/com/dqms/ui/style.css").toExternalForm());

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

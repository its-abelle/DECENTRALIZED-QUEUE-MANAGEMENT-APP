<<<<<<< HEAD
package com.dqms.ui;

import com.dqms.model.Ticket;
import com.dqms.queue.QueueManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * JavaFX controller for the admin dashboard.
 *
 * Two-way binding:
 *   QueueManager notifies this controller via setOnQueueChanged callback
 *   → refreshTable() is called on the JavaFX thread via Platform.runLater()
 */
public class MainController {

    // ── FXML bindings ─────────────────────────────────────────────────────────
    @FXML private Label     nodeLabel;
    @FXML private Label     statusLabel;
    @FXML private TextField regNumberField;
    @FXML private TextField studentNameField;
    @FXML private Button    joinButton;
    @FXML private Button    clearBtn;
    @FXML private Label     joinFeedback;
    @FXML private Label     waitingCount;
    @FXML private Label     clearedCount;
    @FXML private Label     peerCount;
    @FXML private Label     syncLog;

    @FXML private TableView<Ticket>          queueTable;
    @FXML private TableColumn<Ticket,Number> posCol;
    @FXML private TableColumn<Ticket,String> regCol;
    @FXML private TableColumn<Ticket,String> nameCol;
    @FXML private TableColumn<Ticket,String> nodeCol;
    @FXML private TableColumn<Ticket,String> timeCol;
    @FXML private TableColumn<Ticket,String> statusCol;

    private QueueManager queueManager;
    private final ObservableList<Ticket> tableData = FXCollections.observableArrayList();
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Called by Main.java after the FXML is loaded.
     */
    public void init(QueueManager qm) {
        this.queueManager = qm;

        // Set node label
        nodeLabel.setText(qm.getNodeId());

        // Configure table columns
        posCol.setCellValueFactory(cd -> {
            int idx = tableData.indexOf(cd.getValue()) + 1;
            return new SimpleIntegerProperty(idx);
        });
        regCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getRegistrationNumber()));
        nameCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getStudentName()));
        nodeCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getOriginNodeId()));
        timeCol.setCellValueFactory(cd ->
                new SimpleStringProperty(TIME_FMT.format(
                        Instant.ofEpochMilli(cd.getValue().getIssueTime()))));
        statusCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getStatus()));

        // Color-code status column
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    if ("WAITING".equals(status)) {
                        setStyle("-fx-text-fill: #C07000; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #1A7F4B; -fx-font-weight: bold;");
                    }
                }
            }
        });

        queueTable.setItems(tableData);

        // Enable "Mark Cleared" only when a WAITING row is selected AND current node is authorized
        queueTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            clearBtn.setDisable(!canClear(sel));
        });

        // Register callback for when queue changes (called from network threads)
        queueManager.setOnQueueChanged(() ->
                Platform.runLater(this::refreshTable));

        // Auto-refresh peer status every 2s
        Timeline peerRefresh = new Timeline(
                new KeyFrame(Duration.seconds(2), e -> refreshPeerStatus()));
        peerRefresh.setCycleCount(Timeline.INDEFINITE);
        peerRefresh.play();

        // Initial load
        refreshTable();
    }

    // ── Event Handlers ────────────────────────────────────────────────────────

    @FXML
    private void onJoinQueue() {
        String reg  = regNumberField.getText().trim();
        String name = studentNameField.getText().trim();

        if (reg.isEmpty() || name.isEmpty()) {
            joinFeedback.setStyle("-fx-text-fill: #CC0000;");
            joinFeedback.setText("Please fill in both fields.");
            return;
        }

        Ticket t = queueManager.createTicket(reg, name);
        if (t == null) {
            joinFeedback.setStyle("-fx-text-fill: #CC0000;");
            joinFeedback.setText("Error: You already have an active ticket.");
            return;
        }

        // Find actual global position in queue
        List<Ticket> allSystem = queueManager.getUnfilteredTickets();
        List<Ticket> allWaiting = allSystem.stream()
                .filter(ticket -> "WAITING".equals(ticket.getStatus()))
                .sorted()
                .toList();
                
        int pos = allWaiting.indexOf(t) + 1;

        joinFeedback.setStyle("-fx-text-fill: #1A7F4B;");
        joinFeedback.setText("✓ Joined queue! Position: #" + pos);

        regNumberField.clear();
        studentNameField.clear();

        // Refresh to disable inputs
        refreshTable();

        log("New ticket created: " + reg + " (pos #" + pos + ")");
    }

    @FXML
    private void onClearSelected() {
        Ticket selected = queueTable.getSelectionModel().getSelectedItem();
        if (selected == null || !"WAITING".equals(selected.getStatus())) return;

        queueManager.clearStudent(selected.getTicketId());
        clearBtn.setDisable(true);
        log("Cleared: " + selected.getRegistrationNumber() + " by " + queueManager.getNodeId());
    }

    @FXML
    private void onRefresh() {
        queueManager.loadFromDatabase();
        log("Manual refresh (reloaded from DB) at " + TIME_FMT.format(Instant.now()));
    }

    // ── UI Update Helpers ─────────────────────────────────────────────────────

    private void refreshTable() {
        List<Ticket> allVisible = queueManager.getAllTicketsAsList();
        tableData.setAll(allVisible);

        // Calculate totals based on the FULL system queue, not just the visible ones
        List<Ticket> allSystem = queueManager.getUnfilteredTickets();
        long waiting = allSystem.stream().filter(t -> "WAITING".equals(t.getStatus())).count();
        long cleared = allSystem.stream().filter(t -> "CLEARED".equals(t.getStatus())).count();

        waitingCount.setText(String.valueOf(waiting));
        clearedCount.setText(String.valueOf(cleared));

        // Enforce one-ticket-per-node: disable join inputs if node has active ticket
        boolean hasActive = queueManager.hasActiveTicket();
        joinButton.setDisable(hasActive);
        regNumberField.setDisable(hasActive);
        studentNameField.setDisable(hasActive);
        if (hasActive) {
            joinFeedback.setText("You are currently in the queue.");
            joinFeedback.setStyle("-fx-text-fill: #1A7F4B;");
        }

        // Re-check clear button state
        Ticket sel = queueTable.getSelectionModel().getSelectedItem();
        clearBtn.setDisable(!canClear(sel));
    }

    private boolean canClear(Ticket ticket) {
        if (ticket == null || !"WAITING".equals(ticket.getStatus())) return false;

        // ONLY Admin can clear tickets. Regular nodes can only view.
        return queueManager.isAdmin();
    }

    private void refreshPeerStatus() {
        int count = queueManager.getPeerCount();
        peerCount.setText(String.valueOf(count));
        String roleStr = queueManager.isAdmin() ? "ADMIN" : "REGULAR";
        statusLabel.setText(String.format("● %s | %d peer(s)", roleStr, count));
        statusLabel.setStyle(count > 0
                ? "-fx-text-fill: #90EE90;"
                : "-fx-text-fill: #FFB74D;");
    }

    private void log(String msg) {
        syncLog.setText("[" + TIME_FMT.format(Instant.now()) + "] " + msg);
    }
}
=======
package com.dqms.ui;

import com.dqms.db.DatabaseManager;
import com.dqms.model.Message;
import com.dqms.model.NodeInfo;
import com.dqms.model.Ticket;
import com.dqms.network.TCPClient;
import com.dqms.network.TCPServer;
import com.dqms.network.UDPDiscoveryService;
import com.dqms.queue.QueueManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class MainController {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML private VBox loginScreen, studentScreen, adminScreen;
    @FXML private VBox roleStudent, roleAdmin;
    @FXML private VBox studentFields, staffFields, portField;
    @FXML private TextField loginRegField, loginStaffId, loginPort, studentRegField, studentNameField, regNumberField, studentNameFieldAdmin;
    @FXML private PasswordField loginPassword;
    @FXML private Label loginError, studentNamePill, studentError, ticketPosition, ticketRegLabel, ticketTimeLabel, ticketStatus, aheadLabel;
    @FXML private Label nodeLabel, statusLabel, adminSubLabel, adminPill, joinFeedback, waitingCount, clearedCount, peerCount, syncLog, syncLogBar;
    @FXML private VBox ticketBox;
    @FXML private ProgressBar queueProgress;
    @FXML private TableView<Ticket> queueTable;
    @FXML private TableColumn<Ticket, Number> posCol;
    @FXML private TableColumn<Ticket, String> regCol, nameCol, nodeCol, timeCol, statusCol;
    @FXML private Button joinButton, clearBtn, navQueue, navPeers, navLog, getTicketBtn;
    @FXML private HBox tabQueue;
    @FXML private VBox tabPeers, tabLog, peerListContainer;
    @FXML private StackPane dashboardPopup;
    @FXML private Label popupWaiting, popupCleared, popupPeers, popupNode, popupSyncLog;
    @FXML private HBox horizontalQueue;

    // State
    private QueueManager queueManager;
    private final ObservableList<Ticket> tableData = FXCollections.observableArrayList();
    private String currentRole = "STUDENT";
    private String activeRegNumber = null;

    public void init(QueueManager qm) {
        this.queueManager = qm;
        setupTables();
        
        Timeline peerRefresh = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            refreshPeerStatus();
            updatePeerLists();
        }));
        peerRefresh.setCycleCount(Timeline.INDEFINITE);
        peerRefresh.play();

        queueManager.setOnQueueChanged(() -> Platform.runLater(this::refreshTable));

        if (com.dqms.app.Main.isAdmin) {
            currentRole = "ADMIN";
            adminSubLabel.setText("Admin: " + qm.getNodeId());
            showScreen(adminScreen);
        } else if (qm.getNodeId() != null && !qm.getNodeId().equals("NODE_001") && !qm.getNodeId().equals("ADMIN")) {
            currentRole = "STUDENT";
            activeRegNumber = qm.getNodeId().startsWith("STU_") ? qm.getNodeId().substring(4) : qm.getNodeId();
            studentNamePill.setText(activeRegNumber);
            studentRegField.setText(activeRegNumber);
            showScreen(studentScreen);
        } else {
            showScreen(loginScreen);
            onSelectStudent();
        }

        if (nodeLabel != null) nodeLabel.setText(qm.getNodeId());
        refreshTable();
    }

    private void setupTables() {
        posCol.setCellValueFactory(cd -> new SimpleIntegerProperty(tableData.indexOf(cd.getValue()) + 1));
        regCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getRegistrationNumber()));
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getStudentName()));
        nodeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getOriginNodeId()));
        timeCol.setCellValueFactory(cd -> new SimpleStringProperty(TIME_FMT.format(Instant.ofEpochMilli(cd.getValue().getIssueTime()))));
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getStatus()));

        queueTable.setItems(tableData);
        queueTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (clearBtn != null) clearBtn.setDisable(!canClear(sel));
        });
    }

    private void showScreen(VBox screen) {
        loginScreen.setVisible(false); loginScreen.setManaged(false);
        studentScreen.setVisible(false); studentScreen.setManaged(false);
        adminScreen.setVisible(false); adminScreen.setManaged(false);
        
        if (screen != null) {
            screen.setVisible(true);
            screen.setManaged(true);
        }
    }

    @FXML private void onSelectStudent() {
        currentRole = "STUDENT";
        updateRoleUI(roleStudent, studentFields, false, false);
    }

    @FXML private void onSelectFinance() {
        log("Finance role consolidated into Admin.");
    }

    @FXML private void onSelectAdmin() {
        currentRole = "ADMIN";
        updateRoleUI(roleAdmin, staffFields, true, true);
    }

    private void updateRoleUI(VBox selectedBox, VBox fields, boolean showPort, boolean isStaff) {
        if (roleStudent != null) roleStudent.setStyle("-fx-background-color: white; -fx-border-color: #c8eef7; -fx-border-width: 2; -fx-border-radius: 10;");
        if (roleAdmin != null) roleAdmin.setStyle("-fx-background-color: white; -fx-border-color: #c8eef7; -fx-border-width: 2; -fx-border-radius: 10;");
        
        if (selectedBox != null) {
            selectedBox.setStyle("-fx-background-color: #e4f9fd; -fx-border-color: #13C9F0; -fx-border-width: 2; -fx-border-radius: 10;");
        }

        if (studentFields != null) { studentFields.setVisible(!isStaff); studentFields.setManaged(!isStaff); }
        if (staffFields != null) { staffFields.setVisible(isStaff); staffFields.setManaged(isStaff); }
        if (portField != null) { portField.setVisible(showPort); portField.setManaged(showPort); }
    }

    @FXML private void onLogin() {
        String nodeId = "";
        int port = 5001;
        boolean isAdmin = false;

        if ("STUDENT".equals(currentRole)) {
            String reg = loginRegField.getText().trim();
            if (reg.isEmpty()) { loginError.setText("Enter registration number."); return; }
            nodeId = "STU_" + reg.replaceAll("[^a-zA-Z0-9]", "");
            activeRegNumber = reg;
        } else if ("ADMIN".equals(currentRole)) {
            nodeId = loginStaffId.getText().trim();
            if (nodeId.isEmpty() || loginPassword.getText().isEmpty()) { loginError.setText("Enter ID and password."); return; }
            isAdmin = true;
            try { port = Integer.parseInt(loginPort.getText()); } catch (Exception e) { port = 5001; }
        }

        startSystem(nodeId, port, isAdmin);
        com.dqms.app.Main.isAdmin = isAdmin;

        if ("STUDENT".equals(currentRole)) {
            studentNamePill.setText(activeRegNumber);
            studentRegField.setText(activeRegNumber);
            showScreen(studentScreen);
        } else {
            adminSubLabel.setText("Admin: " + nodeId);
            showScreen(adminScreen);
        }
        refreshTable();
    }

    @FXML private void onLogout() {
        showScreen(loginScreen);
    }

    private void startSystem(String nodeId, int port, boolean isAdmin) {
        if (queueManager == null || !queueManager.getNodeId().equals(nodeId)) {
            DatabaseManager db = new DatabaseManager(nodeId);
            TCPClient client = new TCPClient();
            client.setMyTcpPort(port);
            queueManager = new QueueManager(nodeId, port, db, client, new ConcurrentHashMap<>(), isAdmin);
            queueManager.loadFromDatabase();

            new Thread(new TCPServer(port, queueManager)).start();

            new Thread(new UDPDiscoveryService(nodeId, port, isAdmin, new ConcurrentHashMap<>(), p -> {
                Message res = client.requestSync(p, nodeId, isAdmin);
                if (res != null && res.getTicketList() != null) queueManager.applySyncResponse(res.getTicketList());
            })).start();

            queueManager.setOnQueueChanged(() -> Platform.runLater(this::refreshTable));
            if (nodeLabel != null) nodeLabel.setText(nodeId);
        }
    }

    @FXML private void onGetTicket() {
        String reg = studentRegField.getText().trim();
        String name = studentNameField.getText().trim();
        if (reg.isEmpty() || name.isEmpty()) { studentError.setText("Enter registration number and name."); return; }
        
        activeRegNumber = reg;
        studentNamePill.setText(name);

        Ticket t = queueManager.createTicket(reg, name);
        if (t == null) {
            studentError.setText("You already have an active ticket.");
        }

        refreshTable();
        studentError.setText("");
    }

    private void updateTicketView(Ticket t) {
        if (t == null) {
            ticketBox.setVisible(false);
            ticketBox.setManaged(false);
            return;
        }

        ticketBox.setVisible(true);
        ticketBox.setManaged(true);

        if ("WAITING".equals(t.getStatus())) {
            List<Ticket> waiting = queueManager.getUnfilteredTickets().stream()
                    .filter(tk -> "WAITING".equals(tk.getStatus()))
                    .sorted()
                    .toList();
            
            int pos = -1;
            for (int i = 0; i < waiting.size(); i++) {
                if (waiting.get(i).getTicketId().equals(t.getTicketId())) {
                    pos = i + 1;
                    break;
                }
            }

            ticketPosition.setText(pos != -1 ? "#" + pos : "#?");
            ticketStatus.setText("WAITING");
            ticketStatus.setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
            ticketTimeLabel.setText(TIME_FMT.format(Instant.ofEpochMilli(t.getIssueTime())));
            
            int ahead = (pos > 0) ? pos - 1 : 0;
            aheadLabel.setText(ahead == 0 ? "You are next!" : ahead + " students ahead of you");
            queueProgress.setProgress(waiting.isEmpty() ? 1.0 : 1.0 - ((double)ahead / Math.max(1, waiting.size())));
        } else {
            ticketPosition.setText("OK");
            ticketStatus.setText("CLEARED");
            ticketStatus.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
            aheadLabel.setText("You have been served.");
            queueProgress.setProgress(1.0);
        }
    }

    @FXML private void onJoinQueue() {
        String reg = regNumberField.getText().trim();
        String name = studentNameFieldAdmin.getText().trim();
        if (reg.isEmpty() || name.isEmpty()) { joinFeedback.setText("Fill all fields."); return; }

        Ticket t = queueManager.createTicket(reg, name);
        if (t != null) {
            joinFeedback.setText("Added successfully.");
            regNumberField.clear(); studentNameFieldAdmin.clear();
        } else {
            joinFeedback.setText("Student already in queue.");
        }
        refreshTable();
    }

    @FXML private void onClearSelected() {
        Ticket sel = queueTable.getSelectionModel().getSelectedItem();
        if (sel != null && "WAITING".equals(sel.getStatus())) {
            queueManager.clearStudent(sel.getTicketId());
            log("Cleared student: " + sel.getRegistrationNumber());
        }
    }

    @FXML private void onResetQueue() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Reset entire queue?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                queueManager.clearQueue();
                log("QUEUE RESET.");
            }
        });
    }

    @FXML private void onRefresh() {
        queueManager.loadFromDatabase();
        log("Queue reloaded.");
    }

    @FXML private void onNavQueue() { showTab(tabQueue, navQueue); }
    @FXML private void onNavPeers() { showTab(tabPeers, navPeers); }
    @FXML private void onNavLog() { showTab(tabLog, navLog); }

    private void showTab(Pane tab, Button navBtn) {
        tabQueue.setVisible(false); tabQueue.setManaged(false);
        tabPeers.setVisible(false); tabPeers.setManaged(false);
        tabLog.setVisible(false); tabLog.setManaged(false);
        tab.setVisible(true); tab.setManaged(true);

        navQueue.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        navPeers.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        navLog.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        navBtn.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white;");
    }

    @FXML private void onShowDashboard() {
        dashboardPopup.setVisible(true); dashboardPopup.setManaged(true);
        updateDashboardPopup();
    }

    @FXML private void onCloseDashboard() {
        dashboardPopup.setVisible(false); dashboardPopup.setManaged(false);
    }

    private void updateDashboardPopup() {
        List<Ticket> all = queueManager.getUnfilteredTickets();
        popupWaiting.setText(String.valueOf(all.stream().filter(t -> "WAITING".equals(t.getStatus())).count()));
        popupCleared.setText(String.valueOf(all.stream().filter(t -> "CLEARED".equals(t.getStatus())).count()));
        popupPeers.setText(String.valueOf(queueManager.getPeerCount()));
        popupNode.setText(queueManager.getNodeId());
        
        horizontalQueue.getChildren().clear();
        all.stream().filter(t -> "WAITING".equals(t.getStatus())).limit(5).forEach(t -> {
            VBox card = new VBox(new Label("#" + t.getRegistrationNumber()));
            card.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-background-radius: 5;");
            horizontalQueue.getChildren().add(card);
        });
    }

    private void refreshTable() {
        List<Ticket> all = queueManager.getUnfilteredTickets();
        tableData.setAll(queueManager.getAllTicketsAsList());
        
        if (waitingCount != null) waitingCount.setText(String.valueOf(all.stream().filter(t -> "WAITING".equals(t.getStatus())).count()));
        if (clearedCount != null) clearedCount.setText(String.valueOf(all.stream().filter(t -> "CLEARED".equals(t.getStatus())).count()));

        if ("STUDENT".equals(currentRole) && activeRegNumber != null) {
            Ticket myLatest = all.stream()
                .filter(tk -> tk.getRegistrationNumber().equals(activeRegNumber))
                .sorted(Comparator.comparingLong(Ticket::getIssueTime).reversed())
                .findFirst().orElse(null);
            
            updateTicketView(myLatest);
        }
    }

    private boolean canClear(Ticket t) {
        return t != null && "WAITING".equals(t.getStatus()) && "ADMIN".equals(currentRole);
    }

    private void refreshPeerStatus() {
        if (queueManager == null) return;
        int count = queueManager.getPeerCount();
        if (peerCount != null) peerCount.setText(String.valueOf(count));
        if (statusLabel != null) {
            statusLabel.setText(String.format("● %s | %d peer(s)", "ADMIN".equals(currentRole) ? "ADMIN" : "REGULAR", count));
            statusLabel.setStyle(count > 0 ? "-fx-text-fill: #13C9F0;" : "-fx-text-fill: #FFB74D;");
        }
    }

    private void updatePeerLists() {
        if (queueManager == null || peerListContainer == null) return;
        peerListContainer.getChildren().clear();
        peerListContainer.getChildren().add(new Label("Connected peers: " + queueManager.getPeerCount()));
    }

    private void log(String msg) {
        String logLine = "[" + TIME_FMT.format(Instant.now()) + "] " + msg;
        if (syncLog != null) syncLog.setText(logLine);
        if (syncLogBar != null) syncLogBar.setText(msg);
        if (popupSyncLog != null) popupSyncLog.setText(logLine);
    }

    @FXML private void onCallNext() {}
    @FXML private void onFinanceClear() {}
}
>>>>>>> 30d08d7 (update: updated the UI and a few other functionalities)

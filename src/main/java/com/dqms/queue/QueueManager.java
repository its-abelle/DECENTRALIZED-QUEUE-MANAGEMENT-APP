package com.dqms.queue;

import com.dqms.db.DatabaseManager;
import com.dqms.model.Message;
import com.dqms.model.NodeInfo;
import com.dqms.model.Ticket;
import com.dqms.network.TCPClient;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Logger;

public class QueueManager {

    private static final Logger LOG = Logger.getLogger(QueueManager.class.getName());

    private final String nodeId;
    private final int tcpPort;
    private final DatabaseManager db;
    private final TCPClient client;
    private final Map<String, NodeInfo> peers;
    private final boolean isAdmin;

    private final PriorityBlockingQueue<Ticket> queue = new PriorityBlockingQueue<>();
    private Runnable onQueueChanged;

    public QueueManager(String nodeId, int tcpPort, DatabaseManager db, TCPClient client,
                        Map<String, NodeInfo> peers, boolean isAdmin) {
        this.nodeId  = nodeId;
        this.tcpPort = tcpPort;
        this.db      = db;
        this.client  = client;
        this.peers   = peers;
        this.isAdmin = isAdmin;
    }

    public synchronized void setOnQueueChanged(Runnable callback) {
        this.onQueueChanged = callback;
    }

    public boolean isAdmin() { return isAdmin; }

    public synchronized void registerPeer(String peerId, String ip, int port, boolean peerIsAdmin) {
        if (peerId.equals(nodeId)) return;
        if (!peers.containsKey(peerId)) {
            peers.put(peerId, new NodeInfo(peerId, ip, port, peerIsAdmin));
            LOG.info("New peer connected: " + peerId);
        }
    }

    public boolean hasActiveTicket(String regNumber) {
        synchronized (queue) {
            return queue.stream()
                    .anyMatch(t -> t.getRegistrationNumber().equalsIgnoreCase(regNumber)
                            && "WAITING".equals(t.getStatus()));
        }
    }

    public synchronized Ticket createTicket(String registrationNumber, String studentName) {
        if (hasActiveTicket(registrationNumber)) {
            return null;
        }

        Ticket ticket = new Ticket(registrationNumber, studentName, System.currentTimeMillis(), nodeId);
        db.insertTicket(ticket);
        loadFromDatabase(); 
        client.broadcast(peers.values(), Message.newTicket(nodeId, isAdmin, tcpPort, ticket));
        return ticket;
    }

    public synchronized void clearStudent(String ticketId) {
        if (!isAdmin) return;
        
        db.updateStatus(ticketId, "CLEARED");
        loadFromDatabase(); 
        client.broadcast(peers.values(), Message.updateStatus(nodeId, isAdmin, tcpPort, ticketId, "CLEARED"));
    }

    public synchronized void clearQueue() {
        if (!isAdmin) return;
        db.clearAllTickets();
        loadFromDatabase();
        client.broadcast(peers.values(), Message.clearAll(nodeId, tcpPort));
    }

    public synchronized void receiveClearAll() {
        db.clearAllTickets();
        loadFromDatabase();
    }

    public synchronized void receiveTicket(Ticket ticket) {
        if (!db.ticketExists(ticket.getTicketId())) {
            db.insertTicket(ticket);
            loadFromDatabase();
        }
    }

    public synchronized void receiveStatusUpdate(String ticketId, String newStatus) {
        db.updateStatus(ticketId, newStatus);
        loadFromDatabase();
    }

    public synchronized void applySyncResponse(List<Ticket> tickets) {
        if (tickets == null) return;
        for (Ticket t : tickets) {
            db.insertTicket(t);
            if ("CLEARED".equals(t.getStatus())) {
                db.updateStatus(t.getTicketId(), "CLEARED");
            }
        }
        loadFromDatabase();
    }

    public synchronized void loadFromDatabase() {
        try {
            List<Ticket> saved = db.getAllTickets();
            synchronized (queue) {
                queue.clear();
                queue.addAll(saved);
            }
            if (onQueueChanged != null) onQueueChanged.run();
        } catch (Exception e) {
            LOG.severe("Failed to load queue from database: " + e.getMessage());
        }
    }

    public List<Ticket> getUnfilteredTickets() {
        synchronized (queue) {
            return new ArrayList<>(queue);
        }
    }

    public List<Ticket> getAllTicketsAsList() {
        List<Ticket> list;
        synchronized (queue) {
            list = new ArrayList<>(queue);
        }

        if (!isAdmin) {
            // Keep student privacy if needed, but project requirement says Admin sees nodes that joined.
            // For a student node, maybe only show their own for UI clarity.
            // Let's keep it simple: Admins see everything, students see everything too for now to "see their position"
        }

        list.sort(Comparator.comparingInt((Ticket t) -> {
            return "WAITING".equals(t.getStatus()) ? 0 : 1;
        }).thenComparing(Ticket::compareTo));
        return list;
    }

    public List<Ticket> getWaitingTickets() {
        return getAllTicketsAsList().stream()
                .filter(t -> "WAITING".equals(t.getStatus()))
                .toList();
    }

    public String getNodeId()                   { return nodeId; }
    public int getPeerCount()                   { return peers.size(); }
}

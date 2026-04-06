package com.dqms.db;

import com.dqms.model.Ticket;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class DatabaseManager {

    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());
    private Connection conn;
    private final String dbName;

    public DatabaseManager(String nodeId) {
        this.dbName = "dqms_" + nodeId + ".db";
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA busy_timeout = 5000;");
            }
            createTable();
        } catch (SQLException e) {
            LOG.severe("Database connection failed: " + e.getMessage());
        }
    }

    private void createTable() throws SQLException {
        boolean needsRecreation = false;
        try (Statement stmt = conn.createStatement()) {
            try {
                 stmt.executeQuery("SELECT ticketId, issueTime FROM tickets LIMIT 1");
            } catch (SQLException e) {
                 if (!e.getMessage().contains("no such table")) {
                     needsRecreation = true;
                 }
            }
        }

        if (needsRecreation) {
             try (Statement stmt = conn.createStatement()) {
                 stmt.execute("DROP TABLE IF EXISTS tickets");
             }
        }

        String sql = "CREATE TABLE IF NOT EXISTS tickets (" +
                     "ticketId TEXT PRIMARY KEY, " +
                     "registrationNumber TEXT, " +
                     "studentName TEXT, " +
                     "issueTime INTEGER, " +
                     "originNodeId TEXT, " +
                     "status TEXT" +
                     ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public synchronized void insertTicket(Ticket t) {
        String sql = "INSERT OR IGNORE INTO tickets (ticketId, registrationNumber, studentName, issueTime, originNodeId, status) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, t.getTicketId());
            pstmt.setString(2, t.getRegistrationNumber());
            pstmt.setString(3, t.getStudentName());
            pstmt.setLong(4, t.getIssueTime());
            pstmt.setString(5, t.getOriginNodeId());
            pstmt.setString(6, t.getStatus());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("Failed to insert ticket: " + e.getMessage());
        }
    }

    public synchronized void updateStatus(String ticketId, String newStatus) {
        String sql = "UPDATE tickets SET status = ? WHERE ticketId = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus);
            pstmt.setString(2, ticketId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("Failed to update status: " + e.getMessage());
        }
    }

    public synchronized boolean ticketExists(String ticketId) {
        String sql = "SELECT 1 FROM tickets WHERE ticketId = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized List<Ticket> getAllTickets() {
        List<Ticket> tickets = new ArrayList<>();
        String sql = "SELECT * FROM tickets ORDER BY issueTime ASC";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Ticket t = new Ticket(
                        rs.getString("registrationNumber"),
                        rs.getString("studentName"),
                        rs.getLong("issueTime"),
                        rs.getString("originNodeId")
                );
                t.setStatus(rs.getString("status"));
                tickets.add(t);
            }
        } catch (SQLException e) {
            LOG.warning("Failed to retrieve tickets: " + e.getMessage());
        }
        return tickets;
    }

    public synchronized void clearAllTickets() {
        String sql = "DELETE FROM tickets";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            LOG.warning("Failed to clear all tickets: " + e.getMessage());
        }
    }

    public void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }
}

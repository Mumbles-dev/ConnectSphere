package com.connectsphere.dao;

import com.connectsphere.model.Connection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the "connections" table lives here. This is the DAO the
 * graph-building step (ContactGraph) pulls its edges from.
 */
public class ConnectionDAO {

    /**
     * Adds a connection between two contacts, unless one already exists
     * (checked both directions, since a connection is undirected).
     * Returns true if it was added, false if it was a duplicate.
     */
    public boolean addConnection(int contactId1, int contactId2) {
        if (connectionExists(contactId1, contactId2)) {
            return false; // duplicate — do nothing
        }

        String sql = "INSERT OR IGNORE INTO connections (contact_id_1, contact_id_2) VALUES (?, ?)";
        java.sql.Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, contactId1);
            stmt.setInt(2, contactId2);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add connection between " + contactId1 + " and " + contactId2, e);
        }
    }

    /**
     * Removes the connection between two contacts, in whichever direction
     * it was stored.
     */
    public void removeConnection(int contactId1, int contactId2) {
        String sql = """
                DELETE FROM connections
                WHERE (contact_id_1 = ? AND contact_id_2 = ?)
                   OR (contact_id_1 = ? AND contact_id_2 = ?)
                """;
        java.sql.Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, contactId1);
            stmt.setInt(2, contactId2);
            stmt.setInt(3, contactId2);
            stmt.setInt(4, contactId1);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove connection between " + contactId1 + " and " + contactId2, e);
        }
    }

    /**
     * Checks whether a connection already exists between two contacts,
     * in either direction. Used to prevent duplicate connections.
     */
    public boolean connectionExists(int contactId1, int contactId2) {
        String sql = """
                SELECT 1 FROM connections
                WHERE (contact_id_1 = ? AND contact_id_2 = ?)
                   OR (contact_id_1 = ? AND contact_id_2 = ?)
                LIMIT 1
                """;
        java.sql.Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, contactId1);
            stmt.setInt(2, contactId2);
            stmt.setInt(3, contactId2);
            stmt.setInt(4, contactId1);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check connection between " + contactId1 + " and " + contactId2, e);
        }
    }

    /**
     * Returns every connection row in the database. This is what
     * ContactGraph uses to build the full adjacency list before running
     * BFS — the whole graph is loaded once, not re-queried per hop.
     */
    public List<Connection> getAllConnections() {
        String sql = "SELECT id, contact_id_1, contact_id_2 FROM connections";
        List<Connection> connections = new ArrayList<>();
        java.sql.Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                connections.add(new Connection(
                        rs.getInt("id"),
                        rs.getInt("contact_id_1"),
                        rs.getInt("contact_id_2")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load connections", e);
        }
        return connections;
    }

    /**
     * Total number of connections currently stored. Powers the Settings
     * page's "TOTAL CONNECTIONS" stat.
     */
    public int getConnectionCount() {
        String sql = "SELECT COUNT(*) FROM connections";
        java.sql.Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count connections", e);
        }
    }

    /**
     * Returns the IDs of every contact directly connected to the given
     * contact. Powers the "Connected Contacts" display in the UI.
     */
    public List<Integer> getDirectConnections(int contactId) {
        String sql = """
                SELECT contact_id_1, contact_id_2 FROM connections
                WHERE contact_id_1 = ? OR contact_id_2 = ?
                """;
        List<Integer> neighborIds = new ArrayList<>();
        java.sql.Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, contactId);
            stmt.setInt(2, contactId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id1 = rs.getInt("contact_id_1");
                    int id2 = rs.getInt("contact_id_2");
                    // whichever side isn't the contact we asked about
                    // is the neighbor
                    neighborIds.add(id1 == contactId ? id2 : id1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load direct connections for contact " + contactId, e);
        }
        return neighborIds;
    }
}
package com.connectsphere.dao;

import com.connectsphere.util.PhoneNormalizer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the single SQLite connection used by the whole application and
 * creates / migrates the schema. Every DAO asks this class for the shared
 * Connection object — no other class opens its own connection.
 */
public class DatabaseManager {

    private static final String DB_FOLDER = "data";
    private static final String DB_FILE = DB_FOLDER + "/contacts.db";
    private static final String URL = "jdbc:sqlite:" + DB_FILE;

    private static Connection connection;

    /** Problems found during startup migration that the UI should tell the user about. */
    private static final List<String> startupWarnings = new ArrayList<>();

    private DatabaseManager() {
        // utility class — no instances
    }

    public static Connection getConnection() {
        if (connection == null) {
            try {
                File folder = new File(DB_FOLDER);
                if (!folder.exists()) {
                    folder.mkdirs();
                }

                connection = DriverManager.getConnection(URL);

                try (Statement pragma = connection.createStatement()) {
                    pragma.execute("PRAGMA foreign_keys = ON;");
                }

            } catch (SQLException e) {
                throw new RuntimeException("Failed to connect to SQLite database at " + DB_FILE, e);
            }
        }
        return connection;
    }

    /** Warnings produced by the last initializeDatabase() call (empty when all is well). */
    public static List<String> getStartupWarnings() {
        return Collections.unmodifiableList(startupWarnings);
    }

    public static void initializeDatabase() {
        startupWarnings.clear();

        String createContactsTable = """
                CREATE TABLE IF NOT EXISTS contacts (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    name             TEXT NOT NULL,
                    phone            TEXT,
                    phone_normalized TEXT,
                    email            TEXT,
                    date_added       TEXT,
                    is_favorite      INTEGER NOT NULL DEFAULT 0,
                    photo_path       TEXT,
                    is_archived      INTEGER NOT NULL DEFAULT 0
                );
                """;

        String createConnectionsTable = """
                CREATE TABLE IF NOT EXISTS connections (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    contact_id_1 INTEGER NOT NULL,
                    contact_id_2 INTEGER NOT NULL,
                    FOREIGN KEY (contact_id_1) REFERENCES contacts(id) ON DELETE CASCADE,
                    FOREIGN KEY (contact_id_2) REFERENCES contacts(id) ON DELETE CASCADE
                );
                """;

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(createContactsTable);
            stmt.execute(createConnectionsTable);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }

        migrateContactsTable();
        migrateConnectionsTable();
        migratePhoneUniqueness();
    }

    /** Adds any column an older contacts.db is missing. */
    private static void migrateContactsTable() {
        Set<String> existingColumns = new HashSet<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(contacts);")) {
            while (rs.next()) {
                existingColumns.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to inspect contacts table schema", e);
        }

        try (Statement stmt = getConnection().createStatement()) {
            if (!existingColumns.contains("date_added")) {
                stmt.execute("ALTER TABLE contacts ADD COLUMN date_added TEXT;");
            }
            if (!existingColumns.contains("is_favorite")) {
                stmt.execute("ALTER TABLE contacts ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0;");
            }
            if (!existingColumns.contains("photo_path")) {
                stmt.execute("ALTER TABLE contacts ADD COLUMN photo_path TEXT;");
            }
            if (!existingColumns.contains("is_archived")) {
                stmt.execute("ALTER TABLE contacts ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0;");
            }
            if (!existingColumns.contains("phone_normalized")) {
                stmt.execute("ALTER TABLE contacts ADD COLUMN phone_normalized TEXT;");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to migrate contacts table schema", e);
        }
    }

    /**
     * Phone uniqueness among ACTIVE contacts.
     *   1. Fill phone_normalized for rows that don't have it yet.
     *   2. Look for active contacts that already share a number. If any exist the
     *      unique index cannot be built, so the duplicates are reported through
     *      getStartupWarnings() and the index is retried on the next launch.
     *   3. Otherwise create the partial unique index.
     * Archived contacts are excluded from the index, so they never block a number.
     */
    private static void migratePhoneUniqueness() {
        Connection conn = getConnection();

        // 1) backfill (collect first, then write, so no ResultSet stays open during updates)
        Map<Integer, String> toFill = new LinkedHashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, phone FROM contacts WHERE phone_normalized IS NULL")) {
            while (rs.next()) {
                toFill.put(rs.getInt("id"), PhoneNormalizer.normalize(rs.getString("phone")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read phone numbers for migration", e);
        }

        if (!toFill.isEmpty()) {
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE contacts SET phone_normalized = ? WHERE id = ?")) {
                    for (Map.Entry<Integer, String> e : toFill.entrySet()) {
                        ps.setString(1, e.getValue());
                        ps.setInt(2, e.getKey());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) { }
                throw new RuntimeException("Failed to fill phone_normalized", e);
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) { }
            }
        }

        // 2) duplicates among active contacts
        String findDuplicates = """
                SELECT phone, GROUP_CONCAT(name, ', ') AS names
                FROM contacts
                WHERE is_archived = 0 AND phone_normalized <> ''
                GROUP BY phone_normalized
                HAVING COUNT(*) > 1
                """;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(findDuplicates)) {
            while (rs.next()) {
                startupWarnings.add(rs.getString("phone") + "  →  " + rs.getString("names"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check for duplicate phone numbers", e);
        }
        if (!startupWarnings.isEmpty()) return; // can't build the index yet

        // 3) the unique index
        String createIndex = """
                CREATE UNIQUE INDEX IF NOT EXISTS uq_contact_phone_active
                ON contacts (phone_normalized)
                WHERE is_archived = 0 AND phone_normalized <> ''
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createIndex);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create the unique phone index", e);
        }
    }

    /** Makes the connections table duplicate-proof (unchanged from before). */
    private static void migrateConnectionsTable() {
        String deleteSelfLoops = """
                DELETE FROM connections WHERE contact_id_1 = contact_id_2;
                """;

        String deleteDuplicates = """
                DELETE FROM connections
                WHERE id NOT IN (
                    SELECT MIN(id) FROM connections
                    GROUP BY
                        CASE WHEN contact_id_1 < contact_id_2 THEN contact_id_1 ELSE contact_id_2 END,
                        CASE WHEN contact_id_1 < contact_id_2 THEN contact_id_2 ELSE contact_id_1 END
                );
                """;

        String createIndex = """
                CREATE UNIQUE INDEX IF NOT EXISTS uq_connection_pair
                ON connections (
                    CASE WHEN contact_id_1 < contact_id_2 THEN contact_id_1 ELSE contact_id_2 END,
                    CASE WHEN contact_id_1 < contact_id_2 THEN contact_id_2 ELSE contact_id_1 END
                );
                """;

        Connection conn = getConnection();
        try {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(deleteSelfLoops);
                stmt.execute(deleteDuplicates);
                stmt.execute(createIndex);
            }
            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // the original error is more useful
            }
            throw new RuntimeException("Failed to clean up / index the connections table", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // best effort
            }
        }
    }

    /** Call this when the application closes (e.g. in MainApp.stop()). */
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }
}
package com.connectsphere.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Owns the single SQLite connection used by the whole application and
 * creates the schema (contacts + connections tables) the first time the
 * app runs. Every DAO asks this class for the shared Connection object —
 * no other class opens its own connection.
 */
public class DatabaseManager {

    // Stored in a "data" folder at the project root, next to /src.
    // Using a relative path means the .db file is created wherever the
    // app is launched from (IntelliJ's working directory = project root
    // by default), and it persists between runs since it's a real file
    // on disk, not something wiped on exit.
    private static final String DB_FOLDER = "data";
    private static final String DB_FILE = DB_FOLDER + "/contacts.db";
    private static final String URL = "jdbc:sqlite:" + DB_FILE;

    private static Connection connection;

    private DatabaseManager() {
        // utility class — no instances
    }

    /**
     * Opens (or reuses) the single shared connection to contacts.db.
     * Call this once at application startup, and every DAO calls it again
     * afterward to reuse the same connection.
     */
    public static Connection getConnection() {
        if (connection == null) {
            try {
                // Make sure the "data" folder exists before SQLite tries
                // to create the file inside it.
                File folder = new File(DB_FOLDER);
                if (!folder.exists()) {
                    folder.mkdirs();
                }

                connection = DriverManager.getConnection(URL);

                // SQLite does not enforce foreign keys unless you turn
                // this on explicitly, per connection.
                try (Statement pragma = connection.createStatement()) {
                    pragma.execute("PRAGMA foreign_keys = ON;");
                }

            } catch (SQLException e) {
                throw new RuntimeException("Failed to connect to SQLite database at " + DB_FILE, e);
            }
        }
        return connection;
    }

    /**
     * Creates the contacts and connections tables if they don't already
     * exist, then migrates any older contacts.db file that predates the
     * date_added / is_favorite columns. Safe to call every time the app
     * starts.
     */
    public static void initializeDatabase() {
        String createContactsTable = """
                CREATE TABLE IF NOT EXISTS contacts (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    name         TEXT NOT NULL,
                    phone        TEXT,
                    email        TEXT,
                    date_added   TEXT,
                    is_favorite  INTEGER NOT NULL DEFAULT 0,
                    photo_path   TEXT
                );
                """;

        // contact_id_1 / contact_id_2 both reference contacts.id.
        // ON DELETE CASCADE means deleting a contact automatically removes
        // any connections that mention it, so you never end up with a
        // "dangling" connection pointing at a contact that no longer exists.
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
    }

    /**
     * CREATE TABLE IF NOT EXISTS only helps for brand-new databases — an
     * existing contacts.db from before this feature was added won't have
     * date_added / is_favorite. This adds them in place if missing, so
     * older databases keep working without the user losing any data.
     */
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
        } catch (SQLException e) {
            throw new RuntimeException("Failed to migrate contacts table schema", e);
        }
    }

    /**
     * Makes the connections table duplicate-proof.
     *
     * Order matters:
     *   1. Delete self-connections (A-A), which are meaningless.
     *   2. Delete duplicate pairs (A-B and B-A, or repeated rows), keeping
     *      the lowest id per pair. This MUST run before step 3, otherwise
     *      CREATE UNIQUE INDEX fails on existing duplicate data and the
     *      app cannot start.
     *   3. Create the unique index on the normalised (min, max) id pair.
     *
     * Steps 1 and 2 are no-ops on a clean database, so running them on
     * every startup is cheap and safe. All three run in one transaction,
     * so a failure leaves the data untouched.
     *
     * A unique index is used instead of a table constraint because SQLite
     * cannot add constraints to an existing table via ALTER TABLE, and
     * rebuilding the table is risky for live data. CASE WHEN is used
     * instead of MIN()/MAX() to keep the expression index portable across
     * SQLite versions.
     *
     * Effect: inserting (A, B) when (B, A) exists raises a constraint
     * error. Use "INSERT OR IGNORE" in ConnectionDAO.addConnection() so a
     * duplicate is skipped instead of throwing.
     */
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
                // nothing more we can do; the original error is more useful
            }
            throw new RuntimeException("Failed to clean up / index the connections table", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // connection is shared; best effort to restore the default
            }
        }
    }

    /**
     * Call this when the application closes (e.g. in MainApp.stop()).
     */
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
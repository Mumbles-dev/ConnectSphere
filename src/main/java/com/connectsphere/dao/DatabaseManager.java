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
     * Adds a UNIQUE INDEX on the normalised (min, max) id pair to the
     * connections table if it doesn't already exist.
     *
     * Why an index rather than a UNIQUE constraint on the table?
     * SQLite cannot add constraints to existing tables via ALTER TABLE, so
     * a table rebuild would be needed — risky and destructive for live data.
     * A unique index achieves exactly the same enforcement without touching
     * any rows, and IF NOT EXISTS makes it idempotent on every startup.
     *
     * Why CASE WHEN instead of MIN()/MAX()?
     * MIN/MAX are aggregate functions; CASE WHEN is a scalar expression
     * that SQLite's expression-index feature handles correctly across all
     * SQLite versions supported by the JDBC driver.
     *
     * Effect: inserting (A, B) when (B, A) already exists raises a
     * SQLITE_CONSTRAINT_UNIQUE error. ConnectionDAO.addConnection() always
     * calls connectionExists() first, so this index is a silent safety
     * net — not a change in normal behaviour.
     */
    private static void migrateConnectionsTable() {
        String createIndex = """
                CREATE UNIQUE INDEX IF NOT EXISTS uq_connection_pair
                ON connections (
                    CASE WHEN contact_id_1 < contact_id_2 THEN contact_id_1 ELSE contact_id_2 END,
                    CASE WHEN contact_id_1 < contact_id_2 THEN contact_id_2 ELSE contact_id_1 END
                );
                """;
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(createIndex);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create unique index on connections table", e);
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
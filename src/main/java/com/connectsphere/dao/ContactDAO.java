package com.connectsphere.dao;

import com.connectsphere.model.Contact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the "contacts" table lives here. Controllers call these
 * methods and never write SQL themselves.
 */
public class ContactDAO {

    /**
     * Inserts a new contact and returns it with its generated ID and
     * date-added timestamp filled in. If the Contact passed in already
     * has a dateAdded (e.g. re-saving something), that value is kept;
     * otherwise it's stamped with "now".
     */
    public Contact addContact(Contact contact) {
        String dateAdded = contact.getDateAdded() != null ? contact.getDateAdded() : LocalDateTime.now().toString();

        String sql = "INSERT INTO contacts (name, phone, email, date_added, is_favorite) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, contact.getName());
            stmt.setString(2, contact.getPhone());
            stmt.setString(3, contact.getEmail());
            stmt.setString(4, dateAdded);
            stmt.setInt(5, contact.isFavorite() ? 1 : 0);
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    contact.setId(keys.getInt(1));
                }
            }
            contact.setDateAdded(dateAdded);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add contact: " + contact.getName(), e);
        }
        return contact;
    }

    /**
     * Updates an existing contact's editable details (name/phone/email),
     * matched by id. date_added and is_favorite are intentionally not
     * touched here — favorite status goes through setFavorite() instead.
     */
    public void updateContact(Contact contact) {
        String sql = "UPDATE contacts SET name = ?, phone = ?, email = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, contact.getName());
            stmt.setString(2, contact.getPhone());
            stmt.setString(3, contact.getEmail());
            stmt.setInt(4, contact.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update contact id " + contact.getId(), e);
        }
    }

    /**
     * Marks a contact as favorited or not. Powers the star toggle on
     * contact rows and the Favorite Contacts card section.
     */
    public void setFavorite(int contactId, boolean favorite) {
        String sql = "UPDATE contacts SET is_favorite = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, favorite ? 1 : 0);
            stmt.setInt(2, contactId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update favorite status for contact id " + contactId, e);
        }
    }

    /**
     * Deletes a contact by id. Thanks to ON DELETE CASCADE in the schema,
     * any connections involving this contact are removed automatically.
     */
    public void deleteContact(int contactId) {
        String sql = "DELETE FROM contacts WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, contactId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete contact id " + contactId, e);
        }
    }

    /**
     * Returns every contact in the database, ordered by name.
     * Used to populate the TableView and the Start/Destination selectors.
     */
    public List<Contact> getAllContacts() {
        String sql = "SELECT id, name, phone, email, date_added, is_favorite FROM contacts ORDER BY name COLLATE NOCASE";
        List<Contact> contacts = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                contacts.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load contacts", e);
        }
        return contacts;
    }

    /**
     * Returns every favorited contact, ordered by name. Powers the
     * Favorite Contacts card section on the Contacts page.
     */
    public List<Contact> getFavoriteContacts() {
        String sql = "SELECT id, name, phone, email, date_added, is_favorite FROM contacts WHERE is_favorite = 1 ORDER BY name COLLATE NOCASE";
        List<Contact> contacts = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                contacts.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load favorite contacts", e);
        }
        return contacts;
    }

    /**
     * Looks up a single contact by id. Returns null if not found —
     * callers (like BFSPathFinder's caller) should check for that.
     */
    public Contact getContactById(int id) {
        String sql = "SELECT id, name, phone, email, date_added, is_favorite FROM contacts WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load contact id " + id, e);
        }
        return null;
    }

    /**
     * Searches contacts whose name, phone, or email contains the given
     * text (case-insensitive). Powers the Search field in the UI.
     * Passing an empty string returns every contact.
     */
    public List<Contact> searchContacts(String keyword) {
        String sql = """
                SELECT id, name, phone, email, date_added, is_favorite FROM contacts
                WHERE name  LIKE ? COLLATE NOCASE
                   OR phone LIKE ? COLLATE NOCASE
                   OR email LIKE ? COLLATE NOCASE
                ORDER BY name COLLATE NOCASE
                """;
        List<Contact> results = new ArrayList<>();
        String pattern = "%" + keyword + "%";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search contacts for '" + keyword + "'", e);
        }
        return results;
    }

    /**
     * Returns the most recently added contacts (highest id first), capped
     * at `limit` rows. Powers the "Recently Added" list on the Contacts
     * page. Relies on AUTOINCREMENT ids being assigned in insertion order.
     */
    public List<Contact> getRecentContacts(int limit) {
        String sql = "SELECT id, name, phone, email, date_added, is_favorite FROM contacts ORDER BY id DESC LIMIT ?";
        List<Contact> contacts = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    contacts.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load recent contacts", e);
        }
        return contacts;
    }

    /**
     * Total number of contacts currently stored. Powers the Settings
     * page's "TOTAL CONTACTS" stat.
     */
    public int getContactCount() {
        String sql = "SELECT COUNT(*) FROM contacts";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count contacts", e);
        }
    }

    /**
     * Deletes every contact (and, via ON DELETE CASCADE, every connection
     * that referenced one). Backs the Settings page's "Clear All Data".
     */
    public void deleteAllContacts() {
        String sql = "DELETE FROM contacts";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear contacts", e);
        }
    }

    /**
     * Small helper to turn one ResultSet row into a Contact object,
     * so the mapping logic isn't duplicated across every query method.
     */
    private Contact mapRow(ResultSet rs) throws SQLException {
        return new Contact(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("date_added"),
                rs.getInt("is_favorite") == 1
        );
    }
}
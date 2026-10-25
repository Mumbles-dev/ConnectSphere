package com.connectsphere.dao;

import com.connectsphere.model.Contact;
import com.connectsphere.util.PhoneNormalizer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the "contacts" table lives here.
 *
 * Archived contacts (is_archived = 1) are invisible to every query below
 * except getArchivedContacts() and getContactById(). Phone numbers are unique
 * among ACTIVE contacts only, compared by phone_normalized (digits and '+').
 */
public class ContactDAO {

    private static final String COLS =
            "id, name, phone, email, date_added, is_favorite, photo_path, is_archived";

    public Contact addContact(Contact contact) {
        if (!contact.isArchived()) {
            Contact clash = findActiveByPhone(contact.getPhone(), -1);
            if (clash != null) throw new DuplicatePhoneException(clash);
        }

        String dateAdded = contact.getDateAdded() != null ? contact.getDateAdded() : LocalDateTime.now().toString();

        String sql = "INSERT INTO contacts (name, phone, phone_normalized, email, date_added, is_favorite, photo_path, is_archived) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, contact.getName());
            stmt.setString(2, contact.getPhone());
            stmt.setString(3, PhoneNormalizer.normalize(contact.getPhone()));
            stmt.setString(4, contact.getEmail());
            stmt.setString(5, dateAdded);
            stmt.setInt(6, contact.isFavorite() ? 1 : 0);
            stmt.setString(7, contact.getPhotoPath());
            stmt.setInt(8, contact.isArchived() ? 1 : 0);
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

    /** Updates name/phone/email/photo. Favorite and archive state have their own methods. */
    public void updateContact(Contact contact) {
        if (!contact.isArchived()) {
            Contact clash = findActiveByPhone(contact.getPhone(), contact.getId());
            if (clash != null) throw new DuplicatePhoneException(clash);
        }

        String sql = "UPDATE contacts SET name = ?, phone = ?, phone_normalized = ?, email = ?, photo_path = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, contact.getName());
            stmt.setString(2, contact.getPhone());
            stmt.setString(3, PhoneNormalizer.normalize(contact.getPhone()));
            stmt.setString(4, contact.getEmail());
            stmt.setString(5, contact.getPhotoPath());
            stmt.setInt(6, contact.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update contact id " + contact.getId(), e);
        }
    }

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

    // ───────────────────────────── ARCHIVE ─────────────────────────────

    /** Hides the contact everywhere except the Archived page. Connections and favorite flag are kept. */
    public void archiveContact(int contactId) {
        setArchivedFlag(contactId, true);
    }

    /**
     * Brings an archived contact back. Throws DuplicatePhoneException if an
     * active contact has taken the number in the meantime.
     */
    public void restoreContact(int contactId) {
        Contact c = getContactById(contactId);
        if (c == null) return;
        Contact clash = findActiveByPhone(c.getPhone(), contactId);
        if (clash != null) throw new DuplicatePhoneException(clash);
        setArchivedFlag(contactId, false);
    }

    private void setArchivedFlag(int contactId, boolean archived) {
        String sql = "UPDATE contacts SET is_archived = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, archived ? 1 : 0);
            stmt.setInt(2, contactId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to change archive state for contact id " + contactId, e);
        }
    }

    public List<Contact> getArchivedContacts() {
        return query("SELECT " + COLS + " FROM contacts WHERE is_archived = 1 ORDER BY name COLLATE NOCASE",
                "Failed to load archived contacts");
    }

    // ───────────────────────────── DUPLICATES ─────────────────────────────

    /**
     * Returns the ACTIVE contact that already uses this number (ignoring
     * spaces, dashes, brackets), or null. Pass the contact's own id as
     * excludeId when editing, or -1 when adding.
     */
    public Contact findActiveByPhone(String phone, int excludeId) {
        String normalized = PhoneNormalizer.normalize(phone);
        if (normalized.isEmpty()) return null;

        String sql = "SELECT " + COLS + " FROM contacts "
                + "WHERE is_archived = 0 AND phone_normalized = ? AND id <> ? LIMIT 1";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalized);
            stmt.setInt(2, excludeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check phone number", e);
        }
        return null;
    }

    // ───────────────────────────── DELETE ─────────────────────────────

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

    /** Deletes every contact, archived ones included (backs "Clear All Data"). */
    public void deleteAllContacts() {
        String sql = "DELETE FROM contacts";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear contacts", e);
        }
    }

    // ───────────────────────────── QUERIES (active only) ─────────────────────────────

    public List<Contact> getAllContacts() {
        return query("SELECT " + COLS + " FROM contacts WHERE is_archived = 0 ORDER BY name COLLATE NOCASE",
                "Failed to load contacts");
    }

    public List<Contact> getFavoriteContacts() {
        return query("SELECT " + COLS + " FROM contacts WHERE is_favorite = 1 AND is_archived = 0 ORDER BY name COLLATE NOCASE",
                "Failed to load favorite contacts");
    }

    /** Looks up one contact by id, archived or not. Returns null if not found. */
    public Contact getContactById(int id) {
        String sql = "SELECT " + COLS + " FROM contacts WHERE id = ?";
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

    public List<Contact> searchContacts(String keyword) {
        String sql = "SELECT " + COLS + " FROM contacts WHERE is_archived = 0 AND ("
                + "name LIKE ? COLLATE NOCASE OR phone LIKE ? COLLATE NOCASE OR email LIKE ? COLLATE NOCASE) "
                + "ORDER BY name COLLATE NOCASE";
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

    public List<Contact> getRecentContacts(int limit) {
        String sql = "SELECT " + COLS + " FROM contacts WHERE is_archived = 0 ORDER BY id DESC LIMIT ?";
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

    public int getContactCount() {
        String sql = "SELECT COUNT(*) FROM contacts WHERE is_archived = 0";
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count contacts", e);
        }
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private List<Contact> query(String sql, String errorMessage) {
        List<Contact> contacts = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                contacts.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(errorMessage, e);
        }
        return contacts;
    }

    private Contact mapRow(ResultSet rs) throws SQLException {
        return new Contact(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("date_added"),
                rs.getInt("is_favorite") == 1,
                rs.getString("photo_path"),
                rs.getInt("is_archived") == 1
        );
    }
}
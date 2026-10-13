package com.connectsphere.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Plain data model representing a single contact.
 * No SQL and no JavaFX code belongs here — this class is intentionally "dumb".
 */
public class Contact {

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy");

    private int id;            // primary key, -1 means "not yet saved"
    private String name;
    private String phone;
    private String email;
    private String dateAdded;  // raw LocalDateTime.toString() value from ContactDAO, e.g. "2026-09-27T14:03:10.123"; null for legacy rows added before this column existed
    private boolean favorite;
    private String photoPath;  // relative path under data/photos/, e.g. "data/photos/<uuid>.jpg"; null means no photo

    public Contact(int id, String name, String phone, String email, String dateAdded, boolean favorite, String photoPath) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.dateAdded = dateAdded;
        this.favorite = favorite;
        this.photoPath = photoPath;
    }

    // Convenience constructor for a brand-new contact not yet in the database.
    // dateAdded is left null and favorite defaults to false — ContactDAO.addContact
    // fills in dateAdded (stamped with "now") the moment the row is actually inserted.
    public Contact(String name, String phone, String email) {
        this(-1, name, phone, email, null, false, null);
    }

    // Convenience constructor for a new contact that already has a photo path chosen
    // by the upload dialog (photo was copied to data/photos/ before this call).
    public Contact(String name, String phone, String email, String photoPath) {
        this(-1, name, phone, email, null, false, photoPath);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(String dateAdded) {
        this.dateAdded = dateAdded;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    /**
     * The date this contact was added, formatted for display (e.g. "Sep 27, 2026").
     * dateAdded is stored as LocalDateTime.toString() output (ISO-8601, e.g.
     * "2026-09-27T14:03:10.123"), so it round-trips through LocalDateTime.parse()
     * with no custom formatter. Falls back gracefully for legacy contacts
     * added before this column existed (dateAdded == null) or any unexpected shape.
     */
    public String getFormattedDateAdded() {
        if (dateAdded == null || dateAdded.isBlank()) {
            return "Unknown date";
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(dateAdded);
            return parsed.format(DISPLAY_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return dateAdded; // show the raw value rather than nothing
        }
    }

    /**
     * TableView / ComboBox / debugging display.
     * Selectors in the Connection Finder section will show this text.
     */
    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contact)) return false;
        Contact other = (Contact) o;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
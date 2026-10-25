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
    private String dateAdded;  // raw LocalDateTime.toString() value; null for legacy rows
    private boolean favorite;
    private String photoPath;  // e.g. "data/photos/<uuid>.jpg"; null means no photo
    private boolean archived;  // archived contacts only show on the Archived page

    public Contact(int id, String name, String phone, String email, String dateAdded,
                   boolean favorite, String photoPath, boolean archived) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.dateAdded = dateAdded;
        this.favorite = favorite;
        this.photoPath = photoPath;
        this.archived = archived;
    }

    // Kept so existing callers keep compiling: archived defaults to false.
    public Contact(int id, String name, String phone, String email, String dateAdded,
                   boolean favorite, String photoPath) {
        this(id, name, phone, email, dateAdded, favorite, photoPath, false);
    }

    public Contact(String name, String phone, String email) {
        this(-1, name, phone, email, null, false, null, false);
    }

    public Contact(String name, String phone, String email, String photoPath) {
        this(-1, name, phone, email, null, false, photoPath, false);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDateAdded() { return dateAdded; }
    public void setDateAdded(String dateAdded) { this.dateAdded = dateAdded; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

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
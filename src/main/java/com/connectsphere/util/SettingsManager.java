package com.connectsphere.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Persists user-configurable settings to {@code data/settings.properties}.
 *
 * <p>The file lives in the same {@code data/} folder as the SQLite database,
 * so the working-directory assumption is identical. If the file does not
 * exist (first launch, or the user deleted it) every {@link #load()} call
 * silently returns defaults — the app behaves exactly as it did before this
 * feature was added.
 *
 * <p>All public methods are static; this class is never instantiated.
 */
public final class SettingsManager {

    // ── File location ──────────────────────────────────────────────────────
    private static final String SETTINGS_FOLDER = "data";
    private static final String SETTINGS_FILE   = SETTINGS_FOLDER + "/settings.properties";

    // ── Property keys ──────────────────────────────────────────────────────
    /** One of {@code PHONE}, {@code EMAIL}, or {@code BOTH}. */
    public static final String KEY_FORMAT         = "display.format";

    /** {@code true} / {@code false} — whether the Favorites section is shown. */
    public static final String KEY_SHOW_FAVORITES = "show.favorites";

    /** {@code true} / {@code false} — whether the Recently Added section is shown. */
    public static final String KEY_SHOW_RECENT    = "show.recent";

    /** {@code true} / {@code false} — whether dark mode is enabled. */
    public static final String KEY_DARK_MODE      = "ui.dark.mode";

    // ── Default values ────────────────────────────────────────────────────
    public static final String DEFAULT_FORMAT         = "PHONE";
    public static final String DEFAULT_SHOW_FAVORITES = "true";
    public static final String DEFAULT_SHOW_RECENT    = "true";
    public static final String DEFAULT_DARK_MODE      = "false";

    private SettingsManager() { /* utility class — no instances */ }

    // ──────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Loads the settings file and returns a {@link Properties} object.
     * All three known keys are guaranteed to be present in the returned
     * object — missing or unreadable files fall back silently to defaults.
     *
     * @return a {@code Properties} instance with at least the three known keys set
     */
    public static Properties load() {
        Properties props = defaults();

        File file = new File(SETTINGS_FILE);
        if (!file.exists()) {
            return props;          // first launch — just use defaults
        }

        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            // Corrupted or unreadable — log and fall back to defaults silently.
            System.err.println("[SettingsManager] Could not read " + SETTINGS_FILE
                    + ": " + e.getMessage() + " — using defaults.");
        }
        return props;
    }

    /**
     * Persists the four mutable settings to {@code data/settings.properties}.
     * Creates the {@code data/} directory if it does not exist.
     * Failures are logged to stderr but never thrown — a settings-write
     * failure must never crash the application.
     *
     * @param displayFormat  one of {@code "PHONE"}, {@code "EMAIL"}, {@code "BOTH"}
     * @param showFavorites  whether the Favorites section is visible
     * @param showRecent     whether the Recently Added section is visible
     * @param darkMode       whether dark mode is active
     */
    public static void save(String displayFormat, boolean showFavorites, boolean showRecent, boolean darkMode) {
        Properties props = new Properties();
        props.setProperty(KEY_FORMAT,         displayFormat);
        props.setProperty(KEY_SHOW_FAVORITES, String.valueOf(showFavorites));
        props.setProperty(KEY_SHOW_RECENT,    String.valueOf(showRecent));
        props.setProperty(KEY_DARK_MODE,      String.valueOf(darkMode));

        try {
            File folder = new File(SETTINGS_FOLDER);
            if (!folder.exists()) {
                folder.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(SETTINGS_FILE)) {
                props.store(out, "ConnectSphere Settings — do not edit manually");
            }
        } catch (IOException e) {
            System.err.println("[SettingsManager] Could not write " + SETTINGS_FILE
                    + ": " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    /** Returns a fresh {@code Properties} pre-loaded with the four default values. */
    private static Properties defaults() {
        Properties p = new Properties();
        p.setProperty(KEY_FORMAT,         DEFAULT_FORMAT);
        p.setProperty(KEY_SHOW_FAVORITES, DEFAULT_SHOW_FAVORITES);
        p.setProperty(KEY_SHOW_RECENT,    DEFAULT_SHOW_RECENT);
        p.setProperty(KEY_DARK_MODE,      DEFAULT_DARK_MODE);
        return p;
    }
}

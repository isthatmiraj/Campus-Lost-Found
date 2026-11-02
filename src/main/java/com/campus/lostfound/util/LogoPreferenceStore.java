package com.campus.lostfound.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Stores per-account presentation preferences separately from user records. */
public final class LogoPreferenceStore {
    private static final Object LOCK = new Object();
    private LogoPreferenceStore() {}

    private static Path file() throws IOException {
        Path dir = DataStore.get().getDataDirectory();
        Files.createDirectories(dir);
        return dir.resolve("logo-preferences.properties");
    }

    public static String get(String userId) {
        if (userId == null || userId.isBlank()) return "auto";
        synchronized (LOCK) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file())) {
                p.load(in);
            } catch (IOException ignored) {
                return "auto";
            }
            String value = p.getProperty(userId, "auto");
            return "white".equals(value) || "black".equals(value) ? value : "auto";
        }
    }

    public static void set(String userId, String preference) throws IOException {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("Invalid account.");
        if (!"auto".equals(preference) && !"black".equals(preference) && !"white".equals(preference))
            throw new IllegalArgumentException("Choose a valid logo.");
        synchronized (LOCK) {
            Path path = file();
            Properties p = new Properties();
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) { p.load(in); }
            }
            p.setProperty(userId, preference);
            try (OutputStream out = Files.newOutputStream(path)) {
                p.store(out, "CampusFind per-user logo preferences");
            }
        }
    }
}

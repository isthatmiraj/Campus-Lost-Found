package com.campus.lostfound.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One-time helper: uploads legacy local photos to ImageKit and replaces stored photo_name
 * values with their ImageKit URLs. Run locally with DB and ImageKit env vars configured.
 * Safe to rerun: URLs are skipped.
 */
public final class ImageKitMigrationTool {
    private ImageKitMigrationTool() {}

    public static void main(String[] args) throws Exception {
        Path uploads = DataStore.get().getDataDirectory().resolve("uploads").normalize();
        Set<String> names = new LinkedHashSet<>();
        try (Connection c = ChatDatabase.getConnection()) {
            collect(c, "campus_items", "id", names);
            collect(c, "campus_item_edit_proposals", "id", names);
        }
        int migrated = 0, skipped = 0;
        for (String name : names) {
            if (name == null || name.isBlank() || ImageKitStorage.isRemoteUrl(name)) { skipped++; continue; }
            if (!name.matches("[a-fA-F0-9-]+\\.(jpg|png)")) {
                System.out.println("Skipping unsupported photo reference: " + name);
                skipped++; continue;
            }
            Path file = uploads.resolve(name).normalize();
            if (!file.getParent().equals(uploads) || !Files.isRegularFile(file)) {
                System.out.println("Missing local photo: " + name);
                skipped++; continue;
            }
            String mime = name.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
            String url = ImageKitStorage.upload(Files.readAllBytes(file), "campusfind-" + name, mime);
            try (Connection c = ChatDatabase.getConnection()) {
                c.setAutoCommit(false);
                try {
                    update(c, "campus_items", name, url);
                    update(c, "campus_item_edit_proposals", name, url);
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            }
            System.out.println("Migrated " + name + " -> " + url);
            migrated++;
        }
        System.out.println("ImageKit migration finished. Migrated: " + migrated + "; skipped: " + skipped);
        System.out.println("Local originals were kept as a rollback copy.");
    }

    private static void collect(Connection c, String table, String idColumn, Set<String> names) throws SQLException {
        String sql = "SELECT photo_name FROM " + table + " WHERE photo_name IS NOT NULL AND photo_name <> ''";
        try (PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            while (r.next()) names.add(r.getString(1));
        }
    }

    private static void update(Connection c, String table, String oldName, String url) throws SQLException {
        String sql = "UPDATE " + table + " SET photo_name=? WHERE photo_name=?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, url);
            p.setString(2, oldName);
            p.executeUpdate();
        }
    }
}

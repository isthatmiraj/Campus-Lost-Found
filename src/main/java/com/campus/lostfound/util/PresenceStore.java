package com.campus.lostfound.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks browser sessions, not just user accounts, so a user remains online
 * while any one of their signed-in sessions is active.
 */
public final class PresenceStore {
    private PresenceStore() {}

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static void heartbeat(String userId, String sessionId) throws IOException {
        String sessionHash = hash(sessionId);
        try (Connection c = ChatDatabase.getConnection()) {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO campus_user_presence (session_key_hash, user_id, last_seen) " +
                    "VALUES (?, ?, CURRENT_TIMESTAMP) " +
                    "ON CONFLICT (session_key_hash) DO UPDATE SET user_id=EXCLUDED.user_id, last_seen=CURRENT_TIMESTAMP")) {
                p.setString(1, sessionHash);
                p.setString(2, userId);
                p.executeUpdate();
            }
            // Remove only this user's abandoned session records; no global cleanup job
            // or always-on process is needed.
            try (PreparedStatement p = c.prepareStatement(
                    "DELETE FROM campus_user_presence WHERE user_id=? AND last_seen < CURRENT_TIMESTAMP - INTERVAL '1 day'")) {
                p.setString(1, userId);
                p.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IOException("Unable to update user presence", e);
        }
    }

    public static void removeSession(String userId, String sessionId) throws IOException {
        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement(
                     "DELETE FROM campus_user_presence WHERE user_id=? AND session_key_hash=?")) {
            p.setString(1, userId);
            p.setString(2, hash(sessionId));
            p.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Unable to clear user presence", e);
        }
    }

    public static java.util.Map<String, java.util.Map<String, String>> adminStatus() throws IOException {
        java.util.Map<String, java.util.Map<String, String>> result = new java.util.LinkedHashMap<>();
        String sql = "WITH presence AS (" +
                " SELECT user_id, " +
                " MIN(started_at) FILTER (WHERE last_seen >= CURRENT_TIMESTAMP - INTERVAL '2 minutes') AS online_since, " +
                " MAX(last_seen) AS last_seen " +
                " FROM campus_user_presence GROUP BY user_id" +
                "), remembered AS (" +
                " SELECT user_id, MIN(login_started_at) AS remembered_since, MAX(expires_at) AS remember_expires_at " +
                " FROM campus_user_tokens WHERE expires_at > CURRENT_TIMESTAMP GROUP BY user_id" +
                ") " +
                "SELECT u.id, p.online_since, p.last_seen, r.remembered_since, r.remember_expires_at " +
                "FROM campus_users u LEFT JOIN presence p ON p.user_id=u.id " +
                "LEFT JOIN remembered r ON r.user_id=u.id";
        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                java.util.Map<String, String> status = new java.util.LinkedHashMap<>();
                status.put("onlineSince", timestampString(r.getTimestamp("online_since")));
                status.put("lastSeen", timestampString(r.getTimestamp("last_seen")));
                status.put("rememberedSince", timestampString(r.getTimestamp("remembered_since")));
                status.put("rememberExpiresAt", timestampString(r.getTimestamp("remember_expires_at")));
                result.put(r.getString("id"), status);
            }
        } catch (SQLException e) {
            throw new IOException("Unable to read admin user status", e);
        }
        return result;
    }

    private static String timestampString(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }

    public static Set<String> onlineUserIds() throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement(
                     "SELECT DISTINCT user_id FROM campus_user_presence " +
                     "WHERE last_seen >= CURRENT_TIMESTAMP - INTERVAL '2 minutes'")) {
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) ids.add(r.getString(1));
            }
        } catch (SQLException e) {
            throw new IOException("Unable to read user presence", e);
        }
        return ids;
    }
}

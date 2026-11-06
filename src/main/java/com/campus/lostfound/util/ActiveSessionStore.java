package com.campus.lostfound.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HexFormat;

/**
 * Maintains one active authenticated session per user.
 * Only a SHA-256 hash of the servlet session ID is stored.
 */
public final class ActiveSessionStore {

    private ActiveSessionStore() {}

    private static String hash(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** A credential login always takes ownership of the active session. */
    public static void claim(String userId, String sessionId) throws IOException {
        String key = hash(sessionId);

        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement(
                 "INSERT INTO campus_user_active_sessions (user_id, session_key_hash, last_seen) " +
                 "VALUES (?, ?, CURRENT_TIMESTAMP) " +
                 "ON CONFLICT (user_id) DO UPDATE SET " +
                 "session_key_hash = EXCLUDED.session_key_hash, " +
                 "last_seen = CURRENT_TIMESTAMP")) {
            p.setString(1, userId);
            p.setString(2, key);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Unable to register the active session", e);
        }
    }

    /**
     * Used by Remember Me. It may claim an account only when there is
     * no active record or the previous session has been inactive for 30 minutes.
     */
    public static boolean tryClaimIfInactive(String userId, String sessionId)
            throws IOException {
        String key = hash(sessionId);

        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement(
                 "INSERT INTO campus_user_active_sessions (user_id, session_key_hash, last_seen) " +
                 "VALUES (?, ?, CURRENT_TIMESTAMP) " +
                 "ON CONFLICT (user_id) DO UPDATE SET " +
                 "session_key_hash = EXCLUDED.session_key_hash, " +
                 "last_seen = CURRENT_TIMESTAMP " +
                 "WHERE campus_user_active_sessions.last_seen < " +
                 "CURRENT_TIMESTAMP - INTERVAL '30 minutes' " +
                 "RETURNING user_id")) {
            p.setString(1, userId);
            p.setString(2, key);
            try (var r = p.executeQuery()) {
                return r.next();
            }
        } catch (SQLException e) {
            throw new IOException("Unable to restore the remembered session", e);
        }
    }

    /**
     * Confirms that this request belongs to the active session and refreshes
     * its activity timestamp. Also safely adopts a pre-existing session when
     * the newly introduced table has no record for that account yet.
     */
    public static boolean isCurrentAndTouch(String userId, String sessionId)
            throws IOException {
        String key = hash(sessionId);

        try (Connection c = ChatDatabase.getConnection()) {
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO campus_user_active_sessions " +
                    "(user_id, session_key_hash, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                    "ON CONFLICT (user_id) DO NOTHING")) {
                insert.setString(1, userId);
                insert.setString(2, key);
                insert.executeUpdate();
            }

            try (PreparedStatement update = c.prepareStatement(
                    "UPDATE campus_user_active_sessions SET last_seen = CURRENT_TIMESTAMP " +
                    "WHERE user_id = ? AND session_key_hash = ? " +
                    "AND last_seen >= CURRENT_TIMESTAMP - INTERVAL '30 minutes'")) {
                update.setString(1, userId);
                update.setString(2, key);
                return update.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            throw new IOException("Unable to validate the active session", e);
        }
    }

    /** Update the registry when the servlet container rotates a session ID. */
    public static boolean replaceSessionId(String userId, String oldSessionId,
                                           String newSessionId) throws IOException {
        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement(
                 "UPDATE campus_user_active_sessions SET session_key_hash = ?, " +
                 "last_seen = CURRENT_TIMESTAMP WHERE user_id = ? AND session_key_hash = ?")) {
            p.setString(1, hash(newSessionId));
            p.setString(2, userId);
            p.setString(3, hash(oldSessionId));
            return p.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IOException("Unable to update the active session ID", e);
        }
    }

    /** Logout removes only the session that is actually being logged out. */
    public static void clear(String userId, String sessionId) throws IOException {
        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement(
                 "DELETE FROM campus_user_active_sessions " +
                 "WHERE user_id = ? AND session_key_hash = ?")) {
            p.setString(1, userId);
            p.setString(2, hash(sessionId));
            p.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Unable to clear the active session", e);
        }
    }
}

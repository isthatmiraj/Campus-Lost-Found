package com.campus.lostfound.util;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class MaintenanceStore {
    private MaintenanceStore() {}

    public static final String[][] CATEGORIES = {
        {"CHAT_MESSAGES", "Inactive conversation messages", "180"},
        {"READ_NOTIFICATIONS", "Read notifications", "90"},
        {"AUDIT_LOGS", "Audit logs", "365"},
        {"EXPIRED_TOKENS", "Expired remembered devices", "1"},
        {"STALE_SESSIONS", "Stale active sessions", "7"},
        {"STALE_PRESENCE", "Old presence records", "1"}
    };

    public static void initialize() throws SQLException {
        try (Connection c = ChatDatabase.getConnection();
             Statement s = c.createStatement()) {

            s.execute("""
                CREATE TABLE IF NOT EXISTS campus_maintenance_settings (
                    category VARCHAR(50) PRIMARY KEY,
                    retention_days INTEGER NOT NULL,
                    auto_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
            """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS campus_maintenance_config (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    auto_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    interval_hours INTEGER NOT NULL DEFAULT 24,
                    last_auto_run TIMESTAMPTZ
                )
            """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS campus_maintenance_history (
                    id BIGSERIAL PRIMARY KEY,
                    category VARCHAR(50) NOT NULL,
                    deleted_count BIGINT NOT NULL,
                    cutoff_at TIMESTAMPTZ NOT NULL,
                    run_type VARCHAR(20) NOT NULL,
                    actor_id TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
            """);

            s.execute("""
                INSERT INTO campus_maintenance_config (id, auto_enabled, interval_hours)
                VALUES (1, FALSE, 24)
                ON CONFLICT (id) DO NOTHING
            """);

            for (String[] category : CATEGORIES) {
                try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO campus_maintenance_settings
                        (category, retention_days, auto_enabled)
                    VALUES (?, ?, FALSE)
                    ON CONFLICT (category) DO NOTHING
                """)) {
                    p.setString(1, category[0]);
                    p.setInt(2, Integer.parseInt(category[2]));
                    p.executeUpdate();
                }
            }
        }
    }

    public static List<Map<String, Object>> settings() throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement("""
                SELECT category, retention_days, auto_enabled
                FROM campus_maintenance_settings
                ORDER BY category
             """);
             ResultSet r = p.executeQuery()) {

            while (r.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("category", r.getString("category"));
                row.put("days", r.getInt("retention_days"));
                row.put("enabled", r.getBoolean("auto_enabled"));
                row.put("label", label(r.getString("category")));
                result.add(row);
            }
        }
        return result;
    }

    public static Map<String, Object> config() throws SQLException {
        Map<String, Object> result = new HashMap<>();

        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement("""
                SELECT auto_enabled, interval_hours
                FROM campus_maintenance_config WHERE id = 1
             """);
             ResultSet r = p.executeQuery()) {
            if (r.next()) {
                result.put("enabled", r.getBoolean("auto_enabled"));
                result.put("hours", r.getInt("interval_hours"));
            }
        }
        return result;
    }

    public static void saveSettings(
            Map<String, Integer> days,
            Set<String> enabled,
            boolean autoEnabled,
            int intervalHours) throws SQLException {

        if (!Set.of(1, 6, 12, 24, 168).contains(intervalHours)) {
            throw new IllegalArgumentException("Invalid cleanup frequency.");
        }

        try (Connection c = ChatDatabase.getConnection()) {
            c.setAutoCommit(false);

            try {
                for (String[] category : CATEGORIES) {
                    String key = category[0];
                    int value = days.getOrDefault(key, Integer.parseInt(category[2]));

                    int minimum = key.equals("AUDIT_LOGS") ? 30 : 1;
                    if (value < minimum || value > 3650) {
                        throw new IllegalArgumentException(
                            "Retention for " + label(key) + " must be between "
                            + minimum + " and 3650 days.");
                    }

                    try (PreparedStatement p = c.prepareStatement("""
                        UPDATE campus_maintenance_settings
                        SET retention_days = ?, auto_enabled = ?, updated_at = NOW()
                        WHERE category = ?
                    """)) {
                        p.setInt(1, value);
                        p.setBoolean(2, enabled.contains(key));
                        p.setString(3, key);
                        p.executeUpdate();
                    }
                }

                try (PreparedStatement p = c.prepareStatement("""
                    UPDATE campus_maintenance_config
                    SET auto_enabled = ?, interval_hours = ?
                    WHERE id = 1
                """)) {
                    p.setBoolean(1, autoEnabled);
                    p.setInt(2, intervalHours);
                    p.executeUpdate();
                }

                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw new SQLException(e.getMessage(), e);
            }
        }
    }

    public static long preview(String category, int days) throws SQLException {
        validate(category, days);
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement(countSql(category))) {
            bind(p, category, cutoff);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getLong(1) : 0;
            }
        }
    }

    public static long cleanup(
            String category, int days, String actor, String runType)
            throws SQLException {

        validate(category, days);
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        try (Connection c = ChatDatabase.getConnection()) {
            c.setAutoCommit(false);

            try {
                if (!locked(c)) {
                    c.rollback();
                    throw new SQLException(
                        "Another maintenance operation is running. Try again shortly.");
                }

                long deleted;
                try (PreparedStatement p = c.prepareStatement(deleteSql(category))) {
                    bind(p, category, cutoff);
                    deleted = p.executeUpdate();
                }

                try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO campus_maintenance_history
                        (category, deleted_count, cutoff_at, run_type, actor_id)
                    VALUES (?, ?, ?, ?, ?)
                """)) {
                    p.setString(1, category);
                    p.setLong(2, deleted);
                    p.setTimestamp(3, Timestamp.from(cutoff));
                    p.setString(4, runType);
                    p.setString(5, actor);
                    p.executeUpdate();
                }

                c.commit();
                return deleted;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw new SQLException(e.getMessage(), e);
            }
        }
    }

    public static void automaticCleanup() throws SQLException {
        try (Connection c = ChatDatabase.getConnection()) {
            c.setAutoCommit(false);

            try {
                if (!locked(c)) {
                    c.rollback();
                    return;
                }

                boolean globalEnabled;
                int interval;

                try (PreparedStatement p = c.prepareStatement("""
                    SELECT auto_enabled, interval_hours
                    FROM campus_maintenance_config WHERE id = 1
                """);
                     ResultSet r = p.executeQuery()) {
                    if (!r.next()) {
                        c.rollback();
                        return;
                    }
                    globalEnabled = r.getBoolean(1);
                    interval = r.getInt(2);
                }

                if (!globalEnabled) {
                    c.rollback();
                    return;
                }

                try (PreparedStatement p = c.prepareStatement("""
                    SELECT last_auto_run FROM campus_maintenance_config WHERE id = 1
                """);
                     ResultSet r = p.executeQuery()) {
                    if (r.next()) {
                        Timestamp last = r.getTimestamp(1);
                        if (last != null &&
                            last.toInstant().plus(interval, ChronoUnit.HOURS)
                                .isAfter(Instant.now())) {
                            c.rollback();
                            return;
                        }
                    }
                }

                try (PreparedStatement p = c.prepareStatement("""
                    SELECT category, retention_days
                    FROM campus_maintenance_settings
                    WHERE auto_enabled = TRUE
                """);
                     ResultSet r = p.executeQuery()) {

                    while (r.next()) {
                        String category = r.getString(1);
                        int days = r.getInt(2);
                        validate(category, days);

                        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
                        long deleted;

                        try (PreparedStatement d =
                                 c.prepareStatement(deleteSql(category))) {
                            bind(d, category, cutoff);
                            deleted = d.executeUpdate();
                        }

                        try (PreparedStatement h = c.prepareStatement("""
                            INSERT INTO campus_maintenance_history
                                (category, deleted_count, cutoff_at, run_type)
                            VALUES (?, ?, ?, 'AUTOMATIC')
                        """)) {
                            h.setString(1, category);
                            h.setLong(2, deleted);
                            h.setTimestamp(3, Timestamp.from(cutoff));
                            h.executeUpdate();
                        }
                    }
                }

                try (PreparedStatement p = c.prepareStatement("""
                    UPDATE campus_maintenance_config
                    SET last_auto_run = NOW() WHERE id = 1
                """)) {
                    p.executeUpdate();
                }

                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw new SQLException(e.getMessage(), e);
            }
        }
    }

    public static List<Map<String, Object>> history() throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement("""
                SELECT category, deleted_count, cutoff_at, run_type,
                       actor_id, created_at
                FROM campus_maintenance_history
                ORDER BY created_at DESC LIMIT 50
             """);
             ResultSet r = p.executeQuery()) {

            while (r.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("category", label(r.getString("category")));
                row.put("deleted", r.getLong("deleted_count"));
                row.put("cutoff", r.getTimestamp("cutoff_at"));
                row.put("type", r.getString("run_type"));
                row.put("actor", r.getString("actor_id"));
                row.put("created", r.getTimestamp("created_at"));
                result.add(row);
            }
        }
        return result;
    }

    private static boolean locked(Connection c) throws SQLException {
        try (PreparedStatement p =
                 c.prepareStatement("SELECT pg_try_advisory_xact_lock(194827, 7721)");
             ResultSet r = p.executeQuery()) {
            return r.next() && r.getBoolean(1);
        }
    }

    private static String countSql(String category) {
        switch (category) {
            case "CHAT_MESSAGES":
                return """
                    SELECT COUNT(*) FROM chat_messages m
                    JOIN chat_conversations c ON c.id = m.conversation_id
                    WHERE m.sent_at < ? AND c.last_message_at < ?
                """;
            case "READ_NOTIFICATIONS":
                return "SELECT COUNT(*) FROM campus_notifications WHERE status = 'READ' AND created_at < ?";
            case "AUDIT_LOGS":
                return "SELECT COUNT(*) FROM campus_audit_logs WHERE created_at < ?";
            case "EXPIRED_TOKENS":
                return "SELECT COUNT(*) FROM campus_user_tokens WHERE expires_at < NOW()";
            case "STALE_SESSIONS":
                return "SELECT COUNT(*) FROM campus_user_active_sessions WHERE last_seen < ?";
            case "STALE_PRESENCE":
                return "SELECT COUNT(*) FROM campus_user_presence WHERE last_seen < ?";
            default:
                throw new IllegalArgumentException("Unknown cleanup category.");
        }
    }

    private static String deleteSql(String category) {
        switch (category) {
            case "CHAT_MESSAGES":
                return """
                    DELETE FROM chat_messages m
                    USING chat_conversations c
                    WHERE c.id = m.conversation_id
                      AND m.sent_at < ? AND c.last_message_at < ?
                """;
            case "READ_NOTIFICATIONS":
                return "DELETE FROM campus_notifications WHERE status = 'READ' AND created_at < ?";
            case "AUDIT_LOGS":
                return "DELETE FROM campus_audit_logs WHERE created_at < ?";
            case "EXPIRED_TOKENS":
                return "DELETE FROM campus_user_tokens WHERE expires_at < NOW()";
            case "STALE_SESSIONS":
                return "DELETE FROM campus_user_active_sessions WHERE last_seen < ?";
            case "STALE_PRESENCE":
                return "DELETE FROM campus_user_presence WHERE last_seen < ?";
            default:
                throw new IllegalArgumentException("Unknown cleanup category.");
        }
    }

    private static void bind(PreparedStatement p, String category, Instant cutoff)
            throws SQLException {
        if (category.equals("CHAT_MESSAGES")) {
            p.setTimestamp(1, Timestamp.from(cutoff));
            p.setTimestamp(2, Timestamp.from(cutoff));
        } else if (category.equals("READ_NOTIFICATIONS") ||
                   category.equals("AUDIT_LOGS")) {
            p.setString(1, cutoff.toString());
        } else if (!category.equals("EXPIRED_TOKENS")) {
            p.setTimestamp(1, Timestamp.from(cutoff));
        }
    }

    private static void validate(String category, int days) {
        boolean found = false;
        for (String[] item : CATEGORIES) {
            if (item[0].equals(category)) found = true;
        }
        if (!found) throw new IllegalArgumentException("Unknown cleanup category.");

        int minimum = category.equals("AUDIT_LOGS") ? 30 : 1;
        if (days < minimum || days > 3650) {
            throw new IllegalArgumentException("Invalid retention period.");
        }
    }

    public static String label(String category) {
        for (String[] item : CATEGORIES) {
            if (item[0].equals(category)) return item[1];
        }
        return category;
    }
}

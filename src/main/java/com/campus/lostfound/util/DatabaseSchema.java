package com.campus.lostfound.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Additive PostgreSQL schema for CampusFind.
 * Existing chat tables are deliberately left untouched.
 */
public final class DatabaseSchema {

    private DatabaseSchema() {}

    private static final List<String> DDL = List.of(
        """
        CREATE TABLE IF NOT EXISTS campus_users (
            id VARCHAR(64) PRIMARY KEY,
            name TEXT NOT NULL,
            email VARCHAR(320) NOT NULL,
            password_hash TEXT NOT NULL,
            role VARCHAR(32) NOT NULL,
            CONSTRAINT campus_users_email_unique UNIQUE (email)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS campus_items (
            id VARCHAR(64) PRIMARY KEY,
            reporter_id VARCHAR(64) NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            category TEXT NOT NULL,
            type VARCHAR(32) NOT NULL,
            location TEXT NOT NULL,
            item_date TEXT NOT NULL,
            status VARCHAR(64) NOT NULL,
            contact_phone TEXT NOT NULL DEFAULT '',
            photo_name TEXT NOT NULL DEFAULT '',
            approval_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED'
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS campus_claims (
            id VARCHAR(64) PRIMARY KEY,
            item_id VARCHAR(64) NOT NULL,
            claimant_id VARCHAR(64) NOT NULL,
            proof TEXT NOT NULL,
            status VARCHAR(32) NOT NULL,
            created_at TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS campus_item_edit_proposals (
            id VARCHAR(64) PRIMARY KEY,
            item_id VARCHAR(64) NOT NULL,
            reporter_id VARCHAR(64) NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            category TEXT NOT NULL,
            type VARCHAR(32) NOT NULL,
            location TEXT NOT NULL,
            item_date TEXT NOT NULL,
            contact_phone TEXT NOT NULL DEFAULT '',
            photo_name TEXT NOT NULL DEFAULT '',
            submitted_at TEXT NOT NULL,
            CONSTRAINT campus_one_pending_edit_per_item UNIQUE (item_id)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS campus_notifications (
            id VARCHAR(64) PRIMARY KEY,
            user_id VARCHAR(64) NOT NULL,
            message TEXT NOT NULL,
            target_url TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL,
            status VARCHAR(16) NOT NULL DEFAULT 'UNREAD'
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS campus_audit_logs (
            id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            created_at TEXT NOT NULL,
            actor_id VARCHAR(64) NOT NULL DEFAULT '',
            action VARCHAR(128) NOT NULL,
            detail TEXT NOT NULL DEFAULT ''
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS campus_handovers (
            item_id VARCHAR(64) PRIMARY KEY,
            owner_id VARCHAR(64) NOT NULL,
            claimant_id VARCHAR(64) NOT NULL,
            owner_confirm VARCHAR(8) NOT NULL DEFAULT 'NO',
            claimant_confirm VARCHAR(8) NOT NULL DEFAULT 'NO',
            status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS campus_saved_items (
            user_id VARCHAR(64) NOT NULL,
            item_id VARCHAR(64) NOT NULL,
            saved_at TEXT NOT NULL,
            PRIMARY KEY (user_id, item_id)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS campus_listing_flags (
            id VARCHAR(64) PRIMARY KEY,
            user_id VARCHAR(64) NOT NULL,
            item_id VARCHAR(64) NOT NULL,
            reason TEXT NOT NULL,
            status VARCHAR(32) NOT NULL DEFAULT 'OPEN'
        )
        """,
        "CREATE INDEX IF NOT EXISTS campus_items_reporter_idx ON campus_items(reporter_id)",
        "CREATE INDEX IF NOT EXISTS campus_items_status_idx ON campus_items(status)",
        "CREATE INDEX IF NOT EXISTS campus_claims_item_idx ON campus_claims(item_id)",
        "CREATE INDEX IF NOT EXISTS campus_claims_claimant_idx ON campus_claims(claimant_id)",
        "CREATE INDEX IF NOT EXISTS campus_notifications_user_idx ON campus_notifications(user_id)",
        "CREATE INDEX IF NOT EXISTS campus_audit_created_idx ON campus_audit_logs(created_at)",
        "CREATE INDEX IF NOT EXISTS campus_flags_status_idx ON campus_listing_flags(status)"
    );

    public static void initialize() throws SQLException {
        try (Connection connection = ChatDatabase.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : DDL) {
                statement.execute(sql);
            }
        }
    }
}

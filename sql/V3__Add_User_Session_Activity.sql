-- V3__Add_User_Session_Activity.sql
-- Additive migration for Neon PostgreSQL

CREATE TABLE IF NOT EXISTS campus_user_sessions (
    session_id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_campus_user_sessions_user FOREIGN KEY (user_id) REFERENCES campus_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS campus_user_sessions_user_last_seen_idx ON campus_user_sessions(user_id, last_seen);

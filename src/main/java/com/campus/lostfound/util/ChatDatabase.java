package com.campus.lostfound.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/** Shared small JDBC pool for Neon PostgreSQL. Credentials stay in environment variables. */
public final class ChatDatabase {
    private static volatile HikariDataSource dataSource;
    private ChatDatabase() {}

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("CHAT_DB_URL");
        String user = System.getenv("CHAT_DB_USER");
        String password = System.getenv("CHAT_DB_PASSWORD");
        if (url == null || url.isBlank() || user == null || user.isBlank() || password == null || password.isBlank()) {
            throw new SQLException("Chat database credentials are not configured.");
        }
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new SQLException("Invalid PostgreSQL JDBC URL.");
        }
        HikariDataSource pool = dataSource;
        if (pool == null) {
            synchronized (ChatDatabase.class) {
                pool = dataSource;
                if (pool == null) {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(url);
                    config.setUsername(user);
                    config.setPassword(password);
                    config.setPoolName("CampusFind-Neon");
                    config.setMaximumPoolSize(5);
                    config.setMinimumIdle(0);
                    config.setConnectionTimeout(10000);
                    config.setIdleTimeout(300000);
                    config.setMaxLifetime(1500000);
                    config.setInitializationFailTimeout(-1);
                    pool = new HikariDataSource(config);
                    dataSource = pool;
                }
            }
        }
        return pool.getConnection();
    }
}

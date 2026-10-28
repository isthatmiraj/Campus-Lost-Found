
package com.campus.lostfound.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ChatDatabase {

    private ChatDatabase() {}

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("CHAT_DB_URL");
        String user = System.getenv("CHAT_DB_USER");
        String password = System.getenv("CHAT_DB_PASSWORD");

        if (url == null || user == null || password == null) {
            throw new SQLException(
                "Chat database credentials are not configured."
            );
        }

        if (!url.startsWith("jdbc:postgresql://")) {
            throw new SQLException("Invalid PostgreSQL JDBC URL.");
        }

        return DriverManager.getConnection(url, user, password);
    }
}

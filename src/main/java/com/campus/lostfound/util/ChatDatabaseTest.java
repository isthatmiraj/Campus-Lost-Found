package com.campus.lostfound.util;

import java.sql.Connection;

public class ChatDatabaseTest {
    public static void main(String[] args) {
        try (Connection connection = ChatDatabase.getConnection()) {
            System.out.println("NEON CONNECTION SUCCESSFUL!");
            System.out.println("Database: " +
                connection.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            System.err.println("CONNECTION FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

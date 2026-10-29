package com.campus.lostfound.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseInspectionTest {
    public static void main(String[] args) {
        String sql = """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_type = 'BASE TABLE'
            ORDER BY table_name
            """;

        try (Connection connection = ChatDatabase.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            System.out.println("Connected to PostgreSQL.");
            System.out.println("Existing public tables:");

            while (rs.next()) {
                System.out.println(" - " + rs.getString("table_name"));
            }
        } catch (Exception e) {
            System.err.println("Database inspection failed: " + e.getMessage());
            System.exit(1);
        }
    }
}

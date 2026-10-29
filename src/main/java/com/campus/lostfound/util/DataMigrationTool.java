package com.campus.lostfound.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** One-time transactional importer. It never touches chat tables. */
public final class DataMigrationTool {
    private static final String[] TABLES = {
        "campus_users", "campus_items", "campus_claims",
        "campus_item_edit_proposals", "campus_notifications",
        "campus_audit_logs", "campus_handovers",
        "campus_saved_items", "campus_listing_flags"
    };

    private DataMigrationTool() {}

    public static void main(String[] args) {
        Path dir = Paths.get(System.getenv().getOrDefault(
            "CAMPUS_MIGRATION_DATA_DIR",
            System.getProperty("user.home") + "/.campus-lost-found/data"
        )).toAbsolutePath().normalize();

        try {
            if (!Files.isDirectory(dir))
                throw new IOException("Data directory not found: " + dir);
            System.out.println("Source: " + dir);

            try (Connection c = ChatDatabase.getConnection()) {
                c.setAutoCommit(false);
                try {
                    requireEmptyTargets(c);
                    int users = importUsers(c, dir);
                    int items = importItems(c, dir);
                    int claims = importClaims(c, dir);
                    int proposals = importProposals(c, dir);
                    int notifications = importNotifications(c, dir);
                    int audit = importAudit(c, dir);
                    int handovers = importHandovers(c, dir);
                    int saved = importSavedItems(c, dir);
                    int flags = importFlags(c, dir);
                    c.commit();

                    System.out.println("MIGRATION COMMITTED");
                    System.out.println("Users: " + users);
                    System.out.println("Items: " + items);
                    System.out.println("Claims: " + claims);
                    System.out.println("Edit proposals: " + proposals);
                    System.out.println("Notifications: " + notifications);
                    System.out.println("Audit logs: " + audit);
                    System.out.println("Handovers: " + handovers);
                    System.out.println("Saved items: " + saved);
                    System.out.println("Listing flags: " + flags);
                    System.out.println("Chat tables were not modified.");
                } catch (Exception e) {
                    try { c.rollback(); } catch (SQLException x) { e.addSuppressed(x); }
                    throw e;
                }
            }
        } catch (Exception e) {
            System.err.println("MIGRATION FAILED: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void requireEmptyTargets(Connection c) throws SQLException {
        for (String table : TABLES) {
            try (Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("SELECT COUNT(*) FROM public." + table)) {
                r.next();
                if (r.getLong(1) != 0)
                    throw new SQLException("Safety stop: " + table + " is not empty. Nothing imported.");
            }
        }
    }

    private static List<String[]> rows(Path dir, String file) throws IOException {
        Path path = dir.resolve(file);
        List<String[]> out = new ArrayList<>();
        if (Files.notExists(path)) {
            System.out.println("Skipping missing source file: " + file);
            return out;
        }
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] enc = line.split("\\|", -1);
            String[] dec = new String[enc.length];
            try {
                for (int i = 0; i < enc.length; i++)
                    dec[i] = new String(Base64.getUrlDecoder().decode(enc[i]), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid Base64 data in " + file, e);
            }
            out.add(dec);
        }
        return out;
    }

    private static void length(String file, String[] row, int... allowed) throws IOException {
        for (int n : allowed) if (row.length == n) return;
        throw new IOException("Unexpected field count in " + file + ": " + row.length);
    }

    private static int importUsers(Connection c, Path d) throws Exception {
        List<String[]> rows=rows(d,"users.txt");
        try(PreparedStatement p=c.prepareStatement("INSERT INTO campus_users(id,name,email,password_hash,role) VALUES(?,?,?,?,?)")){
            for(String[] r:rows){length("users.txt",r,5);for(int i=0;i<5;i++)p.setString(i+1,r[i]);p.addBatch();}
            p.executeBatch();
        } return rows.size();
    }

    private static int importItems(Connection c, Path d) throws Exception {
        List<String[]> rows=rows(d,"items.txt");
        String sql="INSERT INTO campus_items(id,reporter_id,title,description,category,type,location,item_date,status,contact_phone,photo_name,approval_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement p=c.prepareStatement(sql)){
            for(String[] r:rows){
                length("items.txt",r,9,10,11,12);
                for(int i=0;i<9;i++)p.setString(i+1,r[i]);
                p.setString(10,r.length>=10?r[9]:"");
                p.setString(11,r.length>=11?r[10]:"");
                String approval=r.length>=12?r[11]:"APPROVED";
                p.setString(12,approval==null||approval.isBlank()?"APPROVED":approval);
                p.addBatch();
            }p.executeBatch();
        }return rows.size();
    }

    private static int importClaims(Connection c, Path d) throws Exception {
        List<String[]> rows=rows(d,"claims.txt");
        try(PreparedStatement p=c.prepareStatement("INSERT INTO campus_claims(id,item_id,claimant_id,proof,status,created_at) VALUES(?,?,?,?,?,?)")){
            for(String[] r:rows){length("claims.txt",r,6);for(int i=0;i<6;i++)p.setString(i+1,r[i]);p.addBatch();}
            p.executeBatch();
        }return rows.size();
    }

    private static int importProposals(Connection c, Path d) throws Exception {
        List<String[]> rows=rows(d,"item_edits.txt");
        String sql="INSERT INTO campus_item_edit_proposals(id,item_id,reporter_id,title,description,category,type,location,item_date,contact_phone,photo_name,submitted_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement p=c.prepareStatement(sql)){
            for(String[] r:rows){length("item_edits.txt",r,12);for(int i=0;i<12;i++)p.setString(i+1,r[i]);p.addBatch();}
            p.executeBatch();
        }return rows.size();
    }

    private static int importNotifications(Connection c, Path d) throws Exception {
        List<String[]> rows=rows(d,"notifications.txt");
        try(PreparedStatement p=c.prepareStatement("INSERT INTO campus_notifications(id,user_id,message,target_url,created_at,status) VALUES(?,?,?,?,?,?)")){
            for(String[] r:rows){length("notifications.txt",r,6);for(int i=0;i<6;i++)p.setString(i+1,r[i]);p.addBatch();}
            p.executeBatch();
        }return rows.size();
    }

    private static int importAudit(Connection c, Path d) throws Exception {
        List<String[]> rows=rows(d,"audit.txt");
        try(PreparedStatement p=c.prepareStatement("INSERT INTO campus_audit_logs(created_at,actor_id,action,detail) VALUES(?,?,?,?)")){
            for(String[] r:rows){length("audit.txt",r,4);for(int i=0;i<4;i++)p.setString(i+1,r[i]);p.addBatch();}
            p.executeBatch();
        }return rows.size();
    }

    private static int importHandovers(Connection c, Path d) throws Exception {
        List<String[]> rows=rows(d,"handovers.txt");
        try(PreparedStatement p=c.prepareStatement("INSERT INTO campus_handovers(item_id,owner_id,claimant_id,owner_confirm,claimant_confirm,status) VALUES(?,?,?,?,?,?)")){
            for(String[] r:rows){length("handovers.txt",r,6);for(int i=0;i<6;i++)p.setString(i+1,r[i]);p.addBatch();}
            p.executeBatch();
        }return rows.size();
    }

    private static int importSavedItems(Connection c, Path d) throws Exception {
        List<String[]> rows=rows(d,"saved-items.txt");
        try(PreparedStatement p=c.prepareStatement("INSERT INTO campus_saved_items(user_id,item_id,saved_at) VALUES(?,?,?)")){
            for(String[] r:rows){length("saved-items.txt",r,3);for(int i=0;i<3;i++)p.setString(i+1,r[i]);p.addBatch();}
            p.executeBatch();
        }return rows.size();
    }

    private static int importFlags(Connection c, Path d) throws Exception {
        List<String[]> rows=rows(d,"listing-flags.txt");
        try(PreparedStatement p=c.prepareStatement("INSERT INTO campus_listing_flags(id,user_id,item_id,reason,status) VALUES(?,?,?,?,?)")){
            for(String[] r:rows){length("listing-flags.txt",r,5);for(int i=0;i<5;i++)p.setString(i+1,r[i]);p.addBatch();}
            p.executeBatch();
        }return rows.size();
    }
}

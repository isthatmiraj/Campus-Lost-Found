package com.campus.lostfound.util;

import com.campus.lostfound.model.Claim;
import com.campus.lostfound.model.Item;
import com.campus.lostfound.model.User;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class DataStore {
    private static final DataStore INSTANCE = new DataStore();
    private final Path dir;

    private DataStore() {
        String configured = System.getProperty("campus.data.dir");
        dir = (configured == null || configured.isBlank())
            ? Paths.get(System.getProperty("user.home"), ".campus-lost-found", "data")
            : Paths.get(configured);
        try { Files.createDirectories(dir); }
        catch (IOException e) { throw new IllegalStateException("Cannot initialize data directory: " + dir, e); }
    }

    public static DataStore get() { return INSTANCE; }
    /** Kept for legacy photo storage until ImageKit migration is completed. */
    public Path getDataDirectory() { return dir; }

    private IOException io(SQLException e) { return new IOException("PostgreSQL storage operation failed: " + e.getMessage(), e); }

    private User user(ResultSet r) throws SQLException {
        return new User(r.getString("id"), r.getString("name"), r.getString("email"),
            r.getString("password_hash"), r.getString("role"));
    }
    private Item item(ResultSet r) throws SQLException {
        return new Item(r.getString("id"), r.getString("reporter_id"), r.getString("title"),
            r.getString("description"), r.getString("category"), r.getString("type"),
            r.getString("location"), r.getString("item_date"), r.getString("status"),
            r.getString("contact_phone"), r.getString("photo_name"), r.getString("approval_status"));
    }
    private Claim claim(ResultSet r) throws SQLException {
        return new Claim(r.getString("id"), r.getString("item_id"), r.getString("claimant_id"),
            r.getString("proof"), r.getString("status"), r.getString("created_at"));
    }

    public synchronized List<User> allUsers() throws IOException {
        List<User> out = new ArrayList<>();
        try (Connection c = ChatDatabase.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM campus_users ORDER BY ctid DESC")) {
            while (r.next()) out.add(user(r));
            return out;
        } catch (SQLException e) { throw io(e); }
    }

    public synchronized User findUserByEmail(String email) throws IOException {
        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement(
                 "SELECT * FROM campus_users WHERE lower(email)=lower(?) LIMIT 1")) {
            p.setString(1, email);
            try (ResultSet r = p.executeQuery()) { return r.next() ? user(r) : null; }
        } catch (SQLException e) { throw io(e); }
    }

    public synchronized User findUserById(String id) throws IOException {
        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement("SELECT * FROM campus_users WHERE id=?")) {
            p.setString(1, id);
            try (ResultSet r = p.executeQuery()) { return r.next() ? user(r) : null; }
        } catch (SQLException e) { throw io(e); }
    }

    public synchronized User createUser(String name, String email, String passwordHash, String role) throws IOException {
        if (findUserByEmail(email) != null)
            throw new IllegalArgumentException("An account with that email already exists.");
        User u = new User(UUID.randomUUID().toString(), name, email.toLowerCase(Locale.ROOT), passwordHash, role);
        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement(
                 "INSERT INTO campus_users(id,name,email,password_hash,role) VALUES(?,?,?,?,?)")) {
            p.setString(1, u.getId()); p.setString(2, u.getName()); p.setString(3, u.getEmail());
            p.setString(4, u.getPasswordHash()); p.setString(5, u.getRole()); p.executeUpdate();
            return u;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState()))
                throw new IllegalArgumentException("An account with that email already exists.", e);
            throw io(e);
        }
    }

    public synchronized void updatePasswordHash(String userId, String passwordHash) throws IOException {
        try (Connection c = ChatDatabase.getConnection();
             PreparedStatement p = c.prepareStatement("UPDATE campus_users SET password_hash=? WHERE id=?")) {
            p.setString(1, passwordHash); p.setString(2, userId);
            if (p.executeUpdate() == 0) throw new IllegalArgumentException("User not found.");
        } catch (SQLException e) { throw io(e); }
    }

    public synchronized void ensureAdmin(String email, String passwordHash) throws IOException {
        if (email != null && !email.isBlank() && findUserByEmail(email) == null)
            createUser("Administrator", email.trim().toLowerCase(Locale.ROOT), passwordHash, "ADMIN");
    }

    public synchronized void deleteUser(String userId) throws IOException {
        List<String> photos = new ArrayList<>();
        try (Connection c = ChatDatabase.getConnection()) {
            c.setAutoCommit(false);
            try {
                User target;
                try (PreparedStatement p = c.prepareStatement("SELECT * FROM campus_users WHERE id=? FOR UPDATE")) {
                    p.setString(1, userId);
                    try (ResultSet r = p.executeQuery()) { target = r.next() ? user(r) : null; }
                }
                if (target == null) throw new IllegalArgumentException("User not found.");
                if (target.isAdmin()) {
                    try (Statement s = c.createStatement();
                         ResultSet r = s.executeQuery("SELECT COUNT(*) FROM campus_users WHERE role='ADMIN'")) {
                        r.next();
                        if (r.getLong(1) <= 1) throw new IllegalArgumentException("The last administrator cannot be deleted.");
                    }
                }
                try (PreparedStatement p = c.prepareStatement("SELECT photo_name FROM campus_items WHERE reporter_id=?")) {
                    p.setString(1, userId);
                    try (ResultSet r = p.executeQuery()) { while (r.next()) photos.add(r.getString(1)); }
                }
                try (PreparedStatement p = c.prepareStatement(
                    "DELETE FROM campus_claims WHERE claimant_id=? OR item_id IN (SELECT id FROM campus_items WHERE reporter_id=?)")) {
                    p.setString(1, userId); p.setString(2, userId); p.executeUpdate();
                }
                String[] cleanup = {
                    "DELETE FROM campus_saved_items WHERE user_id=? OR item_id IN (SELECT id FROM campus_items WHERE reporter_id=?)",
                    "DELETE FROM campus_listing_flags WHERE user_id=? OR item_id IN (SELECT id FROM campus_items WHERE reporter_id=?)",
                    "DELETE FROM campus_notifications WHERE user_id=?",
                    "DELETE FROM campus_item_edit_proposals WHERE reporter_id=? OR item_id IN (SELECT id FROM campus_items WHERE reporter_id=?)",
                    "DELETE FROM campus_handovers WHERE owner_id=? OR claimant_id=? OR item_id IN (SELECT id FROM campus_items WHERE reporter_id=?)",
                    "DELETE FROM campus_items WHERE reporter_id=?",
                    "DELETE FROM campus_users WHERE id=?"
                };
                for (int i=0;i<cleanup.length;i++) {
                    try (PreparedStatement p = c.prepareStatement(cleanup[i])) {
                        if (i==0 || i==1 || i==3) { p.setString(1,userId); p.setString(2,userId); }
                        else if (i==2 || i==5 || i==6) p.setString(1,userId);
                        else { p.setString(1,userId); p.setString(2,userId); p.setString(3,userId); }
                        p.executeUpdate();
                    }
                }
                c.commit();
            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException x) { e.addSuppressed(x); }
                if (e instanceof IOException) throw (IOException)e;
                if (e instanceof RuntimeException) throw (RuntimeException)e;
                throw new IOException(e);
            } finally { try { c.setAutoCommit(true); } catch (SQLException ignored) {} }
        } catch (SQLException e) { throw io(e); }
        deleteLegacyPhotos(photos);
    }

    public synchronized void deleteItem(String itemId) throws IOException {
        List<String> photos = new ArrayList<>();
        try (Connection c = ChatDatabase.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("SELECT photo_name FROM campus_items WHERE id=? FOR UPDATE")) {
                    p.setString(1,itemId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Listing not found.");
                        photos.add(r.getString(1));
                    }
                }
                String[] sql = {
                    "DELETE FROM campus_claims WHERE item_id=?",
                    "DELETE FROM campus_saved_items WHERE item_id=?",
                    "DELETE FROM campus_listing_flags WHERE item_id=?",
                    "DELETE FROM campus_item_edit_proposals WHERE item_id=?",
                    "DELETE FROM campus_handovers WHERE item_id=?",
                    "DELETE FROM campus_items WHERE id=?"
                };
                for (String q : sql) try (PreparedStatement p=c.prepareStatement(q)) {
                    p.setString(1,itemId); p.executeUpdate();
                }
                c.commit();
            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException x) { e.addSuppressed(x); }
                if (e instanceof IOException) throw (IOException)e;
                if (e instanceof RuntimeException) throw (RuntimeException)e;
                throw new IOException(e);
            } finally { try { c.setAutoCommit(true); } catch (SQLException ignored) {} }
        } catch (SQLException e) { throw io(e); }
        deleteLegacyPhotos(photos);
    }

    private void deleteLegacyPhotos(List<String> names) throws IOException {
        Path uploads = dir.resolve("uploads").normalize();
        for (String name : names) {
            if (name == null || !name.matches("[a-fA-F0-9-]+\\.(jpg|png)")) continue;
            Path p = uploads.resolve(name).normalize();
            if (p.getParent().equals(uploads)) Files.deleteIfExists(p);
        }
    }

    public synchronized List<Item> allItems() throws IOException {
        List<Item> out = new ArrayList<>();
        try (Connection c=ChatDatabase.getConnection(); Statement s=c.createStatement();
             ResultSet r=s.executeQuery("SELECT * FROM campus_items ORDER BY ctid DESC")) {
            while (r.next()) out.add(item(r));
            return out;
        } catch (SQLException e) { throw io(e); }
    }

    public synchronized Item findItem(String id) throws IOException {
        try (Connection c=ChatDatabase.getConnection();
             PreparedStatement p=c.prepareStatement("SELECT * FROM campus_items WHERE id=?")) {
            p.setString(1,id);
            try (ResultSet r=p.executeQuery()) { return r.next()?item(r):null; }
        } catch (SQLException e) { throw io(e); }
    }

    public synchronized Item createItem(String reporterId,String title,String description,String category,
            String type,String location,String date,String contactPhone) throws IOException {
        return createItem(reporterId,title,description,category,type,location,date,contactPhone,"");
    }

    public synchronized Item createItem(String reporterId,String title,String description,String category,
            String type,String location,String date,String contactPhone,String photoName) throws IOException {
        Item item = new Item(UUID.randomUUID().toString(),reporterId,title,description,category,type,location,date,
            "OPEN",contactPhone,photoName,"PENDING");
        String sql="INSERT INTO campus_items(id,reporter_id,title,description,category,type,location,item_date,status,contact_phone,photo_name,approval_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,item.getId());p.setString(2,item.getReporterId());p.setString(3,item.getTitle());
            p.setString(4,item.getDescription());p.setString(5,item.getCategory());p.setString(6,item.getType());
            p.setString(7,item.getLocation());p.setString(8,item.getDate());p.setString(9,item.getStatus());
            p.setString(10,item.getContactPhone());p.setString(11,item.getPhotoName());p.setString(12,item.getApprovalStatus());
            p.executeUpdate(); return item;
        } catch(SQLException e){throw io(e);}
    }

    public synchronized Item createItem(String reporterId,String title,String description,String category,
            String type,String location,String date) throws IOException {
        return createItem(reporterId,title,description,category,type,location,date,"");
    }

    public synchronized void setItemStatus(String itemId,String status) throws IOException {
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "UPDATE campus_items SET status=? WHERE id=?")) {
            p.setString(1,status);p.setString(2,itemId);
            if(p.executeUpdate()==0)throw new IllegalArgumentException("Item not found.");
        } catch(SQLException e){throw io(e);}
    }

    public synchronized void setItemApprovalStatus(String itemId,String approvalStatus) throws IOException {
        if(!"APPROVED".equals(approvalStatus)&&!"REJECTED".equals(approvalStatus))
            throw new IllegalArgumentException("Invalid item approval status.");
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "UPDATE campus_items SET approval_status=? WHERE id=?")) {
            p.setString(1,approvalStatus);p.setString(2,itemId);
            if(p.executeUpdate()==0)throw new IllegalArgumentException("Item not found.");
        } catch(SQLException e){throw io(e);}
    }

    public synchronized Claim createClaim(String itemId,String claimantId,String proof) throws IOException {
        try(Connection c=ChatDatabase.getConnection()) {
            c.setAutoCommit(false);
            try {
                Item item;
                try(PreparedStatement p=c.prepareStatement("SELECT * FROM campus_items WHERE id=? FOR UPDATE")) {
                    p.setString(1,itemId);
                    try(ResultSet r=p.executeQuery()){item=r.next()?item(r):null;}
                }
                if(item==null)throw new IllegalArgumentException("Item not found.");
                if(!"APPROVED".equals(item.getApprovalStatus()))throw new IllegalArgumentException("This item has not been approved for public claims.");
                if(!"FOUND".equals(item.getType()))throw new IllegalArgumentException("Claims can only be submitted for found items.");
                if(!"OPEN".equals(item.getStatus()))throw new IllegalArgumentException("This item is not open for claims.");
                if(item.getReporterId().equals(claimantId))throw new IllegalArgumentException("You cannot claim an item you reported.");
                try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM campus_claims WHERE item_id=? AND claimant_id=? AND status='PENDING'")) {
                    p.setString(1,itemId);p.setString(2,claimantId);
                    try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("You already have a pending claim for this item.");}
                }
                Claim claim=new Claim(UUID.randomUUID().toString(),itemId,claimantId,proof,"PENDING",Instant.now().toString());
                try(PreparedStatement p=c.prepareStatement("INSERT INTO campus_claims(id,item_id,claimant_id,proof,status,created_at) VALUES(?,?,?,?,?,?)")) {
                    p.setString(1,claim.getId());p.setString(2,itemId);p.setString(3,claimantId);p.setString(4,proof);
                    p.setString(5,"PENDING");p.setString(6,claim.getCreatedAt());p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE campus_items SET status='CLAIM_PENDING' WHERE id=?")) {
                    p.setString(1,itemId);p.executeUpdate();
                }
                c.commit(); return claim;
            } catch(Exception e) {
                try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}
                if(e instanceof IOException)throw(IOException)e;
                if(e instanceof RuntimeException)throw(RuntimeException)e;
                throw new IOException(e);
            } finally {try{c.setAutoCommit(true);}catch(SQLException ignored){}}
        } catch(SQLException e){throw io(e);}
    }

    public synchronized List<Claim> allClaims() throws IOException {
        List<Claim> out=new ArrayList<>();
        try(Connection c=ChatDatabase.getConnection();Statement s=c.createStatement();
            ResultSet r=s.executeQuery("SELECT * FROM campus_claims ORDER BY ctid DESC")) {
            while(r.next())out.add(claim(r));return out;
        }catch(SQLException e){throw io(e);}
    }

    public synchronized List<Claim> claimsForUser(String userId) throws IOException {
        List<Claim> out=new ArrayList<>();
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "SELECT * FROM campus_claims WHERE claimant_id=? ORDER BY ctid DESC")) {
            p.setString(1,userId);try(ResultSet r=p.executeQuery()){while(r.next())out.add(claim(r));}
            return out;
        }catch(SQLException e){throw io(e);}
    }

    public synchronized void reviewClaim(String claimId,String decision) throws IOException {
        if(!"APPROVED".equals(decision)&&!"REJECTED".equals(decision))
            throw new IllegalArgumentException("Invalid claim decision.");
        try(Connection c=ChatDatabase.getConnection()) {
            c.setAutoCommit(false);
            try {
                String itemId; String status;
                try(PreparedStatement p=c.prepareStatement("SELECT item_id,status FROM campus_claims WHERE id=? FOR UPDATE")) {
                    p.setString(1,claimId);try(ResultSet r=p.executeQuery()){
                        if(!r.next())throw new IllegalArgumentException("Claim not found.");
                        itemId=r.getString(1);status=r.getString(2);
                    }
                }
                if(!"PENDING".equals(status))throw new IllegalArgumentException("This claim has already been reviewed.");
                try(PreparedStatement p=c.prepareStatement("UPDATE campus_claims SET status=? WHERE id=?")) {
                    p.setString(1,decision);p.setString(2,claimId);p.executeUpdate();
                }
                String itemStatus;
                if("APPROVED".equals(decision)) itemStatus="RETURN_PENDING";
                else {
                    try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM campus_claims WHERE item_id=? AND status='PENDING'")) {
                        p.setString(1,itemId);try(ResultSet r=p.executeQuery()){itemStatus=r.next()?"CLAIM_PENDING":"OPEN";}
                    }
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE campus_items SET status=? WHERE id=?")) {
                    p.setString(1,itemStatus);p.setString(2,itemId);p.executeUpdate();
                }
                c.commit();
            }catch(Exception e){
                try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}
                if(e instanceof IOException)throw(IOException)e;
                if(e instanceof RuntimeException)throw(RuntimeException)e;
                throw new IOException(e);
            }finally{try{c.setAutoCommit(true);}catch(SQLException ignored){}}
        }catch(SQLException e){throw io(e);}
    }

    public synchronized void saveItemEditProposal(String itemId,String reporterId,String title,String description,
            String category,String type,String location,String date,String contactPhone,String photoName) throws IOException {
        try(Connection c=ChatDatabase.getConnection()) {
            c.setAutoCommit(false);
            try {
                Item current;
                try(PreparedStatement p=c.prepareStatement("SELECT * FROM campus_items WHERE id=? FOR UPDATE")) {
                    p.setString(1,itemId);try(ResultSet r=p.executeQuery()){current=r.next()?item(r):null;}
                }
                if(current==null)throw new IllegalArgumentException("Listing not found.");
                if(!current.getReporterId().equals(reporterId))throw new IllegalArgumentException("You can only edit your own listing.");
                if(!"APPROVED".equals(current.getApprovalStatus()))throw new IllegalArgumentException("Only approved listings can be edited.");
                String id=UUID.randomUUID().toString();
                try(PreparedStatement p=c.prepareStatement(
                    "INSERT INTO campus_item_edit_proposals(id,item_id,reporter_id,title,description,category,type,location,item_date,contact_phone,photo_name,submitted_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    p.setString(1,id);p.setString(2,itemId);p.setString(3,reporterId);p.setString(4,title);p.setString(5,description);
                    p.setString(6,category);p.setString(7,type);p.setString(8,location);p.setString(9,date);
                    p.setString(10,contactPhone==null?"":contactPhone);p.setString(11,photoName==null?"":photoName);
                    p.setString(12,Instant.now().toString());p.executeUpdate();
                }
                c.commit();
            }catch(Exception e){
                try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}
                if(e instanceof IOException)throw(IOException)e;
                if(e instanceof RuntimeException)throw(RuntimeException)e;
                if(e instanceof SQLException && "23505".equals(((SQLException)e).getSQLState()))
                    throw new IllegalArgumentException("This listing already has an edit awaiting review.",e);
                throw new IOException(e);
            }finally{try{c.setAutoCommit(true);}catch(SQLException ignored){}}
        }catch(SQLException e){
            if("23505".equals(e.getSQLState()))throw new IllegalArgumentException("This listing already has an edit awaiting review.",e);
            throw io(e);
        }
    }

    public synchronized List<String[]> pendingItemEditProposals() throws IOException {
        List<String[]> out=new ArrayList<>();
        String sql="SELECT id,item_id,reporter_id,title,description,category,type,location,item_date,contact_phone,photo_name,submitted_at FROM campus_item_edit_proposals ORDER BY submitted_at DESC";
        try(Connection c=ChatDatabase.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){
            while(r.next())out.add(proposal(r));
            return out;
        }catch(SQLException e){throw io(e);}
    }

    private String[] proposal(ResultSet r)throws SQLException {
        return new String[]{r.getString("id"),r.getString("item_id"),r.getString("reporter_id"),
            r.getString("title"),r.getString("description"),r.getString("category"),r.getString("type"),
            r.getString("location"),r.getString("item_date"),r.getString("contact_phone"),
            r.getString("photo_name"),r.getString("submitted_at")};
    }

    public synchronized String[] findItemEditProposal(String proposalId) throws IOException {
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "SELECT id,item_id,reporter_id,title,description,category,type,location,item_date,contact_phone,photo_name,submitted_at FROM campus_item_edit_proposals WHERE id=?")) {
            p.setString(1,proposalId);try(ResultSet r=p.executeQuery()){return r.next()?proposal(r):null;}
        }catch(SQLException e){throw io(e);}
    }

    public synchronized Item applyItemEditProposal(String proposalId) throws IOException {
        try(Connection c=ChatDatabase.getConnection()){
            c.setAutoCommit(false);
            try {
                String[] pr;
                try(PreparedStatement p=c.prepareStatement(
                    "SELECT id,item_id,reporter_id,title,description,category,type,location,item_date,contact_phone,photo_name,submitted_at FROM campus_item_edit_proposals WHERE id=? FOR UPDATE")){
                    p.setString(1,proposalId);try(ResultSet r=p.executeQuery()){pr=r.next()?proposal(r):null;}
                }
                if(pr==null)throw new IllegalArgumentException("Edit proposal not found.");
                Item current;
                try(PreparedStatement p=c.prepareStatement("SELECT * FROM campus_items WHERE id=? FOR UPDATE")){
                    p.setString(1,pr[1]);try(ResultSet r=p.executeQuery()){current=r.next()?item(r):null;}
                }
                if(current==null)throw new IllegalArgumentException("The original listing no longer exists.");
                if(!current.getReporterId().equals(pr[2]))throw new IllegalArgumentException("The proposal owner does not match the listing owner.");
                if(!"APPROVED".equals(current.getApprovalStatus()))throw new IllegalArgumentException("The original listing is no longer approved.");
                try(PreparedStatement p=c.prepareStatement(
                    "UPDATE campus_items SET title=?,description=?,category=?,type=?,location=?,item_date=?,contact_phone=?,photo_name=?,approval_status='APPROVED' WHERE id=?")){
                    for(int i=0;i<8;i++)p.setString(i+1,pr[i+3]);p.setString(9,pr[1]);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("DELETE FROM campus_item_edit_proposals WHERE id=?")){p.setString(1,proposalId);p.executeUpdate();}
                Item updated;
                try(PreparedStatement p=c.prepareStatement("SELECT * FROM campus_items WHERE id=?")){p.setString(1,pr[1]);try(ResultSet r=p.executeQuery()){updated=r.next()?item(r):null;}}
                c.commit();return updated;
            }catch(Exception e){
                try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}
                if(e instanceof IOException)throw(IOException)e;
                if(e instanceof RuntimeException)throw(RuntimeException)e;
                throw new IOException(e);
            }finally{try{c.setAutoCommit(true);}catch(SQLException ignored){}}
        }catch(SQLException e){throw io(e);}
    }

    public synchronized String[] removeItemEditProposal(String proposalId) throws IOException {
        try(Connection c=ChatDatabase.getConnection()){
            c.setAutoCommit(false);
            try{
                String[] selected;
                try(PreparedStatement p=c.prepareStatement(
                    "SELECT id,item_id,reporter_id,title,description,category,type,location,item_date,contact_phone,photo_name,submitted_at FROM campus_item_edit_proposals WHERE id=? FOR UPDATE")){
                    p.setString(1,proposalId);try(ResultSet r=p.executeQuery()){selected=r.next()?proposal(r):null;}
                }
                if(selected==null)throw new IllegalArgumentException("Edit proposal not found.");
                try(PreparedStatement p=c.prepareStatement("DELETE FROM campus_item_edit_proposals WHERE id=?")){p.setString(1,proposalId);p.executeUpdate();}
                c.commit();return selected;
            }catch(Exception e){
                try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}
                if(e instanceof IOException)throw(IOException)e;
                if(e instanceof RuntimeException)throw(RuntimeException)e;
                throw new IOException(e);
            }finally{try{c.setAutoCommit(true);}catch(SQLException ignored){}}
        }catch(SQLException e){throw io(e);}
    }
}

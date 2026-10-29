package com.campus.lostfound.util;

import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CommunityStore {
    private static final CommunityStore INSTANCE = new CommunityStore();
    private CommunityStore() {}
    public static CommunityStore get() { return INSTANCE; }

    private IOException io(SQLException e) { return new IOException("PostgreSQL community operation failed: " + e.getMessage(), e); }

    public synchronized boolean isSaved(String uid,String itemId) throws IOException {
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "SELECT 1 FROM campus_saved_items WHERE user_id=? AND item_id=?")){
            p.setString(1,uid);p.setString(2,itemId);try(ResultSet r=p.executeQuery()){return r.next();}
        }catch(SQLException e){throw io(e);}
    }

    public synchronized void toggleSaved(String uid,String itemId) throws IOException {
        try(Connection c=ChatDatabase.getConnection()){
            c.setAutoCommit(false);
            try{
                int deleted;
                try(PreparedStatement p=c.prepareStatement("DELETE FROM campus_saved_items WHERE user_id=? AND item_id=?")){
                    p.setString(1,uid);p.setString(2,itemId);deleted=p.executeUpdate();
                }
                if(deleted==0){
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO campus_saved_items(user_id,item_id,saved_at) VALUES(?,?,?)")){
                        p.setString(1,uid);p.setString(2,itemId);p.setString(3,Instant.now().toString());p.executeUpdate();
                    }
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

    public synchronized List<String> savedIds(String uid) throws IOException {
        List<String> out=new ArrayList<>();
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "SELECT item_id FROM campus_saved_items WHERE user_id=? ORDER BY saved_at DESC")){
            p.setString(1,uid);try(ResultSet r=p.executeQuery()){while(r.next())out.add(r.getString(1));}
            return out;
        }catch(SQLException e){throw io(e);}
    }

    public synchronized void flag(String uid,String itemId,String reason) throws IOException {
        try(Connection c=ChatDatabase.getConnection()){
            c.setAutoCommit(false);
            try{
                try(PreparedStatement p=c.prepareStatement(
                    "SELECT 1 FROM campus_listing_flags WHERE user_id=? AND item_id=? AND status='OPEN'")){
                    p.setString(1,uid);p.setString(2,itemId);
                    try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("You have already flagged this listing.");}
                }
                try(PreparedStatement p=c.prepareStatement(
                    "INSERT INTO campus_listing_flags(id,user_id,item_id,reason,status) VALUES(?,?,?,?,'OPEN')")){
                    p.setString(1,UUID.randomUUID().toString());p.setString(2,uid);p.setString(3,itemId);p.setString(4,reason);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement(
                    "INSERT INTO campus_audit_logs(created_at,actor_id,action,detail) VALUES(?,?,?,?)")){
                    p.setString(1,Instant.now().toString());p.setString(2,uid);p.setString(3,"LISTING_FLAGGED");
                    p.setString(4,"Item "+itemId+": "+reason);p.executeUpdate();
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

    public synchronized List<String[]> flags() throws IOException {
        List<String[]> out=new ArrayList<>();
        try(Connection c=ChatDatabase.getConnection();Statement s=c.createStatement();
            ResultSet r=s.executeQuery("SELECT id,user_id,item_id,reason,status FROM campus_listing_flags ORDER BY ctid DESC")){
            while(r.next())out.add(new String[]{r.getString("id"),r.getString("user_id"),
                r.getString("item_id"),r.getString("reason"),r.getString("status")});
            return out;
        }catch(SQLException e){throw io(e);}
    }

    public synchronized void resolveFlag(String id,String status) throws IOException {
        if(!"REVIEWED".equals(status)&&!"DISMISSED".equals(status))
            throw new IllegalArgumentException("Invalid flag decision.");
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "UPDATE campus_listing_flags SET status=? WHERE id=?")){
            p.setString(1,status);p.setString(2,id);
            if(p.executeUpdate()==0)throw new IllegalArgumentException("Listing flag not found.");
        }catch(SQLException e){throw io(e);}
    }
}

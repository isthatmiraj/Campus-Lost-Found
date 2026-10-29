package com.campus.lostfound.util;

import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class WorkflowStore {
    private static final WorkflowStore INSTANCE = new WorkflowStore();
    private WorkflowStore() {}
    public static WorkflowStore get() { return INSTANCE; }

    private IOException io(SQLException e) { return new IOException("PostgreSQL workflow operation failed: " + e.getMessage(), e); }

    public synchronized void audit(String actorId, String action, String detail) throws IOException {
        String sql = "INSERT INTO campus_audit_logs(created_at,actor_id,action,detail) VALUES(?,?,?,?)";
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,Instant.now().toString());p.setString(2,actorId==null?"":actorId);
            p.setString(3,action);p.setString(4,detail==null?"":detail);p.executeUpdate();
        }catch(SQLException e){throw io(e);}
    }

    public synchronized List<String[]> recentAudit(int limit) throws IOException {
        if(limit<=0)return new ArrayList<>();
        List<String[]> out=new ArrayList<>();
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "SELECT created_at,actor_id,action,detail FROM campus_audit_logs ORDER BY created_at DESC,id DESC LIMIT ?")){
            p.setInt(1,limit);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new String[]{
                r.getString("created_at"),r.getString("actor_id"),r.getString("action"),r.getString("detail")});}
            return out;
        }catch(SQLException e){throw io(e);}
    }

    public synchronized void notifyUser(String userId,String message,String url) throws IOException {
        if(userId==null||userId.isBlank())return;
        String sql="INSERT INTO campus_notifications(id,user_id,message,target_url,created_at,status) VALUES(?,?,?,?,?,'UNREAD')";
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,UUID.randomUUID().toString());p.setString(2,userId);p.setString(3,message);
            p.setString(4,url==null?"":url);p.setString(5,Instant.now().toString());p.executeUpdate();
        }catch(SQLException e){throw io(e);}
    }

    public synchronized List<String[]> notificationsFor(String userId) throws IOException {
        List<String[]> out=new ArrayList<>();
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "SELECT id,user_id,message,target_url,created_at,status FROM campus_notifications WHERE user_id=? ORDER BY created_at DESC")){
            p.setString(1,userId);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new String[]{
                r.getString("id"),r.getString("user_id"),r.getString("message"),r.getString("target_url"),
                r.getString("created_at"),r.getString("status")});}
            return out;
        }catch(SQLException e){throw io(e);}
    }

    public synchronized long unreadCount(String userId) throws IOException {
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "SELECT COUNT(*) FROM campus_notifications WHERE user_id=? AND status='UNREAD'")){
            p.setString(1,userId);try(ResultSet r=p.executeQuery()){r.next();return r.getLong(1);}
        }catch(SQLException e){throw io(e);}
    }

    public synchronized void markRead(String userId,String notificationId) throws IOException {
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "UPDATE campus_notifications SET status='READ' WHERE user_id=? AND id=?")){
            p.setString(1,userId);p.setString(2,notificationId);p.executeUpdate();
        }catch(SQLException e){throw io(e);}
    }

    public synchronized void createHandover(String itemId,String ownerId,String claimantId) throws IOException {
        String sql = """
            INSERT INTO campus_handovers(item_id,owner_id,claimant_id,owner_confirm,claimant_confirm,status)
            VALUES(?,?,?,'NO','NO','PENDING')
            ON CONFLICT(item_id) DO UPDATE SET owner_id=EXCLUDED.owner_id,
              claimant_id=EXCLUDED.claimant_id,owner_confirm='NO',
              claimant_confirm='NO',status='PENDING'
            WHERE campus_handovers.status='CANCELLED'
            """;
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,itemId);p.setString(2,ownerId);p.setString(3,claimantId);p.executeUpdate();
        }catch(SQLException e){
            if("23505".equals(e.getSQLState()))return;
            throw io(e);
        }
    }

    public synchronized String confirmHandover(String itemId,String userId) throws IOException {
        try(Connection c=ChatDatabase.getConnection()){
            c.setAutoCommit(false);
            try{
                String owner,claimant,ownerConfirm,claimantConfirm;
                try(PreparedStatement p=c.prepareStatement(
                    "SELECT owner_id,claimant_id,owner_confirm,claimant_confirm FROM campus_handovers WHERE item_id=? AND status='PENDING' FOR UPDATE")){
                    p.setString(1,itemId);try(ResultSet r=p.executeQuery()){
                        if(!r.next())throw new IllegalArgumentException("No pending return confirmation was found for this item.");
                        owner=r.getString(1);claimant=r.getString(2);ownerConfirm=r.getString(3);claimantConfirm=r.getString(4);
                    }
                }
                if(userId.equals(owner))ownerConfirm="YES";
                else if(userId.equals(claimant))claimantConfirm="YES";
                else throw new IllegalArgumentException("Only the reporter and approved claimant can confirm this return.");
                String status=("YES".equals(ownerConfirm)&&"YES".equals(claimantConfirm))?"RETURNED":"PENDING";
                try(PreparedStatement p=c.prepareStatement(
                    "UPDATE campus_handovers SET owner_confirm=?,claimant_confirm=?,status=? WHERE item_id=?")){
                    p.setString(1,ownerConfirm);p.setString(2,claimantConfirm);p.setString(3,status);p.setString(4,itemId);p.executeUpdate();
                }
                c.commit();return status;
            }catch(Exception e){
                try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}
                if(e instanceof IOException)throw(IOException)e;
                if(e instanceof RuntimeException)throw(RuntimeException)e;
                throw new IOException(e);
            }finally{try{c.setAutoCommit(true);}catch(SQLException ignored){}}
        }catch(SQLException e){throw io(e);}
    }

    public synchronized List<String[]> handoversFor(String userId) throws IOException {
        List<String[]> out=new ArrayList<>();
        try(Connection c=ChatDatabase.getConnection();PreparedStatement p=c.prepareStatement(
            "SELECT item_id,owner_id,claimant_id,owner_confirm,claimant_confirm,status FROM campus_handovers WHERE owner_id=? OR claimant_id=? ORDER BY item_id DESC")){
            p.setString(1,userId);p.setString(2,userId);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new String[]{
                r.getString("item_id"),r.getString("owner_id"),r.getString("claimant_id"),
                r.getString("owner_confirm"),r.getString("claimant_confirm"),r.getString("status")});}
            return out;
        }catch(SQLException e){throw io(e);}
    }
}

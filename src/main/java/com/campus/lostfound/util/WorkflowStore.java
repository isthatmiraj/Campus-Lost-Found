package com.campus.lostfound.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Additive workflow files. Existing users/items/claims formats are not changed. */
public final class WorkflowStore {
    private static final WorkflowStore INSTANCE = new WorkflowStore();
    private final Path dir, notifications, audit, handovers;
    private WorkflowStore() {
        dir = DataStore.get().getDataDirectory();
        notifications = dir.resolve("notifications.txt"); audit = dir.resolve("audit.txt"); handovers = dir.resolve("handovers.txt");
        try { Files.createDirectories(dir); for (Path p : List.of(notifications,audit,handovers)) if (Files.notExists(p)) Files.createFile(p); }
        catch (IOException e) { throw new IllegalStateException("Cannot initialize workflow storage", e); }
    }
    public static WorkflowStore get() { return INSTANCE; }
    private String enc(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString((s==null?"":s).getBytes(StandardCharsets.UTF_8)); }
    private String dec(String s) { return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8); }
    private String row(String... f) { return Arrays.stream(f).map(this::enc).collect(Collectors.joining("|")); }
    private String[] parse(String line) { return Arrays.stream(line.split("\\|",-1)).map(this::dec).toArray(String[]::new); }
    private List<String> lines(Path p) throws IOException { return Files.readAllLines(p, StandardCharsets.UTF_8); }
    private void append(Path p,String r) throws IOException { Files.writeString(p,r+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
    public synchronized void audit(String actorId,String action,String detail) throws IOException { append(audit,row(Instant.now().toString(),actorId,action,detail)); }
    public synchronized List<String[]> recentAudit(int limit) throws IOException { List<String[]> a=new ArrayList<>(); for(String l:lines(audit)) if(!l.isBlank()){String[] f=parse(l);if(f.length==4)a.add(f);} Collections.reverse(a);return a.subList(0,Math.min(limit,a.size())); }
    public synchronized void notifyUser(String userId,String message,String url) throws IOException { if(userId!=null&&!userId.isBlank()) append(notifications,row(UUID.randomUUID().toString(),userId,message,url,Instant.now().toString(),"UNREAD")); }
    public synchronized List<String[]> notificationsFor(String userId) throws IOException { List<String[]> a=new ArrayList<>();for(String l:lines(notifications))if(!l.isBlank()){String[] f=parse(l);if(f.length==6&&f[1].equals(userId))a.add(f);}Collections.reverse(a);return a; }
    public synchronized long unreadCount(String userId) throws IOException { return notificationsFor(userId).stream().filter(n->"UNREAD".equals(n[5])).count(); }
    public synchronized void markRead(String userId,String notificationId) throws IOException { List<String> out=new ArrayList<>();for(String l:lines(notifications)){if(l.isBlank()){out.add(l);continue;}String[] f=parse(l);if(f.length==6&&f[0].equals(notificationId)&&f[1].equals(userId))f[5]="READ";out.add(row(f));}replace(notifications,out); }
    public synchronized void createHandover(String itemId,String ownerId,String claimantId) throws IOException {
        for(String l:lines(handovers)){if(l.isBlank())continue;String[] f=parse(l);if(f.length==6&&f[0].equals(itemId)&&!"CANCELLED".equals(f[5]))return;}
        append(handovers,row(itemId,ownerId,claimantId,"NO","NO","PENDING"));
    }
    public synchronized String confirmHandover(String itemId,String userId) throws IOException {
        List<String> out=new ArrayList<>();String result=null;boolean found=false;
        for(String l:lines(handovers)){if(l.isBlank()){out.add(l);continue;}String[] f=parse(l);if(f.length==6&&f[0].equals(itemId)&&"PENDING".equals(f[5])){
            if(userId.equals(f[1]))f[3]="YES";else if(userId.equals(f[2]))f[4]="YES";else throw new IllegalArgumentException("Only the reporter and approved claimant can confirm this return.");
            if("YES".equals(f[3])&&"YES".equals(f[4])){f[5]="RETURNED";result="RETURNED";}else result="PENDING";found=true;
        }out.add(row(f));}
        if(!found)throw new IllegalArgumentException("No pending return confirmation was found for this item.");replace(handovers,out);return result;
    }
    public synchronized List<String[]> handoversFor(String userId) throws IOException {List<String[]> a=new ArrayList<>();for(String l:lines(handovers)){if(l.isBlank())continue;String[] f=parse(l);if(f.length==6&&(f[1].equals(userId)||f[2].equals(userId)))a.add(f);}Collections.reverse(a);return a;}
    private void replace(Path p,List<String> rows) throws IOException {Path t=Files.createTempFile(dir,p.getFileName().toString(),".tmp");try{Files.write(t,rows,StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(t,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(t,p,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(t);}}
}

package com.campus.lostfound.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Additive storage for bookmarks and listing flags; core record formats remain unchanged. */
public final class CommunityStore {
    private static final CommunityStore INSTANCE = new CommunityStore();
    private final Path dir, saved, flags;
    private CommunityStore() {
        dir=DataStore.get().getDataDirectory(); saved=dir.resolve("saved-items.txt"); flags=dir.resolve("listing-flags.txt");
        try { Files.createDirectories(dir); if(Files.notExists(saved))Files.createFile(saved); if(Files.notExists(flags))Files.createFile(flags); }
        catch(IOException e){throw new IllegalStateException("Cannot initialize community storage",e);}
    }
    public static CommunityStore get(){return INSTANCE;}
    private String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString((s==null?"":s).getBytes(StandardCharsets.UTF_8));}
    private String dec(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
    private String row(String... a){return Arrays.stream(a).map(this::enc).collect(Collectors.joining("|"));}
    private String[] parse(String s){return Arrays.stream(s.split("\\|",-1)).map(this::dec).toArray(String[]::new);}
    private List<String> read(Path p)throws IOException{return Files.readAllLines(p,StandardCharsets.UTF_8);}
    private void write(Path p,List<String> rows)throws IOException{Path t=Files.createTempFile(dir,"community-",".tmp");try{Files.write(t,rows,StandardCharsets.UTF_8);try{Files.move(t,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(t,p,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(t);}}
    public synchronized boolean isSaved(String uid,String itemId)throws IOException{for(String l:read(saved)){if(l.isBlank())continue;String[] f=parse(l);if(f.length==3&&f[0].equals(uid)&&f[1].equals(itemId))return true;}return false;}
    public synchronized void toggleSaved(String uid,String itemId)throws IOException{List<String> out=new ArrayList<>();boolean removed=false;for(String l:read(saved)){if(l.isBlank())continue;String[] f=parse(l);if(f.length==3&&f[0].equals(uid)&&f[1].equals(itemId)){removed=true;continue;}out.add(l);}if(!removed)out.add(row(uid,itemId,Instant.now().toString()));write(saved,out);}
    public synchronized List<String> savedIds(String uid)throws IOException{List<String> a=new ArrayList<>();for(String l:read(saved)){if(l.isBlank())continue;String[] f=parse(l);if(f.length==3&&f[0].equals(uid))a.add(f[1]);}Collections.reverse(a);return a;}
    public synchronized void flag(String uid,String itemId,String reason)throws IOException{for(String l:read(flags)){if(l.isBlank())continue;String[] f=parse(l);if(f.length==5&&f[0].equals(uid)&&f[1].equals(itemId)&&"OPEN".equals(f[4]))throw new IllegalArgumentException("You have already flagged this listing.");}Files.writeString(flags,row(UUID.randomUUID().toString(),uid,itemId,reason, "OPEN")+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);WorkflowStore.get().audit(uid,"LISTING_FLAGGED","Item "+itemId+": "+reason);}
    public synchronized List<String[]> flags()throws IOException{List<String[]> a=new ArrayList<>();for(String l:read(flags)){if(l.isBlank())continue;String[] f=parse(l);if(f.length==5)a.add(f);}Collections.reverse(a);return a;}
    public synchronized void resolveFlag(String id,String status)throws IOException{if(!Set.of("REVIEWED","DISMISSED").contains(status))throw new IllegalArgumentException("Invalid flag decision.");List<String> out=new ArrayList<>();for(String l:read(flags)){if(l.isBlank()){out.add(l);continue;}String[] f=parse(l);if(f.length==5&&f[0].equals(id))f[4]=status;out.add(row(f));}write(flags,out);}
}

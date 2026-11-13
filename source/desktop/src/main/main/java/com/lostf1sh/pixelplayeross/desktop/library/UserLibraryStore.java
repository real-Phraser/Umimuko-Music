package com.lostf1sh.pixelplayeross.desktop.library;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Small offline library state. Atomic writes; failed writes never change the in-memory snapshot. */
public final class UserLibraryStore {
    public static final class Playlist {
        public final String id, name;
        public final List<String> paths;
        public Playlist(String id, String name, Collection<String> paths) {
            this.id=id;this.name=name;this.paths=List.copyOf(new LinkedHashSet<>(paths));
        }
    }
    public static final class Snapshot {
        public final Set<String> liked;
        public final List<Playlist> playlists;
        public Snapshot(Collection<String> liked, Collection<Playlist> playlists) {
            this.liked=Set.copyOf(liked); this.playlists=List.copyOf(playlists);
        }
        /** Java immutable sets reject null; before playback there is no current song. */
        public boolean isLiked(String path) { return path != null && liked.contains(path); }
    }
    private final Path file;
    private Snapshot current = new Snapshot(Set.of(),List.of());
    private boolean loaded;
    public UserLibraryStore(Path file) { this.file=file; }
    public static UserLibraryStore defaultStore() {
        String appdata=System.getenv("LOCALAPPDATA");
        Path base=appdata==null || appdata.isBlank() ? Path.of(System.getProperty("user.home"),".local","share") : Path.of(appdata);
        return new UserLibraryStore(base.resolve("PixelPlayerDesktop").resolve("library-v1.properties"));
    }
    public synchronized Snapshot load() throws IOException {
        if (!Files.exists(file)) { loaded=true; return current; }
        Properties p=new Properties();
        try(Reader reader=Files.newBufferedReader(file)) { p.load(reader); }
        if (!"1".equals(p.getProperty("version"))) throw new IOException("Unsupported or damaged library state; original file was kept.");
        try {
            List<String> likes=new ArrayList<>();
            for(int i=0,n=count(p,"liked.count");i<n;i++) likes.add(required(p,"liked."+i));
            List<Playlist> lists=new ArrayList<>();
            for(int i=0,n=count(p,"playlist.count");i<n;i++) {
                String prefix="playlist."+i+".";
                List<String> paths=new ArrayList<>();
                for(int j=0,m=count(p,prefix+"count");j<m;j++) paths.add(required(p,prefix+"track."+j));
                lists.add(new Playlist(required(p,prefix+"id"),required(p,prefix+"name"),paths));
            }
            current=new Snapshot(likes,lists); loaded=true; return current;
        } catch (RuntimeException e) { throw new IOException("Damaged library state; original file was kept.",e); }
    }
    private static int count(Properties p,String key) {
        int value=Integer.parseInt(required(p,key));
        if(value<0 || value>1_000_000) throw new IllegalArgumentException("Invalid count");return value;
    }
    private static String required(Properties p,String key) { return Objects.requireNonNull(p.getProperty(key),key); }
    private Snapshot persist(Snapshot next) throws IOException {
        if(!loaded) throw new IOException("Library state was not loaded; writing is disabled.");
        Properties p=new Properties();p.setProperty("version","1");
        p.setProperty("liked.count",""+next.liked.size());int i=0;
        for(String path:new TreeSet<>(next.liked)) p.setProperty("liked."+i++,path);
        p.setProperty("playlist.count",""+next.playlists.size());
        for(i=0;i<next.playlists.size();i++) {
            Playlist list=next.playlists.get(i);String prefix="playlist."+i+".";
            p.setProperty(prefix+"id",list.id);p.setProperty(prefix+"name",list.name);
            p.setProperty(prefix+"count",""+list.paths.size());
            for(int j=0;j<list.paths.size();j++)p.setProperty(prefix+"track."+j,list.paths.get(j));
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(file.toAbsolutePath().getParent(),"library-",".tmp");
        try {
            try(Writer writer=Files.newBufferedWriter(temp)) {p.store(writer,"PixelPlayer desktop library");}
            try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e) {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
        current=next;return next;
    }
    public synchronized Snapshot toggleLike(String path) throws IOException {
        Set<String> likes=new HashSet<>(current.liked);if(!likes.remove(path))likes.add(path);
        return persist(new Snapshot(likes,current.playlists));
    }
    private String validName(String name,String except) {
        String clean=name.strip();if(clean.isEmpty() || clean.length()>120)throw new IllegalArgumentException("Use a name between 1 and 120 characters.");
        for(Playlist list:current.playlists)if(!list.id.equals(except) && list.name.equalsIgnoreCase(clean))throw new IllegalArgumentException("A playlist with this name already exists.");
        return clean;
    }
    public synchronized Snapshot create(String name) throws IOException {
        List<Playlist> lists=new ArrayList<>(current.playlists);lists.add(new Playlist(UUID.randomUUID().toString(),validName(name,""),List.of()));
        return persist(new Snapshot(current.liked,lists));
    }
    public synchronized Snapshot createFromTracks(String name,Collection<String> paths) throws IOException {
        List<Playlist> lists=new ArrayList<>(current.playlists);
        lists.add(new Playlist(UUID.randomUUID().toString(),validName(name,""),paths));
        return persist(new Snapshot(current.liked,lists));
    }
    public synchronized Snapshot rename(String id,String name) throws IOException {
        String clean=validName(name,id);return update(id,list->new Playlist(id,clean,list.paths));
    }
    public synchronized Snapshot delete(String id) throws IOException {
        List<Playlist> lists=new ArrayList<>(current.playlists);lists.removeIf(p->p.id.equals(id));
        return persist(new Snapshot(current.liked,lists));
    }
    public synchronized Snapshot add(String id,String path) throws IOException {
        return update(id,list->{List<String> paths=new ArrayList<>(list.paths);paths.add(path);return new Playlist(id,list.name,paths);});
    }
    public synchronized Snapshot remove(String id,String path) throws IOException {
        return update(id,list->{List<String> paths=new ArrayList<>(list.paths);paths.remove(path);return new Playlist(id,list.name,paths);});
    }
    private Snapshot update(String id,java.util.function.Function<Playlist,Playlist> change) throws IOException {
        List<Playlist> lists=new ArrayList<>();boolean found=false;
        for(Playlist list:current.playlists) {if(list.id.equals(id)){lists.add(change.apply(list));found=true;}else lists.add(list);}
        if(!found)throw new IllegalArgumentException("Playlist no longer exists.");
        return persist(new Snapshot(current.liked,lists));
    }
}

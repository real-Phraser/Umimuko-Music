package com.lostf1sh.pixelplayeross.desktop.library;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Home data is separate from the existing likes/playlists file. All writes are atomic. */
public final class HomeStore {
    public record Pin(String type,String id) { }
    public record Session(List<String> paths,int index,long time) {
        public Session {paths=List.copyOf(paths);}
    }
    public record Snapshot(Map<String,Long> added,List<String> recent,List<Pin> pins,Session session) { }
    private final Path directory;
    private Map<String,Long> added=Map.of();
    private List<String> recent=List.of();
    private List<Pin> pins=List.of();
    private Session session=new Session(List.of(),-1,0);
    private String queueId="";
    private boolean loaded;
    public HomeStore(Path directory){this.directory=directory;}
    public static HomeStore defaultStore(){
        return new HomeStore(PlatformPaths.getDataDirectory().resolve("home-v1"));
    }
    public synchronized Snapshot snapshot(){return new Snapshot(added,recent,pins,session);}
    private Properties read(String name) throws IOException {
        Properties p=new Properties();Path file=directory.resolve(name);
        if(Files.exists(file))try(Reader reader=Files.newBufferedReader(file)){p.load(reader);}
        return p;
    }
    private void write(String name,Properties p) throws IOException {
        Files.createDirectories(directory);Path temp=Files.createTempFile(directory,"home-",".tmp");
        try {
            try(Writer writer=Files.newBufferedWriter(temp)){p.store(writer,"Umimuko Music home");}
            try{Files.move(temp,directory.resolve(name),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,directory.resolve(name),StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static int count(Properties p,String key,int max){int n=Integer.parseInt(p.getProperty(key,"0"));if(n<0||n>max)throw new IllegalArgumentException("Invalid count");return n;}
    private static List<String> paths(Properties p,int max){
        List<String> out=new ArrayList<>();for(int i=0,n=count(p,"count",max);i<n;i++)out.add(Objects.requireNonNull(p.getProperty("path."+i)));return List.copyOf(out);
    }
    private static Properties paths(List<String> values){Properties p=new Properties();p.setProperty("count",""+values.size());for(int i=0;i<values.size();i++)p.setProperty("path."+i,values.get(i));return p;}
    public synchronized Snapshot load() throws IOException {
        try {
            Properties library=read("added.properties");Map<String,Long> times=new HashMap<>();
            for(String key:library.stringPropertyNames())times.put(key,Long.parseLong(library.getProperty(key)));
            Properties home=read("home.properties");List<String> recents=paths(home,100);
            List<Pin> savedPins=new ArrayList<>();
            for(int i=0,n=count(home,"pin.count",200);i<n;i++){
                String type=home.getProperty("pin."+i+".type"),id=home.getProperty("pin."+i+".id");
                if(("album".equals(type)||"playlist".equals(type))&&id!=null)savedPins.add(new Pin(type,id));
            }
            Properties queue=read("queue.properties"),position=read("position.properties");
            String id=queue.getProperty("id","");List<String> tracks=paths(queue,100_000);
            int index=Integer.parseInt(position.getProperty("index","-1"));
            long time=Long.parseLong(position.getProperty("time","0"));
            Session resume=id.equals(position.getProperty("queueId")) && index>=0 && index<tracks.size()
                ?new Session(tracks,index,Math.max(0,time)):new Session(List.of(),-1,0);
            added=Map.copyOf(times);recent=recents;pins=List.copyOf(savedPins);session=resume;queueId=id;
            loaded=true;return snapshot();
        }catch(RuntimeException e){throw new IOException("Could not read home data; original files were kept.",e);}
    }
    private void ready() throws IOException {if(!loaded)throw new IOException("Home data has not been loaded.");}
    public synchronized Snapshot registerLibrary(Collection<String> paths,long now) throws IOException {
        ready();Map<String,Long> next=new HashMap<>(added);boolean changed=false;
        for(String path:paths)if(!next.containsKey(path)){next.put(path,now);changed=true;}
        if(changed){Properties p=new Properties();next.forEach((key,value)->p.setProperty(key,""+value));write("added.properties",p);added=Map.copyOf(next);}
        return snapshot();
    }
    private void saveHome(List<String> nextRecent,List<Pin> nextPins) throws IOException {
        Properties p=paths(nextRecent);p.setProperty("pin.count",""+nextPins.size());
        for(int i=0;i<nextPins.size();i++){p.setProperty("pin."+i+".type",nextPins.get(i).type());p.setProperty("pin."+i+".id",nextPins.get(i).id());}
        write("home.properties",p);recent=List.copyOf(nextRecent);pins=List.copyOf(nextPins);
    }
    public synchronized Snapshot played(String path) throws IOException {
        ready();List<String> next=new ArrayList<>(recent);next.remove(path);next.add(0,path);
        if(next.size()>60)next=next.subList(0,60);saveHome(next,pins);return snapshot();
    }
    public synchronized Snapshot togglePin(String type,String id) throws IOException {
        ready();if(!type.equals("album")&&!type.equals("playlist"))throw new IllegalArgumentException("Invalid pin type");
        Pin pin=new Pin(type,id);List<Pin> next=new ArrayList<>(pins);
        if(!next.remove(pin)){if(next.size()>=200)throw new IllegalArgumentException("Maximum 200 pins");next.add(pin);}
        saveHome(recent,next);return snapshot();
    }
    public synchronized Snapshot movePin(String type,String id,int delta) throws IOException {
        ready();List<Pin> next=new ArrayList<>(pins);int index=next.indexOf(new Pin(type,id));
        if(index>=0){int target=Math.max(0,Math.min(index+delta,next.size()-1));Collections.swap(next,index,target);saveHome(recent,next);}
        return snapshot();
    }
    public synchronized Snapshot saveSession(List<String> tracks,int index,long time) throws IOException {
        ready();if(tracks.size()>100_000)throw new IOException("Queue too large to save.");
        if(!tracks.equals(session.paths())){
            String id=UUID.randomUUID().toString();Properties p=paths(tracks);p.setProperty("id",id);write("queue.properties",p);queueId=id;
        }
        Properties position=new Properties();position.setProperty("queueId",queueId);position.setProperty("index",""+index);position.setProperty("time",""+Math.max(0,time));
        write("position.properties",position);session=new Session(tracks,index,Math.max(0,time));return snapshot();
    }
}

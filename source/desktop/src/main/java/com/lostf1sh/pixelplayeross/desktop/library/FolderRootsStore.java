package com.lostf1sh.pixelplayeross.desktop.library;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Persist only selected folder paths, never modify audio. */
public final class FolderRootsStore {
    private final Path file;
    public FolderRootsStore(Path file){this.file=file;}
    public static FolderRootsStore defaultStore(){
        return new FolderRootsStore(PlatformPaths.getDataDirectory().resolve("folders-v1.properties"));
    }
    public List<String> load() throws IOException {
        if(!Files.exists(file))return List.of();
        Properties p=new Properties();try(Reader r=Files.newBufferedReader(file)){p.load(r);}
        try{
            if(!"1".equals(p.getProperty("version")))throw new IllegalArgumentException();
            int n=Integer.parseInt(p.getProperty("count"));if(n<0||n>10000)throw new IllegalArgumentException();
            ArrayList<String> roots=new ArrayList<>();for(int i=0;i<n;i++)roots.add(Objects.requireNonNull(p.getProperty("folder."+i)));
            return List.copyOf(roots);
        }catch(RuntimeException e){throw new IOException("Saved folders are damaged; original file was kept.",e);}
    }
    public void save(Collection<String> roots)throws IOException {
        Properties p=new Properties();p.setProperty("version","1");p.setProperty("count",Integer.toString(roots.size()));
        int i=0;for(String root:roots)p.setProperty("folder."+i++,root);
        Path dir=file.toAbsolutePath().getParent();Files.createDirectories(dir);
        Path tmp=Files.createTempFile(dir,"folders-",".tmp");
        try{
            try(Writer w=Files.newBufferedWriter(tmp)){p.store(w,"PixelPlayer music folders");}
            try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
}

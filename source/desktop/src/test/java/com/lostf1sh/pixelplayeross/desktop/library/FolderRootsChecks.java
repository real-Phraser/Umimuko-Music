package com.lostf1sh.pixelplayeross.desktop.library;
import java.nio.file.*;
import java.util.*;
public final class FolderRootsChecks {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] a)throws Exception{runChecks();System.out.println("PASS: multiple Unicode Windows folder paths persist, remove/reload, corrupted file preserved");}
    public static void runChecks()throws Exception{
        Path root=Files.createTempDirectory("pixel-roots");
        try {
            Path file=root.resolve("folders.properties");
            FolderRootsStore store=new FolderRootsStore(file);
            check(store.load().isEmpty(),"initial empty");
            List<String> roots=List.of("C:\\Music\\فارسی","D:\\Albums\\One", "D:\\Albums\\One\\Disc 2");
            store.save(roots);check(new FolderRootsStore(file).load().equals(roots),"round trip order and unicode");
            store.save(roots.subList(1,3));check(store.load().equals(roots.subList(1,3)),"remove preserves other roots");
            Files.writeString(file,"version=wrong");
            boolean failed=false;try{store.load();}catch(java.io.IOException ex){failed=true;}
            check(failed && Files.readString(file).equals("version=wrong"),"corrupt file unchanged");
        }finally{try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}

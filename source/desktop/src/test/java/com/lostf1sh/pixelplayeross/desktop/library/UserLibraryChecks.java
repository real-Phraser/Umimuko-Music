package com.lostf1sh.pixelplayeross.desktop.library;
import java.nio.file.*;
import java.util.*;

public final class UserLibraryChecks {
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {runChecks();System.out.println("PASS: artist splitting/deduplication, Unicode persistence, likes, playlist CRUD, track deduplication, failed-write preservation, corrupt-state protection");}
    public static void runChecks()throws Exception {
        check(ArtistNames.split(" Freddie Gibbs/The Alchemist ، Test, test؛Other; ").equals(
            List.of("Freddie Gibbs","The Alchemist","Test","Other")),"artist separators and case deduplication");
        check(ArtistNames.split("  Earth & Fire ").equals(List.of("Earth & Fire")),"ampersand remains part of name");
        check(ArtistNames.split("/// ، ؛ ").isEmpty(),"empty artists");
        Path root=Files.createTempDirectory("pixel-library-tests");
        try {
            Path file=root.resolve("library.properties");
            var store=new UserLibraryStore(file);check(store.load().playlists.isEmpty(),"empty state");
            check(!store.load().isLiked(null), "startup with no selected track must not crash");
            check(!store.load().isLiked("missing.flac"), "unliked selection");
            store.toggleLike("C:\\Music\\آهنگ.flac");
            var s=store.create("علاقه‌ها = #1");String id=s.playlists.get(0).id;
            store.add(id,"C:\\Music\\آهنگ.flac");store.add(id,"C:\\Music\\آهنگ.flac");
            var loaded=new UserLibraryStore(file).load();
            check(loaded.isLiked("C:\\Music\\آهنگ.flac"),"Unicode liked path survives restart");
            check(!loaded.isLiked(null), "loaded immutable likes also accept no selection");
            check(loaded.playlists.get(0).name.equals("علاقه‌ها = #1"),"Unicode name survives restart");
            check(loaded.playlists.get(0).paths.size()==1,"duplicate add prevented");
            store.rename(id,"Renamed");store.create("Second");
            boolean duplicate=false;try{store.rename(id,"second");}catch(IllegalArgumentException e){duplicate=true;}
            check(duplicate,"duplicate names rejected");
            check(store.remove(id,"C:\\Music\\آهنگ.flac").playlists.get(0).paths.isEmpty(),"remove track");
            check(store.toggleLike("C:\\Music\\آهنگ.flac").liked.isEmpty(),"unlike");
            check(store.delete(id).playlists.size()==1,"delete playlist");
            Path broken=root.resolve("broken");Files.writeString(broken,"version=garbage");
            var bad=new UserLibraryStore(broken);boolean failed=false;
            try{bad.load();}catch(java.io.IOException e){failed=true;}check(failed,"corrupt state rejected");
            failed=false;try{bad.create("Cannot write");}catch(java.io.IOException e){failed=true;}
            check(failed && Files.readString(broken).equals("version=garbage"),"corrupt file preserved");
            Path blocking=root.resolve("file-as-directory");Files.writeString(blocking,"keep");
            var cannotWrite=new UserLibraryStore(blocking.resolve("state"));cannotWrite.load();
            failed=false;try{cannotWrite.toggleLike("track");}catch(java.io.IOException e){failed=true;}
            check(failed && cannotWrite.load().liked.isEmpty(),"failed write leaves memory unchanged");
        }finally{try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}

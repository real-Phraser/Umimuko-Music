package com.lostf1sh.pixelplayeross.desktop.metadata;

import java.nio.file.*;
import java.util.*;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;

/** Read-only tag extraction; damaged/unsupported tags never remove a track from the library. */
public final class TrackMetadataReader {
    private final ArtworkStore store;
    private final Map<Path, ArtworkStore.Cover> folderCovers = new HashMap<>();
    public TrackMetadataReader(ArtworkStore store) {
        this.store = store;
        java.util.logging.Logger.getLogger("org.jaudiotagger").setLevel(java.util.logging.Level.WARNING);
    }
    public static final class Metadata {
        public String title = "", artist = "", album = "", albumArtist = "";
        public int discNumber = 0, trackNumber = 0;
        public long durationMs = 0;
        public ArtworkStore.Cover cover;
    }
    public Metadata read(Path path) {
        Metadata result = new Metadata();
        try {
            var audio = AudioFileIO.read(path.toFile());
            if (audio.getAudioHeader() != null) result.durationMs = Math.max(0, audio.getAudioHeader().getTrackLength()) * 1000L;
            var tag = audio.getTag();
            if (tag != null) {
                result.title = tag.getFirst(FieldKey.TITLE).trim();
                result.artist = tag.getFirst(FieldKey.ARTIST).trim();
                result.album = tag.getFirst(FieldKey.ALBUM).trim();
                result.albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST).trim();
                result.trackNumber = number(tag.getFirst(FieldKey.TRACK));
                result.discNumber = number(tag.getFirst(FieldKey.DISC_NO));
                try {
                    var artwork = tag.getFirstArtwork();
                    if (artwork != null) result.cover = store.store(artwork.getBinaryData());
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        if (result.cover == null) result.cover = folderCover(path.getParent());
        return result;
    }
    private static int number(String raw) {
        if (raw == null) return 0;
        try { return Integer.parseInt(raw.trim().split("/", 2)[0]); }
        catch (Exception ignored) { return 0; }
    }
    private ArtworkStore.Cover folderCover(Path folder) {
        if (folder == null) return null;
        if (folderCovers.containsKey(folder)) return folderCovers.get(folder);
        ArtworkStore.Cover found = null;
        // Exact priority, with case-insensitive matching. Never searches parent folders or the web.
        List<String> preferred = List.of("cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png", "album.jpg", "album.png");
        try (var paths = Files.list(folder)) {
            Map<String,Path> files = new HashMap<>();
            paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                .forEach(p -> files.putIfAbsent(p.getFileName().toString().toLowerCase(Locale.ROOT),p));
            for (String name : preferred) {
                Path file = files.get(name);
                if (file != null && Files.size(file) <= ArtworkStore.MAX_BYTES) {
                    try { found = store.store(Files.readAllBytes(file)); } catch (Exception ignored) { }
                    if (found != null) break;
                }
            }
        } catch (Exception ignored) { }
        folderCovers.put(folder, found);
        return found;
    }
}

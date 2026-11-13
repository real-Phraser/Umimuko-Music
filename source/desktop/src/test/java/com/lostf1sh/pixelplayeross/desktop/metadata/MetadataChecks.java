package com.lostf1sh.pixelplayeross.desktop.metadata;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class MetadataChecks {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        runChecks(Path.of(args[0]));
        System.out.println("PASS: embedded FLAC tags/cover, folder fallback, cache reuse, invalid tags/art, dominant color, bounded thumbnail");
    }
    public static void runChecks(Path fixtures) throws Exception {
        Path root = Files.createTempDirectory("pixel-metadata-test");
        try {
            Path cache = root.resolve("cache");
            ArtworkStore store = new ArtworkStore(cache);
            var reader = new TrackMetadataReader(store);
            var tagged = reader.read(fixtures.resolve("embedded.flac"));
            check(tagged.title.equals("آهنگ آزمایشی"), "Unicode title");
            check(tagged.artist.equals("Test Artist") && tagged.album.equals("Test Album"), "artist/album");
            check(tagged.durationMs == 2000, "duration");
            check(tagged.cover != null, "embedded cover");
            BufferedImage thumbnail = ImageIO.read(Path.of(tagged.cover.path).toFile());
            check(Math.max(thumbnail.getWidth(), thumbnail.getHeight()) <= 512, "bounded thumbnail");
            check(((tagged.cover.accent >> 16)&255) > 200, "red cover seed");
            var repeated = reader.read(fixtures.resolve("embedded.flac"));
            check(repeated.cover.path.equals(tagged.cover.path), "reuse identical artwork");
            try (var files = Files.list(cache)) { check(files.count()==1, "one content-addressed image"); }
            BufferedImage green = new BufferedImage(30,30,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<30;y++)for(int x=0;x<30;x++) green.setRGB(x,y,0xFF1BA979);
            ImageIO.write(green,"png",root.resolve("COVER.PNG").toFile());
            Path plain = root.resolve("plain.flac");
            Files.copy(fixtures.resolve("plain.flac"),plain);
            var fallback = reader.read(plain);
            check(fallback.cover != null && ((fallback.cover.accent>>8)&255)>150, "case-insensitive folder art");
            Files.copy(fixtures.resolve("embedded.flac"),root.resolve("both.flac"));
            check(reader.read(root.resolve("both.flac")).cover.path.equals(tagged.cover.path), "embedded beats folder art");
            check(store.store(new byte[]{1,2,3})==null,"invalid image ignored");
            Path badDirectory=Files.createDirectory(root.resolve("bad"));
            Path bad=badDirectory.resolve("broken.mp3");Files.write(bad,new byte[]{1,2,3});
            check(reader.read(bad).cover==null,"damaged tags don't throw or invent artwork");
            BufferedImage transparent = new BufferedImage(5,5,BufferedImage.TYPE_INT_ARGB);
            check(ArtworkStore.dominantColor(transparent)==0xFF9CCAFF,"transparent fallback");
        } finally {
            try(var paths=Files.walk(root)) {
                for(Path path: paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}

package com.lostf1sh.pixelplayeross.desktop.metadata;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths;

/** Local, content-addressed 512px cover thumbnails. Source music is never modified. */
public final class ArtworkStore {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private final Path cache;
    private final Map<String, Cover> memory = new HashMap<>();
    public static final class Cover {
        public final String path;
        public final int accent;
        Cover(String path, int accent) { this.path = path; this.accent = accent; }
    }
    public ArtworkStore(Path cache) { this.cache = cache; }
    public static ArtworkStore defaultStore() {
        return new ArtworkStore(PlatformPaths.getCacheDirectory());
    }
    public Cover store(byte[] data) throws Exception {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) return null;
        String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        if (memory.containsKey(key)) return memory.get(key);
        Files.createDirectories(cache);
        Path target = cache.resolve(key + ".png");
        BufferedImage image = null;
        if (Files.isRegularFile(target)) {
            try { image = ImageIO.read(target.toFile()); } catch (IOException ignored) { }
        }
        if (image == null) {
            image = thumbnail(data);
            if (image == null) return null;
            Path temporary = Files.createTempFile(cache, "cover-", ".png");
            try {
                if (!ImageIO.write(image, "png", temporary.toFile())) return null;
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        }
        Cover cover = new Cover(target.toAbsolutePath().toString(), dominantColor(image));
        memory.put(key, cover);
        return cover;
    }
    private static BufferedImage thumbnail(byte[] data) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (input == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long)width * height > 100_000_000L) return null;
                var params = reader.getDefaultReadParam();
                int sample = Math.max(1, (Math.max(width, height) + 511) / 512);
                params.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage decoded = reader.read(0, params);
                int w = decoded.getWidth(), h = decoded.getHeight();
                BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = result.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(decoded, 0, 0, w, h, null);
                } finally { graphics.dispose(); }
                return result;
            } finally { reader.dispose(); }
        }
    }
    public static int dominantColor(BufferedImage image) {
        // Group by hue, so a large black area cannot outvote a small colored subject.
        double[] scores = new double[36];
        double[] red = new double[36], green = new double[36], blue = new double[36];
        double[] weights = new double[36];
        double gray = 0;
        int grayCount = 0;
        for (int y = 0; y < image.getHeight(); y += Math.max(1, image.getHeight()/64)) {
            for (int x = 0; x < image.getWidth(); x += Math.max(1, image.getWidth()/64)) {
                int pixel = image.getRGB(x,y);
                if ((pixel >>> 24) < 128) continue;
                int r = (pixel >> 16)&255, g = (pixel >> 8)&255, b = pixel&255;
                float[] hsv = java.awt.Color.RGBtoHSB(r,g,b,null);
                gray += (r+g+b)/3.0; grayCount++;
                if (hsv[1] < .24f || hsv[2] < .12f) continue;
                int bin = Math.min(35,(int)(hsv[0]*36));
                double weight = hsv[1] * Math.sqrt(hsv[2]);
                scores[bin] += weight;
                red[bin] += r*weight; green[bin] += g*weight; blue[bin] += b*weight;
                weights[bin] += weight;
            }
        }
        int best = 0;
        for (int i=1;i<scores.length;i++) if (scores[i]>scores[best]) best=i;
        if (weights[best]==0) {
            if (grayCount == 0) return 0xFF9CCAFF;
            int shade = (int)Math.max(55,Math.min(120,gray/grayCount));
            return 0xFF000000 | shade<<16 | shade<<8 | shade;
        }
        int r=(int)(red[best]/weights[best]);
        int g=(int)(green[best]/weights[best]);
        int b=(int)(blue[best]/weights[best]);
        float[] hsv=java.awt.Color.RGBtoHSB(r,g,b,null);
        // A nearly black but colored cover still needs a visible accent.
        return java.awt.Color.HSBtoRGB(hsv[0],Math.max(.35f,hsv[1]),Math.max(.42f,hsv[2]));
    }
}

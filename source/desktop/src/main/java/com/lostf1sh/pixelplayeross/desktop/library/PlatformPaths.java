package com.lostf1sh.pixelplayeross.desktop.library;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;

/**
 * Idiomatic application storage and cache paths across Windows, macOS, and Linux,
 * with automatic backward-compatibility migration from legacy paths.
 */
public final class PlatformPaths {
    private PlatformPaths() {}

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean IS_WINDOWS = OS.contains("win");
    private static final boolean IS_MAC = OS.contains("mac");

    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    public static boolean isMac() {
        return IS_MAC;
    }

    public static boolean isLinux() {
        return !IS_WINDOWS && !IS_MAC;
    }

    public static Path getDataDirectory() {
        String home = System.getProperty("user.home", ".");
        if (IS_WINDOWS) {
            String appdata = System.getenv("LOCALAPPDATA");
            Path base = (appdata == null || appdata.isBlank())
                ? Path.of(home, ".local", "share")
                : Path.of(appdata);
            return base.resolve("PixelPlayerDesktop");
        }

        if (IS_MAC) {
            Path macData = Path.of(home, "Library", "Application Support", "Umimuko Music");
            migrateLegacyIfPresent(Path.of(home, ".local", "share", "PixelPlayerDesktop"), macData);
            return macData;
        }

        // Linux / Unix (XDG Base Directory)
        String xdgData = System.getenv("XDG_DATA_HOME");
        Path linuxData = (xdgData == null || xdgData.isBlank())
            ? Path.of(home, ".local", "share", "umimuko-music")
            : Path.of(xdgData, "umimuko-music");
        migrateLegacyIfPresent(Path.of(home, ".local", "share", "PixelPlayerDesktop"), linuxData);
        return linuxData;
    }

    public static Path getCacheDirectory() {
        String home = System.getProperty("user.home", ".");
        if (IS_WINDOWS) {
            String local = System.getenv("LOCALAPPDATA");
            Path root = (local == null || local.isBlank())
                ? Path.of(home, ".cache")
                : Path.of(local);
            return root.resolve("PixelPlayerDesktop").resolve("covers-v1");
        }

        if (IS_MAC) {
            Path macCache = Path.of(home, "Library", "Caches", "Umimuko Music", "covers-v1");
            migrateLegacyIfPresent(Path.of(home, ".cache", "PixelPlayerDesktop", "covers-v1"), macCache);
            return macCache;
        }

        // Linux / Unix (XDG Cache)
        String xdgCache = System.getenv("XDG_CACHE_HOME");
        Path linuxCache = (xdgCache == null || xdgCache.isBlank())
            ? Path.of(home, ".cache", "umimuko-music", "covers-v1")
            : Path.of(xdgCache, "umimuko-music", "covers-v1");
        migrateLegacyIfPresent(Path.of(home, ".cache", "PixelPlayerDesktop", "covers-v1"), linuxCache);
        return linuxCache;
    }

    private static void migrateLegacyIfPresent(Path legacyDir, Path newDir) {
        if (!Files.exists(legacyDir) || Files.exists(newDir)) {
            return;
        }
        try {
            Files.createDirectories(newDir.getParent());
            copyRecursively(legacyDir, newDir);
        } catch (Exception ignored) {
            // Non-critical; fallback to empty/fresh directory if migration cannot complete.
        }
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            Files.createDirectories(target);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path entry : stream) {
                    copyRecursively(entry, target.resolve(entry.getFileName()));
                }
            }
        } else if (Files.isRegularFile(source)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

package com.lostf1sh.pixelplayeross.desktop.library;
import java.text.Normalizer;
import java.util.*;

public final class ArtistNames {
    private static final Set<String> SINGLE_ARTISTS = Set.of("tyler, the creator", "earth, wind & fire", "earth & fire", "panic! at the disco");
    public static List<String> split(String value) {
        if (value != null && SINGLE_ARTISTS.contains(key(value))) return List.of(value.strip());
        Map<String,String> names = new LinkedHashMap<>();
        if (value != null) for (String part : value.split("[/,،;؛&＆]+")) {
            String name = part.strip().replaceAll("\\s+", " ");
            if (!name.isEmpty()) names.putIfAbsent(key(name), name);
        }
        return List.copyOf(names.values());
    }
    public static String key(String name) {
        return Normalizer.normalize(name.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}

package com.lostf1sh.pixelplayeross.desktop.lyrics

import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences
import kotlin.math.abs

/**
 * Fetches, caches, and persists configuration for Spicy Lyrics (`https://api.spicylyrics.org`),
 * with automatic fallback to local `.ttml`/`.lrc` sidecar files and embedded track lyrics.
 */
object SpicyLyricsService {

    private const val PREFS_NODE = "com.lostf1sh.pixelplayeross.desktop.spicylyrics"
    private const val KEY_API_KEY = "apiKey"
    private const val KEY_SHOW_ROMANIZED = "showRomanized"
    private const val USER_AGENT = "UmimukoMusic/0.25.0 ( https://github.com/real-Phraser )"
    private const val CACHE_SUBDIR = "spicy-lyrics"

    private val prefs: Preferences by lazy { Preferences.userRoot().node(PREFS_NODE) }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(6))
        .build()

    private val memoryCache = ConcurrentHashMap<String, SpicyLyrics>()

    var apiKey: String
        get() = prefs.get(KEY_API_KEY, "")
        set(value) {
            prefs.put(KEY_API_KEY, value.trim())
        }

    var showRomanized: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ROMANIZED, false)
        set(value) {
            prefs.putBoolean(KEY_SHOW_ROMANIZED, value)
        }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    /** Clears both the in-memory and on-disk Spicy Lyrics caches. */
    fun clearCache() {
        memoryCache.clear()
        try {
            val cacheDir = cacheDirectory()
            if (Files.isDirectory(cacheDir)) {
                Files.walk(cacheDir).use { stream ->
                    stream.sorted(Comparator.reverseOrder())
                        .forEach { path -> Files.deleteIfExists(path) }
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Resolves lyrics for [song]:
     * 1. In-memory cache
     * 2. On-disk cache
     * 3. Spicy Lyrics API (if [hasApiKey], resolving trackId via iTunes Search API)
     * 4. Local `.ttml` or `.lrc` sidecar file
     * 5. Embedded `song.lyrics` metadata
     */
    suspend fun getLyrics(song: DesktopSong): SpicyLyrics {
        val cacheKey = song.id
        memoryCache[cacheKey]?.let { return it }

        val diskCached = withContext(Dispatchers.IO) { readFromDiskCache(song) }
        if (diskCached != null && diskCached.lines.isNotEmpty()) {
            memoryCache[cacheKey] = diskCached
            return diskCached
        }

        val currentKey = apiKey
        if (currentKey.isNotBlank()) {
            val online = withContext(Dispatchers.IO) {
                fetchFromSpicyApi(song.artist, song.title, song.durationMs, currentKey)
            }
            if (online != null && online.first.lines.isNotEmpty()) {
                withContext(Dispatchers.IO) { writeRawJsonToDiskCache(song, online.second) }
                memoryCache[cacheKey] = online.first
                return online.first
            }
        }

        val sidecar = withContext(Dispatchers.IO) { readLocalSidecar(song) }
        if (sidecar != null && sidecar.lines.isNotEmpty()) {
            memoryCache[cacheKey] = sidecar
            return sidecar
        }

        if (song.lyrics.isNotBlank()) {
            val embedded = SpicyLyricsParser.parse(song.lyrics)
            if (embedded.lines.isNotEmpty()) {
                memoryCache[cacheKey] = embedded
                return embedded
            }
        }

        return SpicyLyrics.EMPTY
    }

    private fun fetchFromSpicyApi(
        artist: String,
        title: String,
        durationMs: Long,
        bearerToken: String
    ): Pair<SpicyLyrics, String>? {
        val trackId = resolveItunesTrackId(artist, title, durationMs) ?: return null
        val endpoint = "https://api.spicylyrics.org/v1/lyrics/$trackId"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $bearerToken")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                val body = response.body()
                val parsed = SpicyLyricsParser.parseSpicyJson(body)
                if (parsed.lines.isNotEmpty()) parsed to body else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun resolveItunesTrackId(artist: String, title: String, durationMs: Long): Long? {
        val query = listOf(artist.trim(), title.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        if (query.isBlank()) return null

        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val url = "https://itunes.apple.com/search?term=$encoded&entity=song&limit=5"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                pickBestItunesTrackId(response.body(), artist, title, durationMs)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun pickBestItunesTrackId(
        itunesJson: String,
        artist: String,
        title: String,
        durationMs: Long
    ): Long? {
        val root = SimpleJson.parse(itunesJson) as? Map<*, *> ?: return null
        val results = root["results"] as? List<*> ?: return null
        if (results.isEmpty()) return null

        val cleanArtist = artist.trim().lowercase()
        val cleanTitle = title.trim().lowercase()

        var bestId: Long? = null
        var bestScore = Int.MIN_VALUE

        for (item in results) {
            val map = item as? Map<*, *> ?: continue
            val trackId = (map["trackId"] as? Number)?.toLong()?.takeIf { it > 0L } ?: continue
            val trackName = (map["trackName"] as? String).orEmpty().trim().lowercase()
            val artistName = (map["artistName"] as? String).orEmpty().trim().lowercase()
            val trackTimeMs = (map["trackTimeMillis"] as? Number)?.toLong() ?: 0L

            var score = 0
            if (cleanTitle.isNotEmpty()) {
                score += when {
                    trackName == cleanTitle -> 50
                    trackName.contains(cleanTitle) || cleanTitle.contains(trackName) -> 25
                    else -> -10
                }
            }
            if (cleanArtist.isNotEmpty()) {
                score += when {
                    artistName == cleanArtist -> 40
                    artistName.contains(cleanArtist) || cleanArtist.contains(artistName) -> 20
                    else -> -10
                }
            }
            if (durationMs > 0L && trackTimeMs > 0L) {
                val diff = abs(durationMs - trackTimeMs)
                score += when {
                    diff <= 2_000L -> 30
                    diff <= 5_000L -> 15
                    diff <= 10_000L -> 0
                    else -> -25
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestId = trackId
            }
        }

        return bestId
    }

    internal fun readFromDiskCache(song: DesktopSong): SpicyLyrics? {
        return try {
            val file = cacheDirectory().resolve(getSafeFileName(song) + ".json")
            if (Files.isRegularFile(file)) {
                SpicyLyricsParser.parseSpicyJson(Files.readString(file))
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun cacheToDisk(song: DesktopSong, lyrics: SpicyLyrics) {
        val json = serializeToSpicyJson(lyrics)
        writeRawJsonToDiskCache(song, json)
    }

    private fun writeRawJsonToDiskCache(song: DesktopSong, rawJson: String) {
        try {
            val cacheDir = cacheDirectory()
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir)
            }
            val file = cacheDir.resolve(getSafeFileName(song) + ".json")
            Files.writeString(file, rawJson)
        } catch (_: Exception) {
        }
    }

    private fun readLocalSidecar(song: DesktopSong): SpicyLyrics? {
        return try {
            val audioFile = File(song.path)
            val parent = audioFile.parentFile ?: return null
            val baseName = audioFile.nameWithoutExtension

            val candidates = listOf(
                File(parent, "$baseName.ttml"),
                File(parent, "lyrics/$baseName.ttml"),
                File(parent, "$baseName.lrc"),
                File(parent, "lyrics/$baseName.lrc")
            )

            for (file in candidates) {
                if (file.isFile && file.canRead()) {
                    val parsed = SpicyLyricsParser.parse(file.readText())
                    if (parsed.lines.isNotEmpty()) return parsed
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheDirectory(): Path = PlatformPaths.getCacheDirectory().resolve(CACHE_SUBDIR)

    private fun getSafeFileName(song: DesktopSong): String {
        val raw = "${song.artist}_${song.title}".ifBlank { song.id }
        return raw.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private fun serializeToSpicyJson(lyrics: SpicyLyrics): String {
        val typeStr = when (lyrics.type) {
            SpicyLyricsType.SYLLABLE -> "Syllable"
            SpicyLyricsType.LINE -> "Line"
            SpicyLyricsType.STATIC -> "Static"
        }

        fun serializeLine(line: SpicyLine): Map<String, Any?> = linkedMapOf(
            "StartTime" to line.startTimeMs / 1000.0,
            "EndTime" to line.endTimeMs / 1000.0,
            "Text" to line.text,
            "RomanizedText" to line.romanizedText,
            "OppositeAligned" to line.oppositeAligned,
            "Syllables" to line.syllables.map { syl ->
                linkedMapOf(
                    "Text" to syl.text,
                    "StartTime" to syl.startTimeMs / 1000.0,
                    "EndTime" to syl.endTimeMs / 1000.0,
                    "IsPartOfWord" to syl.isPartOfWord,
                    "RomanizedText" to syl.romanizedText
                )
            },
            "Background" to line.backgroundVocals.map { serializeLine(it) }
        )

        val bodyMap = linkedMapOf<String, Any?>(
            "Type" to typeStr,
            "StartTime" to lyrics.startTimeMs / 1000.0,
            "EndTime" to lyrics.endTimeMs / 1000.0,
            "SongWriters" to lyrics.songWriters,
            "Content" to lyrics.lines.map { line ->
                linkedMapOf(
                    "Type" to "Vocal",
                    "OppositeAligned" to line.oppositeAligned,
                    "Lead" to linkedMapOf(
                        "StartTime" to line.startTimeMs / 1000.0,
                        "EndTime" to line.endTimeMs / 1000.0,
                        "Text" to line.text,
                        "RomanizedText" to line.romanizedText,
                        "Syllables" to line.syllables.map { syl ->
                            linkedMapOf(
                                "Text" to syl.text,
                                "StartTime" to syl.startTimeMs / 1000.0,
                                "EndTime" to syl.endTimeMs / 1000.0,
                                "IsPartOfWord" to syl.isPartOfWord,
                                "RomanizedText" to syl.romanizedText
                            )
                        }
                    ),
                    "Background" to line.backgroundVocals.map { serializeLine(it) }
                )
            }
        )

        if (!lyrics.uploaderName.isNullOrBlank()) {
            bodyMap["UploadAttribution"] = mapOf(
                "Uploader" to mapOf("username" to lyrics.uploaderName)
            )
        }

        val root = linkedMapOf<String, Any?>(
            "source" to lyrics.source,
            "Body" to bodyMap
        )
        return SimpleJson.stringify(root)
    }
}

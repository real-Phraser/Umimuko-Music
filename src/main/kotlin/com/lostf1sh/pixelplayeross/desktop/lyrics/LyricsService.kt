package com.lostf1sh.pixelplayeross.desktop.lyrics

import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
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

object LyricsService {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(6))
        .build()

    private const val USER_AGENT = "UmimukoMusic/0.24.3 ( https://github.com/real-Phraser )"
    private val memoryCache = ConcurrentHashMap<String, LrcLyrics>()

    suspend fun getLyrics(song: DesktopSong): LrcLyrics {
        val cacheKey = song.id
        memoryCache[cacheKey]?.let { return it }

        // 1. Online-First lookup: Query LRCLIB.net
        val onlineLyrics = fetchFromLrclib(song.artist, song.title, song.album, song.durationMs)
        if (onlineLyrics != null && onlineLyrics.lines.isNotEmpty()) {
            cacheToDisk(song, onlineLyrics)
            memoryCache[cacheKey] = onlineLyrics
            return onlineLyrics
        }

        // 2. Fallback to Disk Cache
        val cached = readFromDiskCache(song)
        if (cached != null && cached.lines.isNotEmpty()) {
            memoryCache[cacheKey] = cached
            return cached
        }

        // 3. Fallback to Local Sidecar .lrc File
        val sidecar = readLocalSidecar(song)
        if (sidecar != null && sidecar.lines.isNotEmpty()) {
            memoryCache[cacheKey] = sidecar
            return sidecar
        }

        // 4. Fallback to Embedded Metadata Tag
        if (song.lyrics.isNotBlank()) {
            val embedded = LrcParser.parse(song.lyrics)
            if (embedded.lines.isNotEmpty()) {
                memoryCache[cacheKey] = embedded
                return embedded
            }
        }

        return LrcLyrics.EMPTY
    }

    private fun fetchFromLrclib(
        artist: String,
        title: String,
        album: String,
        durationMs: Long
    ): LrcLyrics? {
        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()
        if (cleanArtist.isBlank() || cleanTitle.isBlank()) return null

        val durationSec = durationMs / 1000L
        val cleanAlbum = album.trim()

        val queryParams = mutableListOf(
            "artist_name=" + URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8),
            "track_name=" + URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8)
        )
        if (cleanAlbum.isNotBlank()) {
            queryParams.add("album_name=" + URLEncoder.encode(cleanAlbum, StandardCharsets.UTF_8))
        }
        if (durationSec > 0) {
            queryParams.add("duration=$durationSec")
        }

        val endpoint = "https://lrclib.net/api/get?" + queryParams.joinToString("&")
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                val body = response.body()
                val syncedLrc = extractJsonStringField(body, "syncedLyrics")
                if (!syncedLrc.isNullOrBlank()) {
                    return LrcParser.parse(syncedLrc)
                }
                val plainLyrics = extractJsonStringField(body, "plainLyrics")
                if (!plainLyrics.isNullOrBlank()) {
                    return LrcParser.parse(plainLyrics)
                }
            } else if (response.statusCode() == 404) {
                // Try fuzzy search fallback
                return searchLrclibFuzzy(cleanArtist, cleanTitle)
            }
        } catch (_: Exception) {}

        return null
    }

    private fun searchLrclibFuzzy(artist: String, title: String): LrcLyrics? {
        val query = "$artist $title".trim()
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val endpoint = "https://lrclib.net/api/search?q=$encoded"

        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                val body = response.body()
                val syncedLrc = extractJsonStringField(body, "syncedLyrics")
                if (!syncedLrc.isNullOrBlank()) {
                    return LrcParser.parse(syncedLrc)
                }
                val plainLyrics = extractJsonStringField(body, "plainLyrics")
                if (!plainLyrics.isNullOrBlank()) {
                    return LrcParser.parse(plainLyrics)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    internal fun readFromDiskCache(song: DesktopSong): LrcLyrics? {
        return try {
            val cacheDir = PlatformPaths.getCacheDirectory().resolve("lyrics")
            val safeName = getSafeFileName(song) + ".lrc"
            val file = cacheDir.resolve(safeName)
            if (Files.isRegularFile(file)) {
                val content = Files.readString(file)
                LrcParser.parse(content)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    internal fun cacheToDisk(song: DesktopSong, lyrics: LrcLyrics) {
        try {
            val cacheDir = PlatformPaths.getCacheDirectory().resolve("lyrics")
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir)
            }
            val safeName = getSafeFileName(song) + ".lrc"
            val file = cacheDir.resolve(safeName)
            val content = if (lyrics.isSynced) {
                lyrics.lines.joinToString("\n") { line ->
                    val totalSec = line.timestampMs / 1000L
                    val min = totalSec / 60L
                    val sec = totalSec % 60L
                    val cs = (line.timestampMs % 1000L) / 10L
                    "[%02d:%02d.%02d]%s".format(min, sec, cs, line.text)
                }
            } else {
                lyrics.lines.joinToString("\n") { it.text }
            }
            Files.writeString(file, content)
        } catch (_: Exception) {}
    }

    private fun readLocalSidecar(song: DesktopSong): LrcLyrics? {
        return try {
            val audioFile = File(song.path)
            val parent = audioFile.parentFile ?: return null
            val baseName = audioFile.nameWithoutExtension

            // 1. Same directory: song.lrc
            val directLrc = File(parent, "$baseName.lrc")
            if (directLrc.isFile && directLrc.canRead()) {
                return LrcParser.parse(directLrc.readText())
            }

            // 2. lyrics/ subfolder
            val lyricsSubfolder = File(parent, "lyrics/$baseName.lrc")
            if (lyricsSubfolder.isFile && lyricsSubfolder.canRead()) {
                return LrcParser.parse(lyricsSubfolder.readText())
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getSafeFileName(song: DesktopSong): String {
        val raw = "${song.artist}_${song.title}".ifBlank { song.id }
        return raw.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private fun extractJsonStringField(json: String, fieldName: String): String? {
        val pattern = Regex(""""$fieldName"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val match = pattern.find(json) ?: return null
        val raw = match.groupValues[1]
        return unescapeJson(raw)
    }

    private fun unescapeJson(str: String): String {
        return str.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}

package com.lostf1sh.pixelplayeross.desktop.stats

import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

data class TrackStats(
    val trackId: String,
    val title: String = "",
    val artist: String = "",
    val playCount: Long = 0L,
    val skipCount: Long = 0L,
    val lastPlayedEpochMs: Long = 0L,
    val totalListenDurationMs: Long = 0L
)

data class ArtistStats(
    val artist: String,
    val totalPlayCount: Long,
    val totalListenDurationMs: Long,
    val trackCount: Int
)

class ListeningStatsStore(private val file: Path = PlatformPaths.getDataDirectory().resolve("listening-stats-v1.json")) {

    private val statsMap = ConcurrentHashMap<String, TrackStats>()
    private var loaded = false

    @Synchronized
    fun load(): Map<String, TrackStats> {
        if (loaded) return statsMap.toMap()
        statsMap.clear()
        if (Files.isRegularFile(file)) {
            try {
                val json = Files.readString(file)
                val parsed = parseStatsJson(json)
                statsMap.putAll(parsed)
            } catch (e: Exception) {
                System.err.println("Failed reading listening stats: ${e.message}")
            }
        }
        loaded = true
        return statsMap.toMap()
    }

    fun getStats(trackId: String): TrackStats? {
        load()
        return statsMap[trackId]
    }

    fun getPlayCount(trackId: String): Long {
        return getStats(trackId)?.playCount ?: 0L
    }

    @Synchronized
    fun recordPlay(song: DesktopSong, durationListenedMs: Long) {
        load()
        val current = statsMap[song.id] ?: TrackStats(
            trackId = song.id,
            title = song.title,
            artist = song.artist
        )
        val updated = current.copy(
            title = song.title.ifBlank { current.title },
            artist = song.artist.ifBlank { current.artist },
            playCount = current.playCount + 1,
            lastPlayedEpochMs = System.currentTimeMillis(),
            totalListenDurationMs = current.totalListenDurationMs + durationListenedMs.coerceAtLeast(0)
        )
        statsMap[song.id] = updated
        persist()
    }

    @Synchronized
    fun recordSkip(song: DesktopSong) {
        load()
        val current = statsMap[song.id] ?: TrackStats(
            trackId = song.id,
            title = song.title,
            artist = song.artist
        )
        val updated = current.copy(
            title = song.title.ifBlank { current.title },
            artist = song.artist.ifBlank { current.artist },
            skipCount = current.skipCount + 1
        )
        statsMap[song.id] = updated
        persist()
    }

    fun topPlayedTracks(limit: Int = 50): List<TrackStats> {
        load()
        return statsMap.values
            .sortedByDescending { it.playCount }
            .take(limit)
    }

    fun topArtists(limit: Int = 20): List<ArtistStats> {
        load()
        return statsMap.values
            .filter { it.artist.isNotBlank() }
            .groupBy { it.artist }
            .map { (artist, tracks) ->
                ArtistStats(
                    artist = artist,
                    totalPlayCount = tracks.sumOf { it.playCount },
                    totalListenDurationMs = tracks.sumOf { it.totalListenDurationMs },
                    trackCount = tracks.size
                )
            }
            .sortedByDescending { it.totalPlayCount }
            .take(limit)
    }

    private fun persist(): Boolean {
        return try {
            val json = serializeStatsJson(statsMap.values.toList())
            val parent = file.parent
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent)
            }
            val temp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(temp, json)
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (e: IOException) {
            System.err.println("Failed persisting listening stats: ${e.message}")
            false
        }
    }

    private fun serializeStatsJson(stats: List<TrackStats>): String {
        val sb = StringBuilder("[\n")
        stats.forEachIndexed { index, item ->
            sb.append("  {\n")
            sb.append("    \"trackId\": \"").append(escapeJson(item.trackId)).append("\",\n")
            sb.append("    \"title\": \"").append(escapeJson(item.title)).append("\",\n")
            sb.append("    \"artist\": \"").append(escapeJson(item.artist)).append("\",\n")
            sb.append("    \"playCount\": ").append(item.playCount).append(",\n")
            sb.append("    \"skipCount\": ").append(item.skipCount).append(",\n")
            sb.append("    \"lastPlayedEpochMs\": ").append(item.lastPlayedEpochMs).append(",\n")
            sb.append("    \"totalListenDurationMs\": ").append(item.totalListenDurationMs).append("\n")
            sb.append("  }")
            if (index < stats.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")
        return sb.toString()
    }

    private fun parseStatsJson(json: String): Map<String, TrackStats> {
        val map = mutableMapOf<String, TrackStats>()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return map

        val objectPattern = Regex("""\{[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
        for (match in objectPattern.findAll(trimmed)) {
            val block = match.value
            val trackId = Regex(""""trackId"\s*:\s*"([^"]*)"""").find(block)?.groupValues?.get(1) ?: continue
            val title = Regex(""""title"\s*:\s*"([^"]*)"""").find(block)?.groupValues?.get(1) ?: ""
            val artist = Regex(""""artist"\s*:\s*"([^"]*)"""").find(block)?.groupValues?.get(1) ?: ""
            val playCount = Regex(""""playCount"\s*:\s*([0-9]+)""").find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val skipCount = Regex(""""skipCount"\s*:\s*([0-9]+)""").find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val lastPlayed = Regex(""""lastPlayedEpochMs"\s*:\s*([0-9]+)""").find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val totalListen = Regex(""""totalListenDurationMs"\s*:\s*([0-9]+)""").find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

            val stats = TrackStats(
                trackId = unescapeJson(trackId),
                title = unescapeJson(title),
                artist = unescapeJson(artist),
                playCount = playCount,
                skipCount = skipCount,
                lastPlayedEpochMs = lastPlayed,
                totalListenDurationMs = totalListen
            )
            map[stats.trackId] = stats
        }
        return map
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    }

    private fun unescapeJson(str: String): String {
        return str.replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\\\", "\\")
    }

    companion object {
        private val defaultInstance by lazy { ListeningStatsStore() }
        fun defaultStore(): ListeningStatsStore = defaultInstance
    }
}

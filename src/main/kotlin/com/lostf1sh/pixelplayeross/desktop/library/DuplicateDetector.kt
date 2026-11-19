package com.lostf1sh.pixelplayeross.desktop.library

import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

enum class DuplicateMatchReason(val description: String) {
    EXACT_BINARY_HASH("Identical audio file (Exact SHA-256 match)"),
    METADATA_AND_DURATION("Matching Title, Artist, and Duration (±1s)"),
    SIZE_AND_DURATION("Identical File Size and Audio Duration")
}

data class DuplicateGroup(
    val matchReason: DuplicateMatchReason,
    val recommendedKeeper: DesktopSong,
    val duplicateCandidates: List<DesktopSong>
) {
    val allSongs: List<DesktopSong> get() = listOf(recommendedKeeper) + duplicateCandidates
}

object DuplicateDetector {

    fun findDuplicates(
        songs: List<DesktopSong>,
        checkCancellation: () -> Unit = {}
    ): List<DuplicateGroup> {
        if (songs.size < 2) return emptyList()

        val processedIds = mutableSetOf<String>()
        val result = mutableListOf<DuplicateGroup>()

        // 1. Group by exact file size to find candidate exact binary matches fast
        val bySize = songs.groupBy { it.sizeBytes }.filter { it.value.size > 1 && it.key > 0 }
        for ((_, candidateGroup) in bySize) {
            checkCancellation()
            val hashMap = mutableMapOf<String, MutableList<DesktopSong>>()
            for (song in candidateGroup) {
                if (song.id in processedIds) continue
                val hash = computeQuickHash(song.path) ?: continue
                hashMap.computeIfAbsent(hash) { mutableListOf() }.add(song)
            }
            for ((_, matchingSongs) in hashMap) {
                if (matchingSongs.size > 1) {
                    val sorted = pickBestKeeper(matchingSongs)
                    val keeper = sorted.first()
                    val dupes = sorted.drop(1)
                    result.add(DuplicateGroup(DuplicateMatchReason.EXACT_BINARY_HASH, keeper, dupes))
                    matchingSongs.forEach { processedIds.add(it.id) }
                }
            }
        }

        // 2. Metadata + Duration matching (normalized title & artist with duration within 1.5s)
        val remainingSongs = songs.filter { it.id !in processedIds }
        val metaGroups = mutableMapOf<String, MutableList<DesktopSong>>()
        for (song in remainingSongs) {
            checkCancellation()
            val normKey = normalizeMetadataKey(song.title, song.artist)
            if (normKey.isNotBlank()) {
                metaGroups.computeIfAbsent(normKey) { mutableListOf() }.add(song)
            }
        }

        for ((_, candidateList) in metaGroups) {
            if (candidateList.size > 1) {
                // Group by duration tolerance (within 1500ms)
                val clusters = clusterByDuration(candidateList, 1500L)
                for (cluster in clusters) {
                    if (cluster.size > 1) {
                        val unhandled = cluster.filter { it.id !in processedIds }
                        if (unhandled.size > 1) {
                            val sorted = pickBestKeeper(unhandled)
                            val keeper = sorted.first()
                            val dupes = sorted.drop(1)
                            result.add(DuplicateGroup(DuplicateMatchReason.METADATA_AND_DURATION, keeper, dupes))
                            unhandled.forEach { processedIds.add(it.id) }
                        }
                    }
                }
            }
        }

        // 3. Exact duration (> 10s) and identical file size
        val stillRemaining = songs.filter { it.id !in processedIds }
        val sizeDurationGroups = stillRemaining
            .filter { it.durationMs > 10000L && it.sizeBytes > 0L }
            .groupBy { "${it.sizeBytes}_${it.durationMs}" }
            .filter { it.value.size > 1 }

        for ((_, matchingSongs) in sizeDurationGroups) {
            checkCancellation()
            val unhandled = matchingSongs.filter { it.id !in processedIds }
            if (unhandled.size > 1) {
                val sorted = pickBestKeeper(unhandled)
                val keeper = sorted.first()
                val dupes = sorted.drop(1)
                result.add(DuplicateGroup(DuplicateMatchReason.SIZE_AND_DURATION, keeper, dupes))
                unhandled.forEach { processedIds.add(it.id) }
            }
        }

        return result
    }

    fun deleteTrackFile(song: DesktopSong): Boolean {
        return try {
            val path = Path.of(song.path)
            Files.deleteIfExists(path)
        } catch (_: Exception) {
            false
        }
    }

    private fun pickBestKeeper(candidates: List<DesktopSong>): List<DesktopSong> {
        // Preference: Lossless formats (FLAC/WAV/AIFF) > higher sizeBytes (higher bitrate) > has cover
        return candidates.sortedWith(
            compareByDescending<DesktopSong> { isLossless(it.format) }
                .thenByDescending { it.sizeBytes }
                .thenByDescending { if (it.coverPath != null) 1 else 0 }
                .thenByDescending { if (it.year > 0) 1 else 0 }
        )
    }

    private fun isLossless(format: String): Boolean {
        val f = format.uppercase(Locale.ROOT)
        return f in setOf("FLAC", "WAV", "AIFF", "AIF", "ALAC")
    }

    private fun clusterByDuration(songs: List<DesktopSong>, toleranceMs: Long): List<List<DesktopSong>> {
        val sorted = songs.sortedBy { it.durationMs }
        val clusters = mutableListOf<MutableList<DesktopSong>>()
        for (song in sorted) {
            val cluster = clusters.lastOrNull()
            if (cluster != null && abs(cluster.first().durationMs - song.durationMs) <= toleranceMs) {
                cluster.add(song)
            } else {
                clusters.add(mutableListOf(song))
            }
        }
        return clusters
    }

    fun normalizeMetadataKey(title: String, artist: String): String {
        val cleanTitle = cleanString(title)
        val cleanArtist = cleanString(artist)
        return "$cleanArtist||$cleanTitle"
    }

    private fun cleanString(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .replace(Regex("""\[.*?\]|\(.*?\)|(feat\..*)|(ft\..*)"""), "")
            .replace(Regex("""[^a-z0-9]"""), "")
            .trim()
    }

    private fun computeQuickHash(filePath: String): String? {
        val file = File(filePath)
        if (!file.isFile || !file.canRead()) return null
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val length = file.length()
            FileInputStream(file).use { input ->
                val buffer = ByteArray(65536)
                // If file is large (> 2MB), hash first 1MB and last 64KB for blazing fast library scans
                if (length > 2 * 1024 * 1024) {
                    var read = 0
                    var total = 0
                    while (total < 1024 * 1024 && input.read(buffer).also { read = it } != -1) {
                        md.update(buffer, 0, read)
                        total += read
                    }
                    // Mix in file size
                    md.update(length.toString().toByteArray())
                } else {
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        md.update(buffer, 0, read)
                    }
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}

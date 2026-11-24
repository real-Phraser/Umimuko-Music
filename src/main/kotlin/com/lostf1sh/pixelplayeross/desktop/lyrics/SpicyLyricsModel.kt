package com.lostf1sh.pixelplayeross.desktop.lyrics

/** Sync granularity supported by the Spicy Lyrics engine. */
enum class SpicyLyricsType {
    SYLLABLE,
    LINE,
    STATIC
}

/** A single timed syllable or word token within a [SpicyLine]. */
data class SpicySyllable(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isPartOfWord: Boolean = false,
    val romanizedText: String? = null
)

/** A single lyric line, optionally carrying syllable timings, romanization, duet alignment, and background vocals. */
data class SpicyLine(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val syllables: List<SpicySyllable> = emptyList(),
    val romanizedText: String? = null,
    val oppositeAligned: Boolean = false,
    val backgroundVocals: List<SpicyLine> = emptyList()
)

/** Complete parsed lyrics payload for a track. */
data class SpicyLyrics(
    val type: SpicyLyricsType,
    val lines: List<SpicyLine>,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val songWriters: List<String> = emptyList(),
    val uploaderName: String? = null,
    val source: String? = null
) {
    /**
     * Returns the index of the active lyric line at [timeMs], or `-1` if [lines] is empty
     * or playback is before the first synced line. For [SpicyLyricsType.STATIC], returns `0`.
     */
    fun findActiveLineIndex(timeMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (type == SpicyLyricsType.STATIC) return 0
        if (timeMs < lines.first().startTimeMs) return -1

        var low = 0
        var high = lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startTimeMs <= timeMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    companion object {
        val EMPTY = SpicyLyrics(SpicyLyricsType.STATIC, emptyList())
    }
}

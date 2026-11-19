package com.lostf1sh.pixelplayeross.desktop.lyrics

data class LrcLine(
    val timestampMs: Long,
    val text: String
)

data class LrcLyrics(
    val lines: List<LrcLine>,
    val isSynced: Boolean = true
) {
    fun findActiveLineIndex(timeMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (!isSynced) return 0
        if (timeMs < lines.first().timestampMs) return -1

        var low = 0
        var high = lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= timeMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    companion object {
        val EMPTY = LrcLyrics(emptyList(), isSynced = false)
    }
}

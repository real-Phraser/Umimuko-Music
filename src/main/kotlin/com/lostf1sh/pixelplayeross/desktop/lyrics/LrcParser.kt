package com.lostf1sh.pixelplayeross.desktop.lyrics

object LrcParser {

    private val TIME_TAG_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{2,3}))?\]""")
    private val OFFSET_REGEX = Regex("""\[offset:\s*([+-]?\d+)\]""", RegexOption.IGNORE_CASE)

    fun parse(content: String): LrcLyrics {
        if (content.isBlank()) return LrcLyrics.EMPTY

        val rawLines = content.lines()
        var offsetMs = 0L

        // Check for [offset: +/-ms] tag
        for (line in rawLines) {
            val match = OFFSET_REGEX.find(line)
            if (match != null) {
                offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
                break
            }
        }

        val parsedLines = mutableListOf<LrcLine>()
        var foundAnyTimeTag = false

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isBlank() || isMetadataTag(line)) continue

            val timeMatches = TIME_TAG_REGEX.findAll(line).toList()
            if (timeMatches.isNotEmpty()) {
                foundAnyTimeTag = true
                val text = line.replace(TIME_TAG_REGEX, "").trim()
                for (match in timeMatches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues[3]
                    val fractionMs = when (fractionStr.length) {
                        2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                        3 -> fractionStr.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    val timestamp = (minutes * 60_000L + seconds * 1_000L + fractionMs + offsetMs).coerceAtLeast(0L)
                    parsedLines.add(LrcLine(timestamp, text))
                }
            } else if (!foundAnyTimeTag) {
                // Plain text without timestamps
                parsedLines.add(LrcLine(0L, line))
            }
        }

        return if (foundAnyTimeTag) {
            val sorted = parsedLines.sortedBy { it.timestampMs }
            LrcLyrics(sorted, isSynced = true)
        } else {
            LrcLyrics(parsedLines, isSynced = false)
        }
    }

    private fun isMetadataTag(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return false
        val key = trimmed.substring(1, trimmed.length - 1).substringBefore(":").lowercase()
        return key in setOf("ar", "al", "ti", "au", "length", "by", "offset", "re", "ve")
    }
}

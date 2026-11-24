package com.lostf1sh.pixelplayeross.desktop.lyrics

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong
import org.xml.sax.InputSource

/**
 * Parses Spicy Lyrics API JSON, TTML, LRC (including enhanced word-level LRC),
 * and plain-text lyrics into [SpicyLyrics].
 */
object SpicyLyricsParser {

    private val TIME_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{2,3}))?\]""")
    private val ENHANCED_WORD_TAG_REGEX = Regex("""<(\d{1,3}):(\d{2})(?:\.(\d{2,3}))?>""")
    private val OFFSET_REGEX = Regex("""\[offset:\s*([+-]?\d+)\]""", RegexOption.IGNORE_CASE)
    private val METADATA_KEYS = setOf("ar", "al", "ti", "au", "length", "by", "offset", "re", "ve")

    /** Auto-detects format (Spicy JSON, TTML, LRC, or plain text) and parses into [SpicyLyrics]. */
    fun parse(content: String): SpicyLyrics {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return SpicyLyrics.EMPTY
        return when {
            trimmed.startsWith("{") -> parseSpicyJson(trimmed)
            trimmed.contains("<tt", ignoreCase = true) && trimmed.contains("</tt>", ignoreCase = true) ->
                parseTtml(trimmed)
            else -> parseLrcOrPlain(content)
        }
    }

    /** Parses a Spicy Lyrics API JSON response into [SpicyLyrics]. */
    fun parseSpicyJson(json: String): SpicyLyrics {
        if (json.isBlank()) return SpicyLyrics.EMPTY
        val root = SimpleJson.parse(json) as? Map<*, *> ?: return SpicyLyrics.EMPTY

        val body = (root["Body"] as? Map<*, *>) ?: root
        val source = (root["source"] ?: root["Source"] ?: body["source"] ?: body["Source"]) as? String

        val rawType = (body["Type"] as? String).orEmpty().trim()
        val type = when (rawType.lowercase()) {
            "syllable", "word" -> SpicyLyricsType.SYLLABLE
            "line" -> SpicyLyricsType.LINE
            "static" -> SpicyLyricsType.STATIC
            else -> SpicyLyricsType.STATIC
        }

        val bodyStartMs = secondsToMs(body["StartTime"]) ?: 0L
        val bodyEndMs = secondsToMs(body["EndTime"]) ?: 0L

        val songWriters = (body["SongWriters"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
            ?: emptyList()

        val uploadAttr = body["UploadAttribution"] as? Map<*, *>
        val uploader = uploadAttr?.get("Uploader") as? Map<*, *>
        val uploaderName = (uploader?.get("username") ?: uploader?.get("Username")) as? String

        val rawContent = body["Content"] as? List<*> ?: emptyList<Any?>()
        val parsedLines = mutableListOf<SpicyLine>()

        for (item in rawContent) {
            val obj = item as? Map<*, *> ?: continue
            val parsed = parseContentItem(obj, type)
            if (parsed != null) {
                parsedLines.add(parsed)
            }
        }

        if (parsedLines.isEmpty()) return SpicyLyrics.EMPTY

        val finalLines = if (type != SpicyLyricsType.STATIC) {
            parsedLines.sortedBy { it.startTimeMs }
        } else {
            parsedLines
        }

        val resolvedStart = if (bodyStartMs > 0L || type == SpicyLyricsType.STATIC) {
            bodyStartMs
        } else {
            finalLines.first().startTimeMs
        }
        val resolvedEnd = if (bodyEndMs > 0L || type == SpicyLyricsType.STATIC) {
            bodyEndMs
        } else {
            finalLines.maxOf { it.endTimeMs }
        }

        return SpicyLyrics(
            type = type,
            lines = finalLines,
            startTimeMs = resolvedStart,
            endTimeMs = resolvedEnd,
            songWriters = songWriters,
            uploaderName = uploaderName?.takeIf { it.isNotBlank() },
            source = source?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseContentItem(obj: Map<*, *>, parentType: SpicyLyricsType): SpicyLine? {
        val oppositeAligned = (obj["OppositeAligned"] as? Boolean) ?: false
        val lead = obj["Lead"] as? Map<*, *>
        val mainObj = lead ?: obj

        val rawSyllables = (mainObj["Syllables"] as? List<*>) ?: (mainObj["Words"] as? List<*>)
        val syllables = rawSyllables?.mapNotNull { parseSyllable(it as? Map<*, *>) } ?: emptyList()

        val explicitText = (mainObj["Text"] as? String)?.trimEnd()
        val text = when {
            !explicitText.isNullOrEmpty() -> explicitText
            syllables.isNotEmpty() -> joinSyllablesText(syllables) { it.text }
            else -> ""
        }

        val explicitRomanized = (mainObj["RomanizedText"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val romanizedText = explicitRomanized ?: run {
            if (syllables.any { !it.romanizedText.isNullOrBlank() }) {
                joinSyllablesText(syllables) { it.romanizedText.orEmpty() }.trim().takeIf { it.isNotEmpty() }
            } else null
        }

        val bgList = (obj["Background"] as? List<*>)?.mapNotNull { bgItem ->
            val bgMap = bgItem as? Map<*, *> ?: return@mapNotNull null
            parseContentItem(bgMap, parentType)
        } ?: emptyList()

        if (text.isEmpty() && syllables.isEmpty() && bgList.isEmpty()) return null

        val startMs = secondsToMs(mainObj["StartTime"])
            ?: secondsToMs(obj["StartTime"])
            ?: syllables.firstOrNull()?.startTimeMs
            ?: 0L
        val endMs = secondsToMs(mainObj["EndTime"])
            ?: secondsToMs(obj["EndTime"])
            ?: syllables.lastOrNull()?.endTimeMs
            ?: startMs

        return SpicyLine(
            startTimeMs = startMs,
            endTimeMs = maxOf(startMs, endMs),
            text = text,
            syllables = if (parentType == SpicyLyricsType.SYLLABLE || syllables.isNotEmpty()) syllables else emptyList(),
            romanizedText = romanizedText,
            oppositeAligned = oppositeAligned,
            backgroundVocals = bgList
        )
    }

    private fun parseSyllable(obj: Map<*, *>?): SpicySyllable? {
        if (obj == null) return null
        val text = (obj["Text"] as? String) ?: return null
        val startMs = secondsToMs(obj["StartTime"]) ?: 0L
        val endMs = secondsToMs(obj["EndTime"]) ?: startMs
        val isPartOfWord = (obj["IsPartOfWord"] as? Boolean) ?: false
        val romanized = (obj["RomanizedText"] as? String)?.takeIf { it.isNotBlank() }
        return SpicySyllable(
            text = text,
            startTimeMs = startMs,
            endTimeMs = maxOf(startMs, endMs),
            isPartOfWord = isPartOfWord,
            romanizedText = romanized
        )
    }

    /** Parses TTML (Timed Text Markup Language) lyrics into [SpicyLyrics]. */
    fun parseTtml(ttml: String): SpicyLyrics {
        if (ttml.isBlank()) return SpicyLyrics.EMPTY
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
            val pNodes = doc.getElementsByTagName("p")
            val lines = mutableListOf<SpicyLine>()
            var hasSyllables = false
            var hasLineTimings = false

            for (i in 0 until pNodes.length) {
                val pElem = pNodes.item(i) as? Element ?: continue
                val beginMs = parseTtmlTimeMs(pElem.getAttribute("begin"))
                val endMs = parseTtmlTimeMs(pElem.getAttribute("end"))
                if (beginMs != null) hasLineTimings = true

                val agent = pElem.getAttribute("ttm:agent").ifBlank { pElem.getAttribute("agent") }
                val oppositeAligned = agent.isNotBlank() && !agent.equals("v1", ignoreCase = true)

                val lineSyllables = mutableListOf<SpicySyllable>()
                val bgLines = mutableListOf<SpicyLine>()
                val textBuilder = StringBuilder()
                var lineRomanized: String? = null

                val children = pElem.childNodes
                for (c in 0 until children.length) {
                    val child = children.item(c)
                    when (child.nodeType) {
                        Node.TEXT_NODE -> {
                            textBuilder.append(child.textContent.orEmpty())
                        }
                        Node.ELEMENT_NODE -> {
                            val el = child as Element
                            if (el.localName.equals("span", ignoreCase = true) || el.tagName.equals("span", ignoreCase = true)) {
                                val role = el.getAttribute("ttm:role").ifBlank { el.getAttribute("role") }
                                when {
                                    role.equals("x-bg", ignoreCase = true) -> {
                                        parseTtmlSpanLine(el)?.let { bgLines.add(it) }
                                    }
                                    role.equals("x-roman", ignoreCase = true) -> {
                                        lineRomanized = el.textContent?.trim()?.takeIf { it.isNotEmpty() }
                                    }
                                    else -> {
                                        val sText = el.textContent.orEmpty()
                                        val sBegin = parseTtmlTimeMs(el.getAttribute("begin"))
                                        val sEnd = parseTtmlTimeMs(el.getAttribute("end"))
                                        textBuilder.append(sText)
                                        if (sBegin != null && sText.isNotEmpty()) {
                                            hasSyllables = true
                                            val nextNode = children.item(c + 1)
                                            val followedBySpace = nextNode?.nodeType == Node.TEXT_NODE &&
                                                nextNode.textContent.orEmpty().startsWith(" ")
                                            val endsWithSpace = sText.endsWith(" ")
                                            val isPart = !followedBySpace && !endsWithSpace &&
                                                (nextNode?.nodeType == Node.ELEMENT_NODE ||
                                                    (nextNode?.nodeType == Node.TEXT_NODE && nextNode.textContent.orEmpty().isNotEmpty()))
                                            lineSyllables.add(
                                                SpicySyllable(
                                                    text = sText.trim(),
                                                    startTimeMs = sBegin,
                                                    endTimeMs = maxOf(sBegin, sEnd ?: sBegin),
                                                    isPartOfWord = isPart
                                                )
                                            )
                                        }
                                    }
                                }
                            } else {
                                textBuilder.append(el.textContent.orEmpty())
                            }
                        }
                    }
                }

                val cleanText = textBuilder.toString().replace(Regex("\\s+"), " ").trim()
                if (cleanText.isEmpty() && lineSyllables.isEmpty() && bgLines.isEmpty()) continue

                val resolvedStart = beginMs ?: lineSyllables.firstOrNull()?.startTimeMs ?: 0L
                val resolvedEnd = endMs ?: lineSyllables.lastOrNull()?.endTimeMs ?: resolvedStart

                lines.add(
                    SpicyLine(
                        startTimeMs = resolvedStart,
                        endTimeMs = maxOf(resolvedStart, resolvedEnd),
                        text = cleanText,
                        syllables = lineSyllables,
                        romanizedText = lineRomanized,
                        oppositeAligned = oppositeAligned,
                        backgroundVocals = bgLines
                    )
                )
            }

            if (lines.isEmpty()) return SpicyLyrics.EMPTY

            val type = when {
                hasSyllables -> SpicyLyricsType.SYLLABLE
                hasLineTimings -> SpicyLyricsType.LINE
                else -> SpicyLyricsType.STATIC
            }
            val sorted = if (type != SpicyLyricsType.STATIC) lines.sortedBy { it.startTimeMs } else lines

            SpicyLyrics(
                type = type,
                lines = sorted,
                startTimeMs = sorted.first().startTimeMs,
                endTimeMs = sorted.maxOf { it.endTimeMs },
                source = "TTML"
            )
        } catch (_: Exception) {
            SpicyLyrics.EMPTY
        }
    }

    private fun parseTtmlSpanLine(span: Element): SpicyLine? {
        val beginMs = parseTtmlTimeMs(span.getAttribute("begin"))
        val endMs = parseTtmlTimeMs(span.getAttribute("end"))
        val syllables = mutableListOf<SpicySyllable>()
        val textBuilder = StringBuilder()

        val children = span.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE -> textBuilder.append(child.textContent.orEmpty())
                Node.ELEMENT_NODE -> {
                    val el = child as Element
                    val sText = el.textContent.orEmpty()
                    textBuilder.append(sText)
                    val sBegin = parseTtmlTimeMs(el.getAttribute("begin"))
                    val sEnd = parseTtmlTimeMs(el.getAttribute("end"))
                    if (sBegin != null && sText.isNotEmpty()) {
                        syllables.add(
                            SpicySyllable(
                                text = sText.trim(),
                                startTimeMs = sBegin,
                                endTimeMs = maxOf(sBegin, sEnd ?: sBegin),
                                isPartOfWord = !sText.endsWith(" ")
                            )
                        )
                    }
                }
            }
        }

        val text = textBuilder.toString().replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty() && syllables.isEmpty()) return null
        val start = beginMs ?: syllables.firstOrNull()?.startTimeMs ?: 0L
        val end = endMs ?: syllables.lastOrNull()?.endTimeMs ?: start
        return SpicyLine(
            startTimeMs = start,
            endTimeMs = maxOf(start, end),
            text = text,
            syllables = syllables
        )
    }

    /** Parses LRC (standard or enhanced `<mm:ss.cc>`) or plain text into [SpicyLyrics]. */
    fun parseLrcOrPlain(content: String): SpicyLyrics {
        if (content.isBlank()) return SpicyLyrics.EMPTY

        val rawLines = content.lines()
        var offsetMs = 0L
        for (line in rawLines) {
            val match = OFFSET_REGEX.find(line)
            if (match != null) {
                offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
                break
            }
        }

        data class RawEntry(val startMs: Long, val rawText: String)

        val entries = mutableListOf<RawEntry>()
        var foundAnyTimeTag = false
        var foundEnhancedWords = false

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isBlank() || isMetadataTag(line)) continue

            val timeMatches = TIME_TAG_REGEX.findAll(line).toList()
            if (timeMatches.isNotEmpty()) {
                foundAnyTimeTag = true
                val text = line.replace(TIME_TAG_REGEX, "").trim()
                for (match in timeMatches) {
                    val timestamp = parseLrcTimestamp(
                        match.groupValues[1],
                        match.groupValues[2],
                        match.groupValues[3],
                        offsetMs
                    )
                    entries.add(RawEntry(timestamp, text))
                }
            } else if (!foundAnyTimeTag) {
                entries.add(RawEntry(0L, line))
            }
        }

        if (entries.isEmpty()) return SpicyLyrics.EMPTY

        if (!foundAnyTimeTag) {
            val staticLines = entries.map { SpicyLine(0L, 0L, it.rawText) }
            return SpicyLyrics(
                type = SpicyLyricsType.STATIC,
                lines = staticLines,
                source = "Plain"
            )
        }

        val sorted = entries.sortedBy { it.startMs }
        val lines = sorted.mapIndexed { idx, entry ->
            val nextStart = sorted.getOrNull(idx + 1)?.startMs ?: (entry.startMs + 5_000L)
            val lineEndMs = maxOf(entry.startMs, nextStart)

            val enhancedMatches = ENHANCED_WORD_TAG_REGEX.findAll(entry.rawText).toList()
            if (enhancedMatches.isNotEmpty()) {
                foundEnhancedWords = true
                val syllables = parseEnhancedLrcSyllables(entry.rawText, enhancedMatches, lineEndMs, offsetMs)
                val cleanText = entry.rawText.replace(ENHANCED_WORD_TAG_REGEX, "").replace(Regex("\\s+"), " ").trim()
                SpicyLine(
                    startTimeMs = entry.startMs,
                    endTimeMs = maxOf(lineEndMs, syllables.lastOrNull()?.endTimeMs ?: lineEndMs),
                    text = cleanText,
                    syllables = syllables
                )
            } else {
                SpicyLine(
                    startTimeMs = entry.startMs,
                    endTimeMs = lineEndMs,
                    text = entry.rawText
                )
            }
        }

        return SpicyLyrics(
            type = if (foundEnhancedWords) SpicyLyricsType.SYLLABLE else SpicyLyricsType.LINE,
            lines = lines,
            startTimeMs = lines.first().startTimeMs,
            endTimeMs = lines.last().endTimeMs,
            source = "LRC"
        )
    }

    private fun parseEnhancedLrcSyllables(
        rawText: String,
        matches: List<MatchResult>,
        lineEndMs: Long,
        offsetMs: Long
    ): List<SpicySyllable> {
        val result = mutableListOf<SpicySyllable>()
        for (i in matches.indices) {
            val m = matches[i]
            val startMs = parseLrcTimestamp(m.groupValues[1], m.groupValues[2], m.groupValues[3], offsetMs)
            val textStart = m.range.last + 1
            val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else rawText.length
            val chunk = rawText.substring(textStart, textEnd)
            val trimmedChunk = chunk.trim()
            if (trimmedChunk.isEmpty()) continue

            val endMs = if (i + 1 < matches.size) {
                val next = matches[i + 1]
                parseLrcTimestamp(next.groupValues[1], next.groupValues[2], next.groupValues[3], offsetMs)
            } else {
                lineEndMs
            }

            val isPartOfWord = !chunk.endsWith(" ") && i + 1 < matches.size
            result.add(
                SpicySyllable(
                    text = trimmedChunk,
                    startTimeMs = startMs,
                    endTimeMs = maxOf(startMs, endMs),
                    isPartOfWord = isPartOfWord
                )
            )
        }
        return result
    }

    private fun parseLrcTimestamp(minStr: String, secStr: String, fracStr: String, offsetMs: Long): Long {
        val minutes = minStr.toLongOrNull() ?: 0L
        val seconds = secStr.toLongOrNull() ?: 0L
        val fractionMs = when (fracStr.length) {
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            3 -> fracStr.toLongOrNull() ?: 0L
            else -> 0L
        }
        return (minutes * 60_000L + seconds * 1_000L + fractionMs + offsetMs).coerceAtLeast(0L)
    }

    private fun parseTtmlTimeMs(raw: String?): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.endsWith("ms", ignoreCase = true)) {
            return value.dropLast(2).toDoubleOrNull()?.roundToLong()
        }
        if (value.endsWith("s", ignoreCase = true)) {
            return value.dropLast(1).toDoubleOrNull()?.let { (it * 1000.0).roundToLong() }
        }
        val parts = value.split(":")
        return when (parts.size) {
            1 -> parts[0].toDoubleOrNull()?.let { (it * 1000.0).roundToLong() }
            2 -> {
                val min = parts[0].toLongOrNull() ?: return null
                val sec = parts[1].toDoubleOrNull() ?: return null
                (min * 60_000L + (sec * 1000.0).roundToLong())
            }
            3 -> {
                val hr = parts[0].toLongOrNull() ?: return null
                val min = parts[1].toLongOrNull() ?: return null
                val sec = parts[2].toDoubleOrNull() ?: return null
                (hr * 3_600_000L + min * 60_000L + (sec * 1000.0).roundToLong())
            }
            else -> null
        }
    }

    private fun isMetadataTag(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return false
        val key = trimmed.substring(1, trimmed.length - 1).substringBefore(":").lowercase()
        return key in METADATA_KEYS
    }

    private fun secondsToMs(value: Any?): Long? = when (value) {
        is Number -> (value.toDouble() * 1000.0).roundToLong().coerceAtLeast(0L)
        is String -> value.toDoubleOrNull()?.let { (it * 1000.0).roundToLong().coerceAtLeast(0L) }
        else -> null
    }

    private inline fun joinSyllablesText(
        syllables: List<SpicySyllable>,
        selector: (SpicySyllable) -> String
    ): String {
        val sb = StringBuilder()
        for (i in syllables.indices) {
            val part = selector(syllables[i])
            if (part.isEmpty()) continue
            sb.append(part)
            if (!syllables[i].isPartOfWord &&
                i < syllables.lastIndex &&
                !part.endsWith(" ") &&
                !selector(syllables[i + 1]).startsWith(" ")
            ) {
                sb.append(' ')
            }
        }
        return sb.toString().trimEnd()
    }
}

/**
 * Lightweight zero-dependency recursive-descent JSON parser and serializer
 * used for Spicy Lyrics API responses, iTunes Search API responses, and disk cache.
 */
internal object SimpleJson {

    fun parse(json: String): Any? {
        val parser = Parser(json)
        parser.skipWhitespace()
        if (parser.isEof()) return null
        return parser.readValue()
    }

    fun stringify(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Number -> value.toString()
        is String -> buildString {
            append('"')
            for (ch in value) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) {
                        append("\\u%04x".format(ch.code))
                    } else {
                        append(ch)
                    }
                }
            }
            append('"')
        }
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) ->
            "${stringify(k.toString())}:${stringify(v)}"
        }
        is Iterable<*> -> value.joinToString(",", "[", "]") { stringify(it) }
        else -> stringify(value.toString())
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun isEof(): Boolean = pos >= src.length

        fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (isEof()) return null
            return when (val ch = src[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                '-', in '0'..'9' -> readNumber()
                else -> throw IllegalArgumentException("Unexpected JSON char '$ch' at $pos")
            }
        }

        private fun readObject(): Map<String, Any?> {
            pos++ // '{'
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!isEof() && src[pos] == '}') {
                pos++
                return map
            }
            while (!isEof()) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                if (isEof() || src[pos] != ':') throw IllegalArgumentException("Expected ':' at $pos")
                pos++
                val value = readValue()
                map[key] = value
                skipWhitespace()
                if (!isEof() && src[pos] == ',') {
                    pos++
                } else if (!isEof() && src[pos] == '}') {
                    pos++
                    return map
                } else {
                    throw IllegalArgumentException("Expected ',' or '}' at $pos")
                }
            }
            return map
        }

        private fun readArray(): List<Any?> {
            pos++ // '['
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (!isEof() && src[pos] == ']') {
                pos++
                return list
            }
            while (!isEof()) {
                list.add(readValue())
                skipWhitespace()
                if (!isEof() && src[pos] == ',') {
                    pos++
                } else if (!isEof() && src[pos] == ']') {
                    pos++
                    return list
                } else {
                    throw IllegalArgumentException("Expected ',' or ']' at $pos")
                }
            }
            return list
        }

        private fun readString(): String {
            if (src[pos] != '"') throw IllegalArgumentException("Expected '\"' at $pos")
            pos++
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) break
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 <= src.length) {
                                    val hex = src.substring(pos, pos + 4)
                                    sb.append(hex.toInt(16).toChar())
                                    pos += 4
                                }
                            }
                            else -> sb.append(esc)
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun readNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && src[pos].isDigit()) pos++
            var isFloat = false
            if (pos < src.length && src[pos] == '.') {
                isFloat = true
                pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            val raw = src.substring(start, pos)
            return if (isFloat) raw.toDouble() else (raw.toLongOrNull() ?: raw.toDouble())
        }

        private fun <T> readLiteral(literal: String, result: T): T {
            if (!src.startsWith(literal, pos)) {
                throw IllegalArgumentException("Expected '$literal' at $pos")
            }
            pos += literal.length
            return result
        }
    }
}

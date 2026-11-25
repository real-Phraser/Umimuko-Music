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
    private const val USER_AGENT = "UmimukoMusic/0.26.0 ( https://github.com/real-Phraser )"
    private const val CACHE_SUBDIR = "spicy-lyrics-v2"
    private const val ITUNES_SEARCH_LIMIT = 25
    private const val ITUNES_ARTIST_SEARCH_LIMIT = 5
    private const val ITUNES_LOOKUP_LIMIT = 200
    private const val MIN_ARTIST_LOOKUP_SCORE = 30
    private const val MAX_DURATION_DIFF_MS = 15_000L
    private const val MIN_MATCH_SCORE = 40

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
                fetchFromSpicyApi(song.artist, song.title, song.durationMs, currentKey, song.album)
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
        bearerToken: String,
        album: String = ""
    ): Pair<SpicyLyrics, String>? {
        val trackId = resolveItunesTrackId(artist, title, durationMs, album) ?: return null
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

    internal fun resolveItunesTrackId(
        artist: String,
        title: String,
        durationMs: Long,
        album: String = ""
    ): Long? {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return null

        val primaryArtist = stripFeaturedSuffix(artist.trim())
        val strippedTitle = stripFeaturedSuffix(cleanTitle)
        val primarySplitTitle = cleanTitle.substringBefore('/').trim()
            .let { stripFeaturedSuffix(it) }

        val candidateUrls = buildList {
            val primaryQuery = listOf(primaryArtist, cleanTitle)
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            if (primaryQuery.isNotBlank()) {
                add(buildItunesSearchUrl(primaryQuery))
            }

            if (primarySplitTitle.isNotEmpty() && !primarySplitTitle.equals(cleanTitle, ignoreCase = true)) {
                val splitQuery = listOf(primaryArtist, primarySplitTitle)
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                add(buildItunesSearchUrl(splitQuery))
            }

            add(buildItunesSearchUrl(cleanTitle, attribute = "songTerm"))

            if (strippedTitle.isNotEmpty() && !strippedTitle.equals(cleanTitle, ignoreCase = true)) {
                add(buildItunesSearchUrl(strippedTitle, attribute = "songTerm"))
            }
            if (primarySplitTitle.isNotEmpty() &&
                !primarySplitTitle.equals(cleanTitle, ignoreCase = true) &&
                !primarySplitTitle.equals(strippedTitle, ignoreCase = true)
            ) {
                add(buildItunesSearchUrl(primarySplitTitle, attribute = "songTerm"))
            }
        }.distinct()

        for (url in candidateUrls) {
            queryItunesSearch(url, artist, title, durationMs)?.let { return it }
        }

        if (primaryArtist.isNotEmpty()) {
            lookupItunesTrackByArtist(primaryArtist, artist, title, durationMs)?.let { return it }
        }

        if (album.isNotBlank()) {
            lookupItunesTrackByAlbum(primaryArtist, album, artist, title, durationMs)?.let { return it }
        }

        return null
    }

    private fun buildItunesSearchUrl(
        term: String,
        entity: String = "song",
        attribute: String? = null,
        limit: Int = ITUNES_SEARCH_LIMIT
    ): String {
        val encoded = URLEncoder.encode(term, StandardCharsets.UTF_8)
        val attrParam = if (attribute != null) "&attribute=$attribute" else ""
        return "https://itunes.apple.com/search?term=$encoded&entity=$entity$attrParam&limit=$limit"
    }

    private fun buildItunesLookupUrl(id: Long, entity: String = "song", limit: Int = ITUNES_LOOKUP_LIMIT): String {
        return "https://itunes.apple.com/lookup?id=$id&entity=$entity&limit=$limit"
    }

    private fun fetchItunesJson(url: String): String? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) response.body() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun queryItunesSearch(
        url: String,
        artist: String,
        title: String,
        durationMs: Long
    ): Long? {
        val body = fetchItunesJson(url) ?: return null
        return pickBestItunesTrackId(body, artist, title, durationMs)
    }

    private fun lookupItunesTrackByArtist(
        primaryArtist: String,
        artist: String,
        title: String,
        durationMs: Long
    ): Long? {
        val artistSearchUrl = buildItunesSearchUrl(
            term = primaryArtist,
            entity = "musicArtist",
            limit = ITUNES_ARTIST_SEARCH_LIMIT
        )
        val artistJson = fetchItunesJson(artistSearchUrl) ?: return null
        val artistIds = extractMatchingItunesArtistIds(artistJson, primaryArtist)
        for (artistId in artistIds) {
            val lookupUrl = buildItunesLookupUrl(artistId, entity = "song", limit = ITUNES_LOOKUP_LIMIT)
            queryItunesSearch(lookupUrl, artist, title, durationMs)?.let { return it }
        }
        return null
    }

    private fun lookupItunesTrackByAlbum(
        primaryArtist: String,
        album: String,
        artist: String,
        title: String,
        durationMs: Long
    ): Long? {
        val cleanAlbum = album.trim()
        if (cleanAlbum.isEmpty()) return null
        val albumQuery = listOf(primaryArtist, cleanAlbum)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val albumSearchUrl = buildItunesSearchUrl(
            term = albumQuery,
            entity = "album",
            limit = ITUNES_ARTIST_SEARCH_LIMIT
        )
        val albumJson = fetchItunesJson(albumSearchUrl) ?: return null
        val collectionIds = extractMatchingItunesAlbumIds(albumJson, primaryArtist, cleanAlbum)
        for (collectionId in collectionIds) {
            val lookupUrl = buildItunesLookupUrl(collectionId, entity = "song", limit = ITUNES_LOOKUP_LIMIT)
            queryItunesSearch(lookupUrl, artist, title, durationMs)?.let { return it }
        }
        return null
    }

    internal fun extractMatchingItunesArtistIds(itunesJson: String, artist: String): List<Long> {
        val root = SimpleJson.parse(itunesJson) as? Map<*, *> ?: return emptyList()
        val results = root["results"] as? List<*> ?: return emptyList()
        if (results.isEmpty()) return emptyList()

        val rawArtist = artist.trim().lowercase()
        val normArtist = normalizeForMatch(artist)
        if (normArtist.isEmpty()) return emptyList()
        val artistTokens = significantTokens(normArtist)

        val matches = mutableListOf<Pair<Long, Int>>()
        for (item in results) {
            val map = item as? Map<*, *> ?: continue
            val artistId = (map["artistId"] as? Number)?.toLong()?.takeIf { it > 0L } ?: continue
            val rawArtistName = (map["artistName"] as? String).orEmpty().trim().lowercase()
            val normCandidate = normalizeForMatch(rawArtistName)
            val score = scoreArtistMatch(
                rawArtist = rawArtist,
                normArtist = normArtist,
                artistTokens = artistTokens,
                rawTrackArtist = rawArtistName,
                normTrackArtist = normCandidate,
                trackArtistTokens = significantTokens(normCandidate)
            )?.takeIf { it >= MIN_ARTIST_LOOKUP_SCORE } ?: continue
            matches.add(artistId to score)
        }

        return matches
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
    }

    internal fun extractMatchingItunesAlbumIds(
        itunesJson: String,
        artist: String,
        album: String
    ): List<Long> {
        val root = SimpleJson.parse(itunesJson) as? Map<*, *> ?: return emptyList()
        val results = root["results"] as? List<*> ?: return emptyList()
        if (results.isEmpty()) return emptyList()

        val rawAlbum = album.trim().lowercase()
        val normAlbum = normalizeForMatch(album)
        if (normAlbum.isEmpty()) return emptyList()
        val albumHalves = splitTitleHalves(album)
        val albumTokens = tokenize(normAlbum).toSet()

        val rawArtist = artist.trim().lowercase()
        val normArtist = normalizeForMatch(artist)
        val artistTokens = significantTokens(normArtist)

        val matches = mutableListOf<Pair<Long, Int>>()
        for (item in results) {
            val map = item as? Map<*, *> ?: continue
            val collectionId = (map["collectionId"] as? Number)?.toLong()?.takeIf { it > 0L } ?: continue
            val rawCollectionName = (map["collectionName"] as? String).orEmpty().trim().lowercase()
            val normCollection = normalizeForMatch(rawCollectionName)
            val albumScore = scoreTitleMatch(
                rawTitle = rawAlbum,
                normTitle = normAlbum,
                titleHalves = albumHalves,
                titleTokens = albumTokens,
                rawTrack = rawCollectionName,
                normTrack = normCollection,
                trackHalves = splitTitleHalves(rawCollectionName),
                trackTokens = tokenize(normCollection).toSet()
            ) ?: continue

            val artistScore = if (normArtist.isNotEmpty()) {
                val rawArtistName = (map["artistName"] as? String).orEmpty().trim().lowercase()
                val normCandidate = normalizeForMatch(rawArtistName)
                scoreArtistMatch(
                    rawArtist = rawArtist,
                    normArtist = normArtist,
                    artistTokens = artistTokens,
                    rawTrackArtist = rawArtistName,
                    normTrackArtist = normCandidate,
                    trackArtistTokens = significantTokens(normCandidate)
                ) ?: continue
            } else {
                0
            }

            matches.add(collectionId to (albumScore + artistScore))
        }

        return matches
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
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

        val rawTitle = title.trim().lowercase()
        val normTitle = normalizeForMatch(title)
        if (normTitle.isEmpty()) return null
        val titleHalves = splitTitleHalves(title)
        val titleTokens = tokenize(normTitle).toSet()

        val rawArtist = artist.trim().lowercase()
        val normArtist = normalizeForMatch(artist)
        val artistTokens = significantTokens(normArtist)

        var bestId: Long? = null
        var bestScore = MIN_MATCH_SCORE - 1

        for (item in results) {
            val map = item as? Map<*, *> ?: continue
            val wrapperType = (map["wrapperType"] as? String)?.lowercase()
            if (wrapperType == "artist" || wrapperType == "collection") continue
            val trackId = (map["trackId"] as? Number)?.toLong()?.takeIf { it > 0L } ?: continue
            val rawTrackName = (map["trackName"] as? String).orEmpty().trim().lowercase()
            val rawArtistName = (map["artistName"] as? String).orEmpty().trim().lowercase()
            val trackTimeMs = (map["trackTimeMillis"] as? Number)?.toLong() ?: 0L

            if (durationMs > 0L && trackTimeMs > 0L && abs(durationMs - trackTimeMs) > MAX_DURATION_DIFF_MS) {
                continue
            }

            val normTrack = normalizeForMatch(rawTrackName)
            val trackHalves = splitTitleHalves(rawTrackName)
            val trackTokens = tokenize(normTrack).toSet()
            val titleScore = scoreTitleMatch(
                rawTitle = rawTitle,
                normTitle = normTitle,
                titleHalves = titleHalves,
                titleTokens = titleTokens,
                rawTrack = rawTrackName,
                normTrack = normTrack,
                trackHalves = trackHalves,
                trackTokens = trackTokens
            ) ?: continue

            val artistScore = if (normArtist.isNotEmpty()) {
                val normTrackArtist = normalizeForMatch(rawArtistName)
                val trackArtistTokens = significantTokens(normTrackArtist)
                scoreArtistMatch(
                    rawArtist = rawArtist,
                    normArtist = normArtist,
                    artistTokens = artistTokens,
                    rawTrackArtist = rawArtistName,
                    normTrackArtist = normTrackArtist,
                    trackArtistTokens = trackArtistTokens
                ) ?: continue
            } else {
                0
            }

            var score = titleScore + artistScore
            if (durationMs > 0L && trackTimeMs > 0L) {
                val diff = abs(durationMs - trackTimeMs)
                score += when {
                    diff <= 2_000L -> 30
                    diff <= 5_000L -> 15
                    diff <= 10_000L -> 5
                    else -> -5
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestId = trackId
            }
        }

        return bestId
    }

    private fun scoreTitleMatch(
        rawTitle: String,
        normTitle: String,
        titleHalves: List<String>,
        titleTokens: Set<String>,
        rawTrack: String,
        normTrack: String,
        trackHalves: List<String>,
        trackTokens: Set<String>
    ): Int? {
        if (normTrack.isEmpty()) return null
        if (rawTrack == rawTitle) return 60
        if (normTrack == normTitle) return 50
        if (trackHalves.any { it == normTitle } || titleHalves.any { it == normTrack }) return 45

        if (titleTokens.isEmpty() || trackTokens.isEmpty()) return null
        val common = titleTokens.intersect(trackTokens)
        if (common.isEmpty()) return null

        if (common.size == titleTokens.size || common.size == trackTokens.size) {
            return 30
        }
        val maxTokens = maxOf(titleTokens.size, trackTokens.size)
        return if (common.size * 2 >= maxTokens) 20 else null
    }

    private fun scoreArtistMatch(
        rawArtist: String,
        normArtist: String,
        artistTokens: Set<String>,
        rawTrackArtist: String,
        normTrackArtist: String,
        trackArtistTokens: Set<String>
    ): Int? {
        if (normTrackArtist.isEmpty()) return null
        if (rawTrackArtist == rawArtist) return 50
        if (normTrackArtist == normArtist) return 45
        if (normTrackArtist.length >= 2 && normArtist.length >= 2 &&
            (normTrackArtist.contains(normArtist) || normArtist.contains(normTrackArtist))
        ) {
            return 30
        }
        return if (artistTokens.intersect(trackArtistTokens).isNotEmpty()) 20 else null
    }

    private fun stripFeaturedSuffix(text: String): String {
        return text
            .replace(Regex("""\s*[\(\[]\s*(?:feat\.?|ft\.?|featuring)\s+[^\)\]]*[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun normalizeForMatch(text: String): String {
        return stripFeaturedSuffix(text)
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .trim()
    }

    private fun splitTitleHalves(text: String): List<String> {
        if (!text.contains('/')) return emptyList()
        return text.split('/')
            .map { normalizeForMatch(it) }
            .filter { it.isNotEmpty() }
    }

    private fun tokenize(normalized: String): List<String> {
        if (normalized.isEmpty()) return emptyList()
        return normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun significantTokens(normalized: String): Set<String> {
        val all = tokenize(normalized)
        val filtered = all.filterNot { it in ARTIST_STOPWORDS }
        return (if (filtered.isNotEmpty()) filtered else all).toSet()
    }

    private val ARTIST_STOPWORDS = setOf("the", "a", "an", "and", "of")

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

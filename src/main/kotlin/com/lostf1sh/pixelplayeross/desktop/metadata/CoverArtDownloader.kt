package com.lostf1sh.pixelplayeross.desktop.metadata

import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

data class CoverArtCandidate(
    val mbid: String,
    val releaseTitle: String,
    val artistName: String,
    val imageUrl: String,
    val imageBytes: ByteArray? = null
)

/**
 * Resilient multi-provider Cover Art Downloader:
 * 1. Apple Music / iTunes Search API (1000x1000 crystal-clear, global CDN, zero API key)
 * 2. Deezer API (1000x1000 high-res, zero API key)
 * 3. MusicBrainz & Cover Art Archive (CAA) (open source fallback)
 */
object CoverArtDownloader {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(8))
        .build()

    private const val USER_AGENT = "UmimukoMusic/0.25.0 ( https://github.com/real-Phraser )"

    fun searchAndDownloadCover(
        artist: String,
        album: String,
        title: String = ""
    ): ByteArray? {
        // 1. Try iTunes Search API (Highest quality 1000x1000, 99.9% coverage)
        fetchFromItunes(artist, album, title)?.let { return it }

        // 2. Try Deezer Search API (1000x1000 high-res fallback)
        fetchFromDeezer(artist, album, title)?.let { return it }

        // 3. Try MusicBrainz & Cover Art Archive
        fetchFromMusicBrainz(artist, album, title)?.let { return it }

        return null
    }

    fun fetchFromItunes(artist: String, album: String, title: String): ByteArray? {
        val query = (if (album.isNotBlank()) "$artist $album" else "$artist $title").trim()
        if (query.isBlank()) return null
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val url = "https://itunes.apple.com/search?term=$encoded&entity=album&limit=3"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                val json = response.body()
                val artUrl = Regex(""""artworkUrl100"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)
                if (artUrl != null) {
                    val highResUrl = artUrl.replace("100x100bb", "1000x1000bb")
                    downloadImageBytes(highResUrl)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun fetchFromDeezer(artist: String, album: String, title: String): ByteArray? {
        val query = (if (album.isNotBlank()) "$artist $album" else "$artist $title").trim()
        if (query.isBlank()) return null
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val url = "https://api.deezer.com/search/album?q=$encoded&limit=3"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                val json = response.body()
                val artUrl = Regex(""""cover_xl"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)
                    ?: Regex(""""cover_big"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)
                if (artUrl != null) {
                    downloadImageBytes(artUrl)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun fetchFromMusicBrainz(artist: String, album: String, title: String): ByteArray? {
        val mbid = findReleaseMbid(artist, album, title) ?: return null
        return downloadCoverByMbid(mbid)
    }

    fun findReleaseMbid(artist: String, album: String, title: String): String? {
        val cleanArtist = artist.trim()
        val cleanAlbum = album.trim()
        val cleanTitle = title.trim()

        val query = when {
            cleanAlbum.isNotBlank() && cleanArtist.isNotBlank() ->
                "release:\"$cleanAlbum\" AND artist:\"$cleanArtist\""
            cleanAlbum.isNotBlank() ->
                "release:\"$cleanAlbum\""
            cleanTitle.isNotBlank() && cleanArtist.isNotBlank() ->
                "recording:\"$cleanTitle\" AND artist:\"$cleanArtist\""
            cleanTitle.isNotBlank() ->
                "recording:\"$cleanTitle\""
            else -> return null
        }

        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val endpoint = if (cleanAlbum.isNotBlank()) {
            "https://musicbrainz.org/ws/2/release/?query=$encodedQuery&fmt=json&limit=5"
        } else {
            "https://musicbrainz.org/ws/2/recording/?query=$encodedQuery&fmt=json&limit=5"
        }

        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) return null

            val body = response.body()
            return extractMbidFromJson(body)
        } catch (_: Exception) {
            return null
        }
    }

    fun downloadCoverByMbid(mbid: String): ByteArray? {
        val urls = listOf(
            "https://coverartarchive.org/release/$mbid/front-500",
            "https://coverartarchive.org/release/$mbid/front"
        )
        for (url in urls) {
            val bytes = downloadImageBytes(url)
            if (bytes != null) return bytes
        }
        return null
    }

    fun downloadImageBytes(url: String): ByteArray? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() in 200..299) {
                val bytes = response.body()
                if (bytes != null && bytes.size > 1024) {
                    bytes
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun cacheCoverImage(imageBytes: ByteArray, cacheKey: String): Path? {
        return try {
            val cacheDir = PlatformPaths.getCacheDirectory()
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir)
            }
            val safeName = cacheKey.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".jpg"
            val target = cacheDir.resolve(safeName)
            Files.write(target, imageBytes)
            target
        } catch (_: IOException) {
            null
        }
    }

    fun saveAsFolderCover(folder: Path, imageBytes: ByteArray, fileName: String = "cover.jpg"): Path? {
        return try {
            val target = folder.resolve(fileName)
            Files.write(target, imageBytes)
            target
        } catch (_: Exception) {
            null
        }
    }

    private fun extractMbidFromJson(json: String): String? {
        val idRegex = Regex(""""id"\s*:\s*"([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})"""")
        return idRegex.find(json)?.groupValues?.get(1)
    }
}

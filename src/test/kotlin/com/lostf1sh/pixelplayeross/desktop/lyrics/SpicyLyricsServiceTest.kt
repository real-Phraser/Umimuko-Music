package com.lostf1sh.pixelplayeross.desktop.lyrics

import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class SpicyLyricsServiceTest {

    private val tempDir = Files.createTempDirectory("spicy-lyrics-test-")
    private val audioFile = tempDir.resolve("my_song.mp3")
    private val lrcSidecar = tempDir.resolve("my_song.lrc")
    private val ttmlSidecar = tempDir.resolve("my_song.ttml")

    @BeforeTest
    fun setup() {
        SpicyLyricsService.clearCache()
        Files.writeString(audioFile, "dummy audio bytes")
        Files.writeString(
            lrcSidecar,
            """
                [00:03.00]Sidecar line 1
                [00:07.50]Sidecar line 2
            """.trimIndent()
        )
    }

    @AfterTest
    fun cleanup() {
        SpicyLyricsService.clearCache()
        Files.deleteIfExists(ttmlSidecar)
        Files.deleteIfExists(lrcSidecar)
        Files.deleteIfExists(audioFile)
        Files.deleteIfExists(tempDir)
    }

    @Test
    fun persistsApiKeyAndShowRomanizedPreferences() {
        val origKey = SpicyLyricsService.apiKey
        val origRom = SpicyLyricsService.showRomanized
        try {
            SpicyLyricsService.apiKey = "  test-spicy-key-123  "
            assertEquals("test-spicy-key-123", SpicyLyricsService.apiKey)
            assertTrue(SpicyLyricsService.hasApiKey())

            SpicyLyricsService.apiKey = ""
            assertFalse(SpicyLyricsService.hasApiKey())

            SpicyLyricsService.showRomanized = true
            assertTrue(SpicyLyricsService.showRomanized)

            SpicyLyricsService.showRomanized = false
            assertFalse(SpicyLyricsService.showRomanized)
        } finally {
            SpicyLyricsService.apiKey = origKey
            SpicyLyricsService.showRomanized = origRom
        }
    }

    @Test
    fun readsLocalSidecarLrcAndPrefersTtmlOverLrc() {
        runBlocking {
            val origKey = SpicyLyricsService.apiKey
            try {
                SpicyLyricsService.apiKey = ""
                val song = DesktopSong(
                    id = audioFile.toString(),
                    title = "Sidecar Song",
                    path = audioFile.toString(),
                    format = "MP3",
                    sizeBytes = 1000L,
                    artist = "Local Artist"
                )

                val lrcLyrics = SpicyLyricsService.getLyrics(song)
                assertEquals(SpicyLyricsType.LINE, lrcLyrics.type)
                assertEquals(2, lrcLyrics.lines.size)
                assertEquals("Sidecar line 1", lrcLyrics.lines[0].text)
                assertEquals(3000L, lrcLyrics.lines[0].startTimeMs)

                // Now write a TTML sidecar and clear memory cache; TTML should take precedence
                SpicyLyricsService.clearCache()
                Files.writeString(
                    ttmlSidecar,
                    """
                        <tt xmlns="http://www.w3.org/ns/ttml">
                          <body><div>
                            <p begin="00:01.000" end="00:04.000"><span begin="00:01.000" end="00:02.000">TTML</span> <span begin="00:02.000" end="00:04.000">word</span></p>
                          </div></body>
                        </tt>
                    """.trimIndent()
                )
                val ttmlLyrics = SpicyLyricsService.getLyrics(song)
                assertEquals(SpicyLyricsType.SYLLABLE, ttmlLyrics.type)
                assertEquals("TTML word", ttmlLyrics.lines[0].text)
            } finally {
                SpicyLyricsService.apiKey = origKey
            }
        }
    }

    @Test
    fun fallsBackToEmbeddedLyricsWhenNoSidecarOrApiKey() {
        runBlocking {
            val origKey = SpicyLyricsService.apiKey
            try {
                SpicyLyricsService.apiKey = ""
                val noSidecarAudio = tempDir.resolve("embedded_only.mp3")
                Files.writeString(noSidecarAudio, "dummy audio bytes")

                val song = DesktopSong(
                    id = noSidecarAudio.toString(),
                    title = "NonExistentTrackXYZ999",
                    path = noSidecarAudio.toString(),
                    format = "MP3",
                    sizeBytes = 1000L,
                    artist = "NonExistentArtistXYZ999",
                    lyrics = "[00:02.00]Embedded tag line"
                )

                val lyrics = SpicyLyricsService.getLyrics(song)
                assertEquals(SpicyLyricsType.LINE, lyrics.type)
                assertEquals(1, lyrics.lines.size)
                assertEquals("Embedded tag line", lyrics.lines[0].text)

                Files.deleteIfExists(noSidecarAudio)
            } finally {
                SpicyLyricsService.apiKey = origKey
            }
        }
    }

    @Test
    fun cachesFullSpicyLyricsToDiskAndClearsCache() {
        val song = DesktopSong(
            id = "test-spicy-cache-id",
            title = "Cached Track",
            path = "/dummy/cached.mp3",
            format = "MP3",
            sizeBytes = 1000L,
            artist = "Cached Artist"
        )
        val spicy = SpicyLyrics(
            type = SpicyLyricsType.SYLLABLE,
            lines = listOf(
                SpicyLine(
                    startTimeMs = 1000L,
                    endTimeMs = 3000L,
                    text = "Hello world",
                    syllables = listOf(
                        SpicySyllable("Hel", 1000L, 1500L, isPartOfWord = true),
                        SpicySyllable("lo", 1500L, 2000L, isPartOfWord = false),
                        SpicySyllable("world", 2000L, 3000L, isPartOfWord = false)
                    ),
                    romanizedText = "haro warudo",
                    oppositeAligned = true,
                    backgroundVocals = listOf(SpicyLine(2200L, 2900L, "(yeah)"))
                )
            ),
            startTimeMs = 1000L,
            endTimeMs = 3000L,
            songWriters = listOf("Writer One"),
            uploaderName = "uploader99",
            source = "spicylyrics"
        )

        SpicyLyricsService.cacheToDisk(song, spicy)
        val reloaded = SpicyLyricsService.readFromDiskCache(song)
        assertNotNull(reloaded)
        assertEquals(SpicyLyricsType.SYLLABLE, reloaded.type)
        assertEquals("uploader99", reloaded.uploaderName)
        assertEquals(listOf("Writer One"), reloaded.songWriters)
        assertEquals(1, reloaded.lines.size)
        assertTrue(reloaded.lines[0].oppositeAligned)
        assertEquals("haro warudo", reloaded.lines[0].romanizedText)
        assertEquals(3, reloaded.lines[0].syllables.size)
        assertEquals(1, reloaded.lines[0].backgroundVocals.size)

        SpicyLyricsService.clearCache()
        assertNull(SpicyLyricsService.readFromDiskCache(song))
    }

    @Test
    fun picksBestItunesTrackIdByTitleArtistAndDuration() {
        val itunesResponse = """
            {
              "resultCount": 2,
              "results": [
                {
                  "trackId": 111111,
                  "artistName": "Other Artist",
                  "trackName": "Nightfall (Live)",
                  "trackTimeMillis": 280000
                },
                {
                  "trackId": 222222,
                  "artistName": "Target Artist",
                  "trackName": "Nightfall",
                  "trackTimeMillis": 210500
                }
              ]
            }
        """.trimIndent()

        val picked = SpicyLyricsService.pickBestItunesTrackId(
            itunesJson = itunesResponse,
            artist = "Target Artist",
            title = "Nightfall",
            durationMs = 210000L
        )
        assertEquals(222222L, picked)
    }
}

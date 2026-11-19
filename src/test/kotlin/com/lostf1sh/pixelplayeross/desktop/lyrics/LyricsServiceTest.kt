package com.lostf1sh.pixelplayeross.desktop.lyrics

import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class LyricsServiceTest {

    private val tempDir = Files.createTempDirectory("lyrics-test-")
    private val audioFile = tempDir.resolve("my_song.mp3")
    private val sidecarFile = tempDir.resolve("my_song.lrc")

    @BeforeTest
    fun setup() {
        Files.writeString(audioFile, "dummy audio bytes")
        Files.writeString(
            sidecarFile,
            """
                [00:03.00]Sidecar line 1
                [00:07.50]Sidecar line 2
            """.trimIndent()
        )
    }

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(sidecarFile)
        Files.deleteIfExists(audioFile)
        Files.deleteIfExists(tempDir)
    }

    @Test
    fun readsFromLocalSidecarFile() {
        runBlocking {
            val song = DesktopSong(
                id = audioFile.toString(),
                title = "Sidecar Song",
                path = audioFile.toString(),
                format = "MP3",
                sizeBytes = 1000L,
                artist = "Local Artist"
            )

            val lyrics = LyricsService.getLyrics(song)
            assertTrue(lyrics.isSynced)
            assertEquals(2, lyrics.lines.size)
            assertEquals("Sidecar line 1", lyrics.lines[0].text)
            assertEquals(3000L, lyrics.lines[0].timestampMs)
        }
    }

    @Test
    fun fallsBackToEmbeddedLyricsWhenNoSidecarOrOnlineResult() {
        runBlocking {
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

            val lyrics = LyricsService.getLyrics(song)
            assertTrue(lyrics.isSynced)
            assertEquals(1, lyrics.lines.size)
            assertEquals("Embedded tag line", lyrics.lines[0].text)

            Files.deleteIfExists(noSidecarAudio)
        }
    }

    @Test
    fun cachesPlainLyricsWithoutTimestamps() {
        val song = DesktopSong(
            id = "test-plain-id",
            title = "Plain Track",
            path = "/dummy/plain.mp3",
            format = "MP3",
            sizeBytes = 1000L,
            artist = "Plain Artist"
        )
        val plainLyrics = LrcLyrics(
            lines = listOf(
                LrcLine(0L, "Plain line 1"),
                LrcLine(0L, "Plain line 2")
            ),
            isSynced = false
        )
        LyricsService.cacheToDisk(song, plainLyrics)
        val reloaded = LyricsService.readFromDiskCache(song)
        assertNotNull(reloaded)
        assertFalse(reloaded.isSynced)
        assertEquals(2, reloaded.lines.size)
        assertEquals("Plain line 1", reloaded.lines[0].text)
        assertEquals("Plain line 2", reloaded.lines[1].text)
    }
}

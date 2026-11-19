package com.lostf1sh.pixelplayeross.desktop.stats

import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import java.nio.file.Files
import kotlin.test.*

class ListeningStatsTest {

    private val tempDir = Files.createTempDirectory("stats-test-")
    private val tempFile = tempDir.resolve("test-stats.json")
    private val store = ListeningStatsStore(tempFile)

    private val song1 = DesktopSong(
        id = "/path/to/song1.mp3",
        title = "Starlight",
        path = "/path/to/song1.mp3",
        format = "MP3",
        sizeBytes = 5000000L,
        artist = "Muse",
        album = "Black Holes",
        durationMs = 240000L
    )

    private val song2 = DesktopSong(
        id = "/path/to/song2.mp3",
        title = "Supermassive Black Hole",
        path = "/path/to/song2.mp3",
        format = "MP3",
        sizeBytes = 6000000L,
        artist = "Muse",
        album = "Black Holes",
        durationMs = 210000L
    )

    private val song3 = DesktopSong(
        id = "/path/to/song3.flac",
        title = "Paranoid Android",
        path = "/path/to/song3.flac",
        format = "FLAC",
        sizeBytes = 40000000L,
        artist = "Radiohead",
        album = "OK Computer",
        durationMs = 380000L
    )

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(tempFile)
        Files.deleteIfExists(tempDir)
    }

    @Test
    fun recordsPlaysAndSkipsCorrectly() {
        store.recordPlay(song1, 240000L)
        store.recordPlay(song1, 240000L)
        store.recordSkip(song1)

        val stats = store.getStats(song1.id)
        assertNotNull(stats)
        assertEquals(2L, stats.playCount)
        assertEquals(1L, stats.skipCount)
        assertEquals(480000L, stats.totalListenDurationMs)
        assertTrue(stats.lastPlayedEpochMs > 0L)
    }

    @Test
    fun aggregatesTopPlayedTracksAndArtists() {
        store.recordPlay(song1, 240000L)
        store.recordPlay(song1, 240000L)
        store.recordPlay(song1, 240000L)

        store.recordPlay(song2, 210000L)

        store.recordPlay(song3, 380000L)
        store.recordPlay(song3, 380000L)

        val topTracks = store.topPlayedTracks(10)
        assertEquals(3, topTracks.size)
        assertEquals(song1.id, topTracks[0].trackId)
        assertEquals(3L, topTracks[0].playCount)
        assertEquals(song3.id, topTracks[1].trackId)
        assertEquals(2L, topTracks[1].playCount)
        assertEquals(song2.id, topTracks[2].trackId)
        assertEquals(1L, topTracks[2].playCount)

        val topArtists = store.topArtists(10)
        assertEquals(2, topArtists.size)
        assertEquals("Muse", topArtists[0].artist)
        assertEquals(4L, topArtists[0].totalPlayCount) // 3 + 1
        assertEquals(2, topArtists[0].trackCount)
        assertEquals("Radiohead", topArtists[1].artist)
        assertEquals(2L, topArtists[1].totalPlayCount)
    }

    @Test
    fun persistsAndReloadsAcrossStoreInstances() {
        store.recordPlay(song1, 100000L)

        val newStore = ListeningStatsStore(tempFile)
        val loadedStats = newStore.getStats(song1.id)
        assertNotNull(loadedStats)
        assertEquals(1L, loadedStats.playCount)
        assertEquals(100000L, loadedStats.totalListenDurationMs)
    }
}

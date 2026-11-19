package com.lostf1sh.pixelplayeross.desktop.library

import com.lostf1sh.pixelplayeross.desktop.stats.ListeningStatsStore
import java.nio.file.Files
import kotlin.test.*

class TrackSortingTest {

    private val tempDir = Files.createTempDirectory("sort-test-")
    private val statsStore = ListeningStatsStore(tempDir.resolve("stats.json"))

    private val songA = DesktopSong(
        id = "1",
        title = "Alpha",
        path = "/music/alpha.mp3",
        format = "MP3",
        sizeBytes = 1000L,
        artist = "Zoe",
        album = "Omega Album",
        durationMs = 120000L,
        year = 2020,
        trackNumber = 2,
        discNumber = 1,
        dateAdded = 100L
    )

    private val songB = DesktopSong(
        id = "2",
        title = "Beta",
        path = "/music/beta.mp3",
        format = "MP3",
        sizeBytes = 2000L,
        artist = "Adam",
        album = "Beta Album",
        durationMs = 300000L,
        year = 2015,
        trackNumber = 1,
        discNumber = 1,
        dateAdded = 300L
    )

    private val songC = DesktopSong(
        id = "3",
        title = "Gamma",
        path = "/music/gamma.mp3",
        format = "MP3",
        sizeBytes = 1500L,
        artist = "Adam",
        album = "Alpha Album",
        durationMs = 240000L,
        year = 2018,
        trackNumber = 3,
        discNumber = 1,
        dateAdded = 200L
    )

    private val songs = listOf(songA, songB, songC)

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(tempDir.resolve("stats.json"))
        Files.deleteIfExists(tempDir)
    }

    @Test
    fun sortsByTitleAscendingAndDescending() {
        val asc = TrackSortingSystem.sortTracks(songs, SortOption(SortField.TITLE, SortOrder.ASCENDING))
        assertEquals(listOf("Alpha", "Beta", "Gamma"), asc.map { it.title })

        val desc = TrackSortingSystem.sortTracks(songs, SortOption(SortField.TITLE, SortOrder.DESCENDING))
        assertEquals(listOf("Gamma", "Beta", "Alpha"), desc.map { it.title })
    }

    @Test
    fun sortsByArtistWithAlbumTieBreaker() {
        val asc = TrackSortingSystem.sortTracks(songs, SortOption(SortField.ARTIST, SortOrder.ASCENDING))
        // Adam has Alpha Album (Gamma) and Beta Album (Beta). Zoe has Omega Album (Alpha).
        assertEquals(listOf("Gamma", "Beta", "Alpha"), asc.map { it.title })
    }

    @Test
    fun sortsByDuration() {
        val asc = TrackSortingSystem.sortTracks(songs, SortOption(SortField.DURATION, SortOrder.ASCENDING))
        assertEquals(listOf(120000L, 240000L, 300000L), asc.map { it.durationMs })
    }

    @Test
    fun sortsByYear() {
        val asc = TrackSortingSystem.sortTracks(songs, SortOption(SortField.YEAR, SortOrder.ASCENDING))
        assertEquals(listOf(2015, 2018, 2020), asc.map { it.year })
    }

    @Test
    fun sortsByYearDescendingWithUnknownYearSentinelAtBottom() {
        val songUnknownYear = DesktopSong(
            id = "4",
            title = "Delta",
            path = "/music/delta.mp3",
            format = "MP3",
            sizeBytes = 1100L,
            artist = "Unknown",
            album = "Delta Album",
            durationMs = 180000L,
            year = 0,
            trackNumber = 1,
            discNumber = 1,
            dateAdded = 400L
        )
        val allSongs = songs + songUnknownYear

        val desc = TrackSortingSystem.sortTracks(allSongs, SortOption(SortField.YEAR, SortOrder.DESCENDING))
        // 2020 (Alpha), 2018 (Gamma), 2015 (Beta), 0 (Delta at end)
        assertEquals(listOf("Alpha", "Gamma", "Beta", "Delta"), desc.map { it.title })
    }

    @Test
    fun sortsByArtistDescendingWithBlankArtistAtBottomAndAscendingTieBreaker() {
        val songBlankArtist = DesktopSong(
            id = "4",
            title = "Delta Blank",
            path = "/music/delta_blank.mp3",
            format = "MP3",
            sizeBytes = 1100L,
            artist = "",
            album = "Delta Album",
            durationMs = 180000L,
            year = 2019,
            trackNumber = 1,
            discNumber = 1,
            dateAdded = 400L
        )
        val allSongs = songs + songBlankArtist

        val desc = TrackSortingSystem.sortTracks(allSongs, SortOption(SortField.ARTIST, SortOrder.DESCENDING))
        // Zoe (Alpha), Adam (Gamma with Alpha Album, Beta with Beta Album), Blank (Delta at bottom)
        assertEquals(listOf("Alpha", "Gamma", "Beta", "Delta Blank"), desc.map { it.title })
    }

    @Test
    fun sortsByDateAdded() {
        val asc = TrackSortingSystem.sortTracks(songs, SortOption(SortField.DATE_ADDED, SortOrder.ASCENDING))
        assertEquals(listOf(100L, 200L, 300L), asc.map { it.dateAdded })
    }

    @Test
    fun sortsByPlayCount() {
        statsStore.recordPlay(songA, 10000L)
        statsStore.recordPlay(songC, 10000L)
        statsStore.recordPlay(songC, 10000L)

        val desc = TrackSortingSystem.sortTracks(songs, SortOption(SortField.PLAY_COUNT, SortOrder.DESCENDING), statsStore)
        // Gamma (2 plays) > Alpha (1 play) > Beta (0 plays)
        assertEquals(listOf("Gamma", "Alpha", "Beta"), desc.map { it.title })
    }
}

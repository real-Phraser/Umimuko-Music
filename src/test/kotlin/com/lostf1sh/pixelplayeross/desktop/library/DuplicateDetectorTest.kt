package com.lostf1sh.pixelplayeross.desktop.library

import java.nio.file.Files
import kotlin.test.*

class DuplicateDetectorTest {

    private val tempDir = Files.createTempDirectory("dupe-test-")
    private val file1 = tempDir.resolve("track1.mp3")
    private val file2 = tempDir.resolve("track2.mp3")

    @BeforeTest
    fun setup() {
        Files.writeString(file1, "sample-audio-data-identical-content-12345")
        Files.writeString(file2, "sample-audio-data-identical-content-12345")
    }

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(file1)
        Files.deleteIfExists(file2)
        Files.deleteIfExists(tempDir)
    }

    @Test
    fun detectsExactBinaryDuplicatesViaSha256() {
        val song1 = DesktopSong(
            id = file1.toString(),
            title = "Song One",
            path = file1.toString(),
            format = "MP3",
            sizeBytes = Files.size(file1),
            artist = "Artist",
            durationMs = 180000L
        )

        val song2 = DesktopSong(
            id = file2.toString(),
            title = "Song One (Copy)",
            path = file2.toString(),
            format = "MP3",
            sizeBytes = Files.size(file2),
            artist = "Artist",
            durationMs = 180000L
        )

        val duplicates = DuplicateDetector.findDuplicates(listOf(song1, song2))
        assertEquals(1, duplicates.size)
        assertEquals(DuplicateMatchReason.EXACT_BINARY_HASH, duplicates.first().matchReason)
        assertEquals(2, duplicates.first().allSongs.size)
    }

    @Test
    fun detectsMetadataAndDurationMatches() {
        val song1 = DesktopSong(
            id = "a",
            title = "Bohemian Rhapsody [2011 Remaster]",
            path = "/music/queen/bohemian.mp3",
            format = "MP3",
            sizeBytes = 8000000L,
            artist = "Queen",
            durationMs = 354000L
        )

        val song2 = DesktopSong(
            id = "b",
            title = "Bohemian Rhapsody",
            path = "/music/singles/bohemian.flac",
            format = "FLAC",
            sizeBytes = 35000000L,
            artist = "Queen",
            durationMs = 354500L // 500ms difference
        )

        val duplicates = DuplicateDetector.findDuplicates(listOf(song1, song2))
        assertEquals(1, duplicates.size)
        assertEquals(DuplicateMatchReason.METADATA_AND_DURATION, duplicates.first().matchReason)
        // FLAC should be picked as recommended keeper over MP3
        assertEquals(song2.id, duplicates.first().recommendedKeeper.id)
    }

    @Test
    fun normalizesMetadataKeyIgnoringPunctuationAndFeatures() {
        val key1 = DuplicateDetector.normalizeMetadataKey("Lose Yourself (Remastered)", "Eminem feat. Someone")
        val key2 = DuplicateDetector.normalizeMetadataKey("Lose Yourself", "Eminem")
        assertEquals(key2, key1)
    }
}

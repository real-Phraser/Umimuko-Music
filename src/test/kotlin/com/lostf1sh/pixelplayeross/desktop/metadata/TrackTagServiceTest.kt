package com.lostf1sh.pixelplayeross.desktop.metadata

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.*

class TrackTagServiceTest {

    private val sourceFlac = Path.of("src/test/resources/metadata/plain.flac")
    private val tempDir = Files.createTempDirectory("tag-test-")
    private val testFile = tempDir.resolve("editable.flac")

    @BeforeTest
    fun setup() {
        Files.copy(sourceFlac, testFile, StandardCopyOption.REPLACE_EXISTING)
    }

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(testFile)
        Files.deleteIfExists(tempDir)
    }

    @Test
    fun readsTagsFromAudioFile() {
        val tags = TrackTagService.readTags(testFile)
        assertNotNull(tags)
    }

    @Test
    fun writesAndReReadsTagsSafely() {
        val newTags = EditableTags(
            title = "Quantum Echoes",
            artist = "Synthesizer Pilot",
            album = "Cosmic Odyssey",
            albumArtist = "Synthesizer Pilot",
            year = 2024,
            genre = "Synthwave",
            trackNumber = 4,
            discNumber = 1,
            lyrics = "Look at the stars shining bright"
        )

        val writeResult = TrackTagService.writeTags(testFile, newTags)
        assertTrue(writeResult is TagWriteResult.Success)

        val readBack = TrackTagService.readTags(testFile)
        assertNotNull(readBack)
        assertEquals("Quantum Echoes", readBack.title)
        assertEquals("Synthesizer Pilot", readBack.artist)
        assertEquals("Cosmic Odyssey", readBack.album)
        assertEquals(2024, readBack.year)
        assertEquals("Synthwave", readBack.genre)
        assertEquals(4, readBack.trackNumber)
        assertEquals(1, readBack.discNumber)
        assertEquals("Look at the stars shining bright", readBack.lyrics)
    }

    @Test
    fun embedsArtworkSuccessfully() {
        val samplePng = Path.of("src/test/resources/metadata/embedded-cover.png")
        if (Files.exists(samplePng)) {
            val bytes = Files.readAllBytes(samplePng)
            val embedded = TrackTagService.embedArtwork(testFile, bytes, "image/png")
            assertTrue(embedded)
        }
    }

    @Test
    fun handlesCorruptedOrUnwritableFileWithoutTruncation() {
        val dummyCorrupted = tempDir.resolve("corrupted.mp3")
        try {
            Files.writeString(dummyCorrupted, "not a valid audio stream header")
            val sizeBefore = Files.size(dummyCorrupted)
            assertTrue(sizeBefore > 0L)

            val result = TrackTagService.writeTags(dummyCorrupted, EditableTags("New Title"))
            assertTrue(result is TagWriteResult.Error)
            // File must not be wiped to 0 bytes
            assertTrue(Files.exists(dummyCorrupted))
            assertEquals(sizeBefore, Files.size(dummyCorrupted))
            assertEquals("not a valid audio stream header", Files.readString(dummyCorrupted))
        } finally {
            Files.deleteIfExists(dummyCorrupted)
        }
    }
}

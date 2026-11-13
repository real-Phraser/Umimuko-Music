package com.lostf1sh.pixelplayeross.desktop.library

import java.nio.file.Files
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FolderScannerTest {
    @Test
    fun findsNestedAudioCaseInsensitivelyAndPreservesDuplicateNames() {
        val root = Files.createTempDirectory("pixel-scanner")
        try {
            val nested = Files.createDirectory(root.resolve("nested"))
            Files.write(root.resolve("Track.MP3"), byteArrayOf(1, 2, 3))
            Files.write(nested.resolve("Track.mp3"), byteArrayOf(4))
            Files.write(nested.resolve("آهنگ.flac"), byteArrayOf(5))
            Files.write(root.resolve("notes.txt"), byteArrayOf())
            Files.createDirectory(root.resolve("fake.wav"))
            val result = FolderScanner().scan(root)
            assertEquals(3, result.songs.size)
            assertEquals(3, result.songs.map { it.id }.toSet().size)
            assertEquals(setOf("MP3", "FLAC"), result.songs.map { it.format }.toSet())
            assertEquals(3L, result.songs.single { it.path.endsWith("Track.MP3") }.sizeBytes)
            assertEquals(0, result.unreadableEntries)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun handlesEmptyFolderAndRejectsFileAsRoot() {
        val root = Files.createTempDirectory("pixel-empty")
        try {
            assertEquals(emptyList(), FolderScanner().scan(root).songs)
            val file = Files.createFile(root.resolve("song.mp3"))
            assertFailsWith<IllegalArgumentException> { FolderScanner().scan(file) }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun propagatesCancellationWithoutReturningPartialResults() {
        val root = Files.createTempDirectory("pixel-cancel")
        try {
            assertFailsWith<CancellationException> {
                FolderScanner().scan(root) { throw CancellationException("Cancelled") }
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}

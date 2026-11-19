package com.lostf1sh.pixelplayeross.desktop.metadata

import java.nio.file.Files
import kotlin.test.*

class CoverArtDownloaderTest {

    private val tempDir = Files.createTempDirectory("cover-test-")

    @AfterTest
    fun cleanup() {
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun savesAsFolderCover() {
        val sampleBytes = ByteArray(1024) { 0x42 }
        val savedPath = CoverArtDownloader.saveAsFolderCover(tempDir, sampleBytes, "cover.jpg")
        assertNotNull(savedPath)
        assertTrue(Files.exists(savedPath))
        assertEquals(1024L, Files.size(savedPath))
    }

    @Test
    fun cachesCoverImageSuccessfully() {
        val sampleBytes = ByteArray(2048) { 0x11 }
        val cachedPath = CoverArtDownloader.cacheCoverImage(sampleBytes, "test_album_key_123")
        assertNotNull(cachedPath)
        assertTrue(Files.exists(cachedPath))
        assertEquals(2048L, Files.size(cachedPath))
        Files.deleteIfExists(cachedPath)
    }

    @Test
    fun liveArtFetchingReturnsHighResCover() {
        val bytes = CoverArtDownloader.searchAndDownloadCover("Radiohead", "OK Computer", "")
        assertNotNull(bytes, "Expected cover art bytes from live provider")
        assertTrue(bytes.size > 10000, "Expected non-trivial image byte size (got ${bytes.size} bytes)")
    }
}

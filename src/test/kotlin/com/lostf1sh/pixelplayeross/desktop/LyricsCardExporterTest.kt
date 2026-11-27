package com.lostf1sh.pixelplayeross.desktop

import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LyricsCardExporterTest {
    @Test
    fun exportsPngWithCardContents() {
        val destination = Files.createTempFile("umimuko-lyric-card", ".png").toFile()
        try {
            val coverPath = Path.of(javaClass.getResource("/metadata/embedded-cover.png")!!.toURI()).toString()
            LyricsCardExporter.export(
                DesktopSong(
                    id = "track",
                    title = "A song",
                    path = "",
                    format = "mp3",
                    sizeBytes = 0,
                    artist = "An artist",
                    coverPath = coverPath
                ),
                "A line selected\nAnother selected line",
                destination
            )

            val image = ImageIO.read(destination)
            assertNotNull(image)
            assertEquals(1200, image.width)
            assertTrue(image.height >= 1350)
            assertTrue(destination.length() > 0)
        } finally {
            Files.deleteIfExists(destination.toPath())
        }
    }

    @Test
    fun requiresSelectedLyrics() {
        val destination = Files.createTempFile("umimuko-lyric-card", ".png").toFile()
        try {
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                LyricsCardExporter.export(
                    DesktopSong(id = "track", title = "A song", path = "", format = "mp3", sizeBytes = 0),
                    " \n ",
                    destination
                )
            }
        } finally {
            Files.deleteIfExists(destination.toPath())
        }
    }
}

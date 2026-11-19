package com.lostf1sh.pixelplayeross.desktop

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrandingTest {

    @Test
    fun testWindowIconResourceMatchesOfficialPackaging() {
        val stream: InputStream? = javaClass.classLoader.getResourceAsStream("branding/window-icon.png")
        assertNotNull(stream, "branding/window-icon.png must be present on the classpath")

        val resourceBytes = stream.use { it.readAllBytes() }
        val packagingPath = Path.of("packaging", "umimuko.png")
        assertTrue(Files.isRegularFile(packagingPath), "packaging/umimuko.png must exist on disk")

        val packagingBytes = Files.readAllBytes(packagingPath)
        assertEquals(packagingBytes.size, resourceBytes.size, "window-icon.png size must match packaging/umimuko.png")

        fun sha256(data: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(data).joinToString("") { "%02x".format(it) }
        }

        assertEquals(sha256(packagingBytes), sha256(resourceBytes), "SHA-256 checksums must match")
    }

    @Test
    fun testInAppIconResourceExistsAndIsTransparentMark() {
        val stream = javaClass.classLoader.getResourceAsStream("branding/app-icon.png")
        assertNotNull(stream, "branding/app-icon.png must be present for in-app logo")
        val image: BufferedImage? = stream.use { ImageIO.read(it) }
        assertNotNull(image, "Failed to decode branding/app-icon.png")

        assertEquals(512, image.width)
        assertEquals(512, image.height)
        assertTrue(image.colorModel.hasAlpha(), "In-app logo must have transparency for dynamic surface tinting")
    }

    @Test
    fun testWindowIconDimensionsAndColorContent() {
        val stream = javaClass.classLoader.getResourceAsStream("branding/window-icon.png")
        assertNotNull(stream)
        val image: BufferedImage? = stream.use { ImageIO.read(it) }
        assertNotNull(image, "Failed to decode window-icon.png")

        assertEquals(512, image.width, "Window icon width should be 512")
        assertEquals(512, image.height, "Window icon height should be 512")

        var nonMonochromePixels = 0
        val sampleStep = 8
        for (y in 0 until image.height step sampleStep) {
            for (x in 0 until image.width step sampleStep) {
                val argb = image.getRGB(x, y)
                val alpha = (argb ushr 24) and 0xFF
                if (alpha > 50) {
                    val red = (argb ushr 16) and 0xFF
                    val green = (argb ushr 8) and 0xFF
                    val blue = argb and 0xFF
                    if (red != green || green != blue) {
                        nonMonochromePixels++
                    }
                }
            }
        }
        assertTrue(nonMonochromePixels > 10, "Window icon should contain rich non-monochrome branding colors")
    }

    @Test
    fun testMultiScaleIconGeneration() {
        val stream = javaClass.classLoader.getResourceAsStream("branding/window-icon.png")
        assertNotNull(stream)
        val base = stream.use { ImageIO.read(it) }
        assertNotNull(base)

        val targetSizes = intArrayOf(16, 20, 24, 32, 40, 48, 64, 96, 128, 256)
        for (size in targetSizes) {
            val scaled = scaleIcon(base, size)
            assertEquals(size, scaled.width)
            assertEquals(size, scaled.height)
            assertEquals(BufferedImage.TYPE_INT_ARGB, scaled.type)

            // Ensure scaled icon has visible non-transparent content
            var visiblePixels = 0
            for (y in 0 until scaled.height) {
                for (x in 0 until scaled.width) {
                    val alpha = (scaled.getRGB(x, y) ushr 24) and 0xFF
                    if (alpha > 10) visiblePixels++
                }
            }
            assertTrue(visiblePixels > 0, "Scaled icon $size x $size should contain non-transparent content")
        }
    }

    @Test
    fun testPlatformPackagingAssetsExist() {
        val ico = Path.of("packaging", "umimuko.ico")
        assertTrue(Files.isRegularFile(ico) && Files.size(ico) > 1000, "packaging/umimuko.ico must exist and be valid")

        val icns = Path.of("packaging", "umimuko.icns")
        assertTrue(Files.isRegularFile(icns) && Files.size(icns) > 1000, "packaging/umimuko.icns must exist and be valid")
    }

    private fun scaleIcon(src: BufferedImage, targetSize: Int): BufferedImage {
        var current = src
        var currentW = src.width
        var currentH = src.height

        while (currentW > targetSize * 2 || currentH > targetSize * 2) {
            val nextW = maxOf(targetSize, currentW / 2)
            val nextH = maxOf(targetSize, currentH / 2)
            val step = BufferedImage(nextW, nextH, BufferedImage.TYPE_INT_ARGB)
            val g = step.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.drawImage(current, 0, 0, nextW, nextH, null)
            } finally {
                g.dispose()
            }
            current = step
            currentW = nextW
            currentH = nextH
        }

        val finalImg = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB)
        val g = finalImg.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(current, 0, 0, targetSize, targetSize, null)
        } finally {
            g.dispose()
        }
        return finalImg
    }
}

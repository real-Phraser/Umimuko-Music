package com.lostf1sh.pixelplayeross.desktop

import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

internal object LyricsCardExporter {
    fun export(song: DesktopSong, lyrics: String, destination: File) {
        require(lyrics.isNotBlank()) { "Select at least one lyric line." }
        val image = render(song, lyrics)
        if (!ImageIO.write(image, "png", destination)) {
            throw IOException("PNG export is not supported.")
        }
    }

    private fun render(song: DesktopSong, lyrics: String): BufferedImage {
        val lyricFont = Font("SansSerif", Font.BOLD, 44)
        val measure = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val measureGraphics = measure.createGraphics()
        measureGraphics.font = lyricFont
        val lyricLines = try {
            wrapText(lyrics, measureGraphics.fontMetrics, 1040)
        } finally {
            measureGraphics.dispose()
        }
        val lyricFontSize = when {
            lyricLines.size <= 4 -> 42
            lyricLines.size <= 8 -> 40
            lyricLines.size <= 14 -> 34
            else -> 28
        }
        val lineHeight = lyricFontSize + 22
        val height = maxOf(1350, 410 + lyricLines.size * (lineHeight + 18) + 230)
        val image = BufferedImage(1200, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.paint = GradientPaint(0f, 0f, Color(54, 18, 19), 1200f, height.toFloat(), Color(20, 14, 22))
            graphics.fillRect(0, 0, image.width, image.height)

            val cover = song.coverPath?.let { path ->
                val file = File(path)
                if (file.isFile) {
                    ImageIO.read(file) ?: throw IOException("The track cover could not be read.")
                } else {
                    null
                }
            }
            if (cover != null) {
                val blurredCover = createBlurredCover(cover, image.width, image.height)
                graphics.drawImage(blurredCover, 0, 0, image.width, image.height, null)
                graphics.paint = GradientPaint(
                    0f, 0f, Color(48, 12, 14, 170),
                    0f, height.toFloat(), Color(18, 14, 22, 185)
                )
                graphics.fillRect(0, 0, image.width, image.height)
            }

            if (cover != null) {
                val oldClip = graphics.clip
                graphics.clip = java.awt.geom.RoundRectangle2D.Float(76f, 76f, 170f, 170f, 24f, 24f)
                drawCoverCrop(graphics, cover, 76, 76, 170, 170)
                graphics.clip = oldClip
            } else {
                graphics.color = Color(255, 255, 255, 22)
                graphics.fillRoundRect(76, 76, 170, 170, 24, 24)
                graphics.font = Font("SansSerif", Font.PLAIN, 90)
                graphics.color = Color.WHITE
                drawCentered(graphics, "♪", 161, 188)
            }

            graphics.color = Color.WHITE
            drawFittedText(graphics, song.title.ifBlank { "Unknown title" }, 284, 140, 830, 50, 30)
            graphics.color = Color(255, 255, 255, 190)
            drawFittedText(graphics, song.artist.ifBlank { "Unknown artist" }, 284, 190, 830, 32, 20)

            graphics.font = Font("SansSerif", Font.BOLD, lyricFontSize)
            graphics.color = Color.WHITE
            var y = 340
            lyricLines.forEach { line ->
                graphics.drawString(line, 76, y)
                y += lineHeight + 18
            }

            val logoStream = LyricsCardExporter::class.java.classLoader
                .getResourceAsStream("branding/app-icon.png")
                ?: throw IOException("The Umimuko logo resource is missing.")
            val logo = logoStream.use(ImageIO::read)
                ?: throw IOException("The Umimuko logo could not be read.")
            graphics.drawImage(logo, 76, height - 112, 64, 64, null)
            graphics.font = Font("SansSerif", Font.BOLD, 24)
            graphics.color = Color(255, 255, 255, 210)
            graphics.drawString("UMIMUKO MUSIC", 154, height - 72)
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun createBlurredCover(cover: BufferedImage, width: Int, height: Int): BufferedImage {
        val smallWidth = 160
        val smallHeight = (height * smallWidth / width).coerceAtLeast(1)
        val small = BufferedImage(smallWidth, smallHeight, BufferedImage.TYPE_INT_RGB)
        val smallGraphics = small.createGraphics()
        try {
            smallGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawCoverCrop(smallGraphics, cover, 0, 0, smallWidth, smallHeight)
        } finally {
            smallGraphics.dispose()
        }

        val radius = 4
        val sigma = 1.8
        val kernelSize = radius * 2 + 1
        val kernel = FloatArray(kernelSize * kernelSize)
        var total = 0.0
        for (row in -radius..radius) {
            for (column in -radius..radius) {
                val weight = kotlin.math.exp(-(column * column + row * row) / (2 * sigma * sigma))
                kernel[(row + radius) * kernelSize + column + radius] = weight.toFloat()
                total += weight
            }
        }
        for (index in kernel.indices) kernel[index] = (kernel[index] / total).toFloat()
        val blurred = ConvolveOp(Kernel(kernelSize, kernelSize, kernel), ConvolveOp.EDGE_NO_OP, null).filter(small, null)
        return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { expanded ->
            val expandedGraphics = expanded.createGraphics()
            try {
                expandedGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                expandedGraphics.drawImage(blurred, 0, 0, width, height, null)
            } finally {
                expandedGraphics.dispose()
            }
        }
    }

    private fun drawCoverCrop(
        graphics: java.awt.Graphics2D,
        cover: BufferedImage,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        val targetRatio = width.toDouble() / height
        val coverRatio = cover.width.toDouble() / cover.height
        val sourceWidth: Int
        val sourceHeight: Int
        val sourceX: Int
        val sourceY: Int
        if (coverRatio > targetRatio) {
            sourceHeight = cover.height
            sourceWidth = (cover.height * targetRatio).toInt()
            sourceX = (cover.width - sourceWidth) / 2
            sourceY = 0
        } else {
            sourceWidth = cover.width
            sourceHeight = (cover.width / targetRatio).toInt()
            sourceX = 0
            sourceY = (cover.height - sourceHeight) / 2
        }
        graphics.drawImage(
            cover,
            x, y, x + width, y + height,
            sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight,
            null
        )
    }

    private fun drawFittedText(
        graphics: java.awt.Graphics2D,
        text: String,
        x: Int,
        baseline: Int,
        maxWidth: Int,
        initialSize: Int,
        minimumSize: Int
    ) {
        var size = initialSize
        do {
            graphics.font = Font("SansSerif", Font.BOLD, size)
            size--
        } while (graphics.fontMetrics.stringWidth(text) > maxWidth && size >= minimumSize)
        graphics.font = Font("SansSerif", Font.BOLD, size.coerceAtLeast(minimumSize))
        val shownText = ellipsize(text, graphics.fontMetrics, maxWidth)
        graphics.drawString(shownText, x, baseline)
    }

    private fun ellipsize(text: String, metrics: java.awt.FontMetrics, maxWidth: Int): String {
        if (metrics.stringWidth(text) <= maxWidth) return text
        var shortened = text
        while (shortened.isNotEmpty() && metrics.stringWidth("$shortened…") > maxWidth) {
            shortened = shortened.dropLast(1)
        }
        return "$shortened…"
    }

    private fun drawCentered(graphics: java.awt.Graphics2D, text: String, centerX: Int, baseline: Int) {
        graphics.drawString(text, centerX - graphics.fontMetrics.stringWidth(text) / 2, baseline)
    }

    private fun wrapText(text: String, metrics: java.awt.FontMetrics, maxWidth: Int): List<String> =
        text.lineSequence().flatMap { paragraph ->
            val words = paragraph.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
            if (words.isEmpty()) {
                sequenceOf("")
            } else {
                sequence {
                    var current = ""
                    for (word in words) {
                        if (metrics.stringWidth(word) > maxWidth) {
                            if (current.isNotEmpty()) {
                                yield(current)
                                current = ""
                            }
                            var chunk = ""
                            for (character in word) {
                                val candidate = chunk + character
                                if (metrics.stringWidth(candidate) > maxWidth && chunk.isNotEmpty()) {
                                    yield(chunk)
                                    chunk = character.toString()
                                } else {
                                    chunk = candidate
                                }
                            }
                            current = chunk
                            continue
                        }
                        val candidate = if (current.isEmpty()) word else "$current $word"
                        if (metrics.stringWidth(candidate) <= maxWidth) {
                            current = candidate
                        } else {
                            if (current.isNotEmpty()) yield(current)
                            current = word
                        }
                    }
                    if (current.isNotEmpty()) yield(current)
                }
            }
        }.toList()
}

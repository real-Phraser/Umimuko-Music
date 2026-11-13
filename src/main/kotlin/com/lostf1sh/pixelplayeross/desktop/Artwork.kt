package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

@Composable
fun Artwork(size: Dp, active: Boolean = false, coverPath: String? = null) {
    val bitmap by produceState<ImageBitmap?>(null, coverPath) {
        value = null
        if (coverPath != null) value = withContext(Dispatchers.IO) {
            try {
                org.jetbrains.skia.Image.makeFromEncoded(Files.readAllBytes(Path.of(coverPath))).toComposeImageBitmap()
            } catch (_: Exception) { null }
        }
    }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4))
        .background(if (active) colors.primary.copy(alpha = .18f) else colors.surfaceVariant),
        contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) Image(loaded, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High)
        else Glyph("music", if (active) colors.primary else colors.onSurfaceVariant)
    }
}

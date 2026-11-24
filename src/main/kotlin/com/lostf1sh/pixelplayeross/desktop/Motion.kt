package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image as SkiaImage
import java.util.prefs.Preferences
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths

/** Material 3 "emphasized decelerate": quick start, long soft landing. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

/** True while the startup animation covers the window, so Home waits to animate in until it is gone. */
object IntroState {
    /** True while the startup cover is on screen. */
    val active = mutableStateOf(false)
    /** True from launch until Home has finished its "now playing" moment (or decided to skip it). */
    val heroPending = mutableStateOf(false)
}

/** Tiny persisted settings; java.util.prefs needs no files of our own. */
object StartupPrefs {
    private val node: Preferences = Preferences.userRoot().node("umimuko-music")
    var introEnabled: Boolean
        get() = try { node.getBoolean("startupAnimation", true) } catch (e: Exception) { true }
        set(value) { try { node.putBoolean("startupAnimation", value) } catch (e: Exception) {} }

    val followSystemThemeFlow = MutableStateFlow(try { node.getBoolean("followSystemTheme", true) } catch (e: Exception) { true })

    var followSystemTheme: Boolean
        get() = try { node.getBoolean("followSystemTheme", true) } catch (e: Exception) { true }
        set(value) {
            try { node.putBoolean("followSystemTheme", value) } catch (e: Exception) {}
            followSystemThemeFlow.value = value
        }
    var darkMode: Boolean
        get() = try { node.getBoolean("darkMode", true) } catch (e: Exception) { true }
        set(value) { try { node.putBoolean("darkMode", value) } catch (e: Exception) {} }
    var windowMaximized: Boolean
        get() = try { node.getBoolean("windowMaximized", !PlatformPaths.isMac()) } catch (e: Exception) { !PlatformPaths.isMac() }
        set(value) { try { node.putBoolean("windowMaximized", value) } catch (e: Exception) {} }
    var windowWidth: Int
        get() = try { node.getInt("windowWidth", 1180) } catch (e: Exception) { 1180 }
        set(value) { try { node.putInt("windowWidth", value) } catch (e: Exception) {} }
    var windowHeight: Int
        get() = try { node.getInt("windowHeight", 800) } catch (e: Exception) { 800 }
        set(value) { try { node.putInt("windowHeight", value) } catch (e: Exception) {} }
    var themePreset: String
        get() = try { node.get("themePreset", "dynamic") } catch (e: Exception) { "dynamic" }
        set(value) { try { node.put("themePreset", value) } catch (e: Exception) {} }
    var minimizeToTray: Boolean
        get() = try { node.getBoolean("minimizeToTray", false) } catch (e: Exception) { false }
        set(value) { try { node.putBoolean("minimizeToTray", value) } catch (e: Exception) {} }
    var closeToTray: Boolean
        get() = try { node.getBoolean("closeToTray", false) } catch (e: Exception) { false }
        set(value) { try { node.putBoolean("closeToTray", value) } catch (e: Exception) {} }

    // Audio DSP & Equalizer Persistence
    var eqEnabled: Boolean
        get() = try { node.getBoolean("eqEnabled", false) } catch (e: Exception) { false }
        set(value) { try { node.putBoolean("eqEnabled", value) } catch (e: Exception) {} }

    var eqPreset: String
        get() = try { node.get("eqPreset", "Flat") } catch (e: Exception) { "Flat" }
        set(value) { try { node.put("eqPreset", value) } catch (e: Exception) {} }

    var eqPreamp: Float
        get() = try { node.getFloat("eqPreamp", 0.0f) } catch (e: Exception) { 0.0f }
        set(value) { try { node.putFloat("eqPreamp", value) } catch (e: Exception) {} }

    var eqCustomBands: String
        get() = try { node.get("eqCustomBands", "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0") } catch (e: Exception) { "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0" }
        set(value) { try { node.put("eqCustomBands", value) } catch (e: Exception) {} }

    var playbackSpeed: Float
        get() = try { node.getFloat("playbackSpeed", 1.0f) } catch (e: Exception) { 1.0f }
        set(value) { try { node.putFloat("playbackSpeed", value) } catch (e: Exception) {} }

    var crossfadeDuration: Float
        get() = try { node.getFloat("crossfadeDuration", 0.0f) } catch (e: Exception) { 0.0f }
        set(value) { try { node.putFloat("crossfadeDuration", value) } catch (e: Exception) {} }
}

/**
 * Fade + small rise, started [delayMs] after the startup sequence ends. Runs once per [key] (state survives lazy-list scrolling),
 * and only touches the draw layer, so it never causes relayout.
 */
@Composable
fun Modifier.entrance(key: Any, delayMs: Long, animate: Boolean = true, durationMs: Int = 420, rise: Dp = 18.dp): Modifier {
    var played by rememberSaveable(key) { mutableStateOf(!animate) }
    val progress = remember(key) { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!played) {
            snapshotFlow { IntroState.active.value || IntroState.heroPending.value }.first { !it }
            delay(delayMs.coerceIn(0L, 700L))
            progress.animateTo(1f, tween(durationMs, easing = EmphasizedDecelerate))
            played = true
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * rise.toPx()
    }
}

private const val STARTUP_INTRO_RESOURCE = "branding/startup-intro.webp"
private const val DEFAULT_FRAME_DURATION_MS = 17

private fun loadStartupIntroBytes(): ByteArray? {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(STARTUP_INTRO_RESOURCE)
        ?: IntroState::class.java.classLoader?.getResourceAsStream(STARTUP_INTRO_RESOURCE)
    return stream?.use { it.readBytes() }
}

/**
 * Startup animation played from `branding/startup-intro.webp` via Skia's animated WebP [Codec],
 * followed by a smooth fade-out into the main window.
 */
@Composable
fun StartupIntro(onFinished: () -> Unit) {
    val cover = remember { Animatable(1f) }
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        try {
            val bytes = withContext(Dispatchers.IO) { loadStartupIntroBytes() }
            if (bytes != null && bytes.isNotEmpty()) {
                Data.makeFromBytes(bytes).use { data ->
                    Codec.makeFromData(data).use { codec ->
                        val frameCount = codec.frameCount.coerceAtLeast(1)
                        val framesInfo = codec.framesInfo
                        val endTimesMs = LongArray(frameCount)
                        var totalMs = 0L
                        for (i in 0 until frameCount) {
                            val d = framesInfo.getOrNull(i)?.duration ?: DEFAULT_FRAME_DURATION_MS
                            totalMs += if (d > 0) d.toLong() else DEFAULT_FRAME_DURATION_MS.toLong()
                            endTimesMs[i] = totalMs
                        }

                        Bitmap().use { workingBitmap ->
                            workingBitmap.allocPixels(codec.imageInfo)
                            var decodedIdx = 0
                            var lastDecoded = -1

                            suspend fun decodeTo(targetIdx: Int): ImageBitmap = withContext(Dispatchers.Default) {
                                while (decodedIdx < targetIdx) {
                                    decodedIdx++
                                    val req = framesInfo.getOrNull(decodedIdx)?.requiredFrame ?: -1
                                    val prior = if (req >= 0 && lastDecoded >= 0) lastDecoded else -1
                                    codec.readPixels(workingBitmap, decodedIdx, prior)
                                    lastDecoded = decodedIdx
                                }
                                SkiaImage.makeFromBitmap(workingBitmap).use { it.toComposeImageBitmap() }
                            }

                            currentFrame = withContext(Dispatchers.Default) {
                                codec.readPixels(workingBitmap, 0)
                                lastDecoded = 0
                                SkiaImage.makeFromBitmap(workingBitmap).use { it.toComposeImageBitmap() }
                            }

                            if (frameCount > 1) {
                                var startNanos = -1L
                                var targetIdx = 0
                                while (true) {
                                    val frameTimeNanos = withFrameNanos { it }
                                    if (startNanos < 0L) startNanos = frameTimeNanos
                                    val elapsedMs = (frameTimeNanos - startNanos) / 1_000_000L
                                    if (elapsedMs >= totalMs) {
                                        if (decodedIdx < frameCount - 1) {
                                            currentFrame = decodeTo(frameCount - 1)
                                        }
                                        break
                                    }
                                    while (targetIdx < frameCount - 1 && elapsedMs >= endTimesMs[targetIdx]) {
                                        targetIdx++
                                    }
                                    if (targetIdx > decodedIdx) {
                                        currentFrame = decodeTo(targetIdx)
                                    }
                                }
                            } else {
                                delay(totalMs)
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Fall through to fade-out and onFinished so startup is never blocked.
        }
        cover.animateTo(0f, tween(260))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = cover.value }
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        currentFrame?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

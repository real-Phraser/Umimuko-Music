package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lostf1sh.pixelplayeross.desktop.library.ArtistNames
import com.lostf1sh.pixelplayeross.desktop.lyrics.SpicyLyrics
import com.lostf1sh.pixelplayeross.desktop.lyrics.SpicyLyricsService
import com.lostf1sh.pixelplayeross.desktop.lyrics.SpicyLyricsView
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import com.lostf1sh.pixelplayeross.desktop.playback.RepeatMode
import com.lostf1sh.pixelplayeross.desktop.theme.FullPlayerAmbientColors
import com.lostf1sh.pixelplayeross.desktop.theme.ThemeManager
import com.lostf1sh.pixelplayeross.desktop.theme.ThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

@Composable
fun FullPlayerScreen(
    audio: AudioController,
    liked: Boolean,
    editable: Boolean,
    onLike: () -> Unit,
    onAdd: () -> Unit,
    onQueue: () -> Unit,
    onSleepTimer: () -> Unit = {},
    sleepTimerState: com.lostf1sh.pixelplayeross.desktop.system.SleepTimerState? = null,
    onOpenSettings: (() -> Unit)? = null,
    onExit: () -> Unit
) {
    val state by audio.state.collectAsState()
    var preview by remember(state.song?.id) { mutableStateOf<Float?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var showLyricCardDialog by remember(state.song?.id) { mutableStateOf(false) }

    // Lyrics state
    var lyrics by remember(state.song?.id) { mutableStateOf(SpicyLyrics.EMPTY) }
    var isLoadingLyrics by remember(state.song?.id) { mutableStateOf(false) }

    LaunchedEffect(state.song?.id) {
        val song = state.song
        if (song != null) {
            isLoadingLyrics = true
            val fetched = withContext(Dispatchers.IO) {
                SpicyLyricsService.getLyrics(song)
            }
            lyrics = fetched
            isLoadingLyrics = false
        } else {
            lyrics = SpicyLyrics.EMPTY
        }
    }

    val currentPreset by ThemeManager.currentPreset.collectAsState()
    val ambientColors = remember(currentPreset, state.song?.accentArgb) {
        ThemeManager.resolveFullPlayerAmbientColors(state.song?.accentArgb, currentPreset)
    }

    val fullColors = remember(currentPreset, state.song?.accentArgb, ambientColors) {
        resolveFullPlayerContrastColors(
            baseScheme = ThemeManager.resolveFullPlayerColorScheme(state.song?.accentArgb),
            ambientColors = ambientColors
        )
    }

    MaterialTheme(colorScheme = fullColors, typography = MaterialTheme.typography) {
        val colors = MaterialTheme.colorScheme
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            Box(Modifier.fillMaxSize()) {
                FullPlayerAmbientBackground(
                    ambientColors = ambientColors,
                    secondaryColor = fullColors.secondary,
                    tertiaryColor = fullColors.tertiary,
                    isDynamicPreset = currentPreset == ThemePreset.DYNAMIC,
                    coverPath = state.song?.coverPath
                )
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .padding(28.dp)
                ) {
                val isWide = maxWidth >= 840.dp

                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Header Bar
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "NOW PLAYING",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.weight(1f))

                        // Lyrics Toggle Button
                        IconButton(
                            onClick = { showLyrics = !showLyrics },
                            modifier = Modifier.semantics { contentDescription = "Toggle lyrics" }
                        ) {
                            Glyph(
                                "lyrics",
                                if (showLyrics) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        TextButton(
                            onClick = { showLyricCardDialog = true },
                            enabled = state.song != null && !isLoadingLyrics && lyrics.lines.isNotEmpty()
                        ) {
                            Text("Lyric card")
                        }

                        Spacer(Modifier.width(8.dp))

                        TextButton(
                            onClick = onExit,
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)
                        ) {
                            Glyph("collapse", LocalContentColor.current)
                            Spacer(Modifier.width(8.dp))
                            Text("Back")
                        }
                    }

                    // Content Area: Split View when lyrics enabled and wide screen
                    if (showLyrics && isWide) {
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Pane: Artwork & Transport Controls
                            Column(
                                Modifier.weight(1.1f).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                BoxWithConstraints(
                                    Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val artSize = minOf(maxWidth * 0.85f, maxHeight - 12.dp, 360.dp).coerceAtLeast(100.dp)
                                    Artwork(artSize, state.song != null, state.song?.coverPath)
                                }

                                Spacer(Modifier.height(14.dp))

                                PlayerMetadataSection(state.song, colors)

                                Spacer(Modifier.height(14.dp))

                                PlayerScrubberAndControls(
                                    audio = audio,
                                    state = state,
                                    preview = preview,
                                    liked = liked,
                                    editable = editable,
                                    onPreviewChange = { preview = it },
                                    onLike = onLike,
                                    onAdd = onAdd,
                                    onQueue = onQueue,
                                    onSleepTimer = onSleepTimer,
                                    sleepTimerState = sleepTimerState,
                                    colors = colors
                                )
                            }

                            // Right Pane: Spacious Spicy Lyrics Canvas
                            Box(
                                Modifier
                                    .weight(1.3f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 8.dp)
                            ) {
                                LyricsPaneContent(
                                    lyrics = lyrics,
                                    currentTimeMs = state.time,
                                    isPlaying = state.playing,
                                    playbackRate = state.rate,
                                    isLoading = isLoadingLyrics,
                                    fadeColor = ambientColors.deepBackground,
                                    onBackgroundColor = colors.onBackground,
                                    accentColor = colors.primary,
                                    onSeek = { seekMs ->
                                        if (state.duration > 0) {
                                            audio.seek(seekMs.toFloat() / state.duration.toFloat())
                                        }
                                    },
                                    onOpenSettings = onOpenSettings
                                )
                            }
                        }
                    } else if (showLyrics && !isWide) {
                        // Narrow screen with lyrics: spacious lyrics canvas with overlay controls
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            LyricsPaneContent(
                                lyrics = lyrics,
                                currentTimeMs = state.time,
                                isPlaying = state.playing,
                                playbackRate = state.rate,
                                isLoading = isLoadingLyrics,
                                fadeColor = ambientColors.deepBackground,
                                onBackgroundColor = colors.onBackground,
                                accentColor = colors.primary,
                                onSeek = { seekMs ->
                                    if (state.duration > 0) {
                                        audio.seek(seekMs.toFloat() / state.duration.toFloat())
                                    }
                                },
                                onOpenSettings = onOpenSettings
                            )
                        }

                        PlayerScrubberAndControls(
                            audio = audio,
                            state = state,
                            preview = preview,
                            liked = liked,
                            editable = editable,
                            onPreviewChange = { preview = it },
                            onLike = onLike,
                            onAdd = onAdd,
                            onQueue = onQueue,
                            onSleepTimer = onSleepTimer,
                            sleepTimerState = sleepTimerState,
                            colors = colors
                        )
                    } else {
                        // Standard Fullscreen View (Artwork centered)
                        BoxWithConstraints(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val artSize = minOf(maxWidth * 0.6f, maxHeight - 8.dp, 440.dp).coerceAtLeast(100.dp)
                            Artwork(artSize, state.song != null, state.song?.coverPath)
                        }

                        PlayerMetadataSection(state.song, colors)

                        Column(
                            Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                        ) {
                            PlayerScrubberAndControls(
                                audio = audio,
                                state = state,
                                preview = preview,
                                liked = liked,
                                editable = editable,
                                onPreviewChange = { preview = it },
                                onLike = onLike,
                                onAdd = onAdd,
                                onQueue = onQueue,
                                onSleepTimer = onSleepTimer,
                                sleepTimerState = sleepTimerState,
                                colors = colors
                            )
                        }
                    }
                }
                if (showLyricCardDialog) {
                    state.song?.let { song ->
                        LyricsCardDialog(
                            song = song,
                            lyrics = lyrics,
                            onDismiss = { showLyricCardDialog = false }
                        )
                    }
                }
            }
        }
    }
}
}

/**
 * Renders an organic, multi-layered ambient flow background for the fullscreen player.
 * Combines a heavily blurred, slowly drifting album-cover texture (in dynamic mode) with
 * overlapping, stretched elliptical color washes so there is no single circular spotlight.
 */
@Composable
private fun FullPlayerAmbientBackground(
    ambientColors: FullPlayerAmbientColors,
    secondaryColor: Color,
    tertiaryColor: Color,
    isDynamicPreset: Boolean,
    coverPath: String?
) {
    val deepBg by animateColorAsState(ambientColors.deepBackground, tween(900))
    val glowBase by animateColorAsState(ambientColors.glowBase, tween(900))
    val glowAccent by animateColorAsState(ambientColors.glowAccent, tween(900))
    val secondary by animateColorAsState(secondaryColor, tween(900))
    val tertiary by animateColorAsState(tertiaryColor, tween(900))

    val coverBitmap by produceState<ImageBitmap?>(null, coverPath, isDynamicPreset) {
        value = null
        if (isDynamicPreset && coverPath != null) {
            value = withContext(Dispatchers.IO) {
                try {
                    org.jetbrains.skia.Image.makeFromEncoded(Files.readAllBytes(Path.of(coverPath))).toComposeImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val driftPrimary by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(13000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    val driftSecondary by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(17000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    val driftTertiary by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(21000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(deepBg)
            .clipToBounds()
    ) {
        // Multi-stop diagonal base wash
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        lerp(deepBg, glowBase, 0.75f),
                        deepBg,
                        lerp(deepBg, glowAccent, 0.16f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
        }

        // Blurred, zoomed, slowly drifting album-cover texture in Dynamic mode
        val loadedCover = coverBitmap
        if (isDynamicPreset && loadedCover != null) {
            Image(
                bitmap = loadedCover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.65f + driftPrimary * 0.14f
                        scaleY = 1.65f + driftSecondary * 0.14f
                        translationX = (driftPrimary - 0.5f) * 80f
                        translationY = (driftSecondary - 0.5f) * 60f
                        rotationZ = (driftTertiary - 0.5f) * 14f
                        alpha = 0.28f
                    }
                    .blur(96.dp, BlurredEdgeTreatment.Unbounded)
            )
            // Protective scrim to preserve contrast-ratio guarantees on bright covers
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                deepBg.copy(alpha = 0.52f),
                                deepBg.copy(alpha = 0.36f),
                                deepBg.copy(alpha = 0.58f)
                            )
                        )
                    )
            )
        }

        // Overlapping, stretched elliptical color washes
        Canvas(Modifier.fillMaxSize()) {
            val maxDim = maxOf(size.width, size.height).coerceAtLeast(1f)
            val primaryWash = lerp(glowBase, glowAccent, 0.24f + driftPrimary * 0.12f)
            val secondaryWash = lerp(glowBase, secondary, 0.22f + driftSecondary * 0.10f)
            val tertiaryWash = lerp(deepBg, tertiary, 0.20f + driftTertiary * 0.10f)
            val bridgeWash = lerp(glowBase, glowAccent, 0.18f + (1f - driftPrimary) * 0.10f)

            drawAmbientEllipse(
                center = Offset(
                    x = size.width * (0.18f + driftPrimary * 0.14f),
                    y = size.height * (0.22f + driftSecondary * 0.12f)
                ),
                radius = maxDim * 0.62f,
                scaleX = 1.85f,
                scaleY = 0.88f,
                rotationDeg = -22f + driftTertiary * 12f,
                color = primaryWash,
                peakAlpha = 0.56f
            )
            drawAmbientEllipse(
                center = Offset(
                    x = size.width * (0.82f - driftSecondary * 0.15f),
                    y = size.height * (0.76f - driftPrimary * 0.12f)
                ),
                radius = maxDim * 0.68f,
                scaleX = 1.75f,
                scaleY = 0.92f,
                rotationDeg = 28f - driftPrimary * 10f,
                color = secondaryWash,
                peakAlpha = 0.50f
            )
            drawAmbientEllipse(
                center = Offset(
                    x = size.width * (0.76f - driftTertiary * 0.14f),
                    y = size.height * (0.20f + driftPrimary * 0.14f)
                ),
                radius = maxDim * 0.56f,
                scaleX = 1.60f,
                scaleY = 0.85f,
                rotationDeg = 14f + driftSecondary * 14f,
                color = tertiaryWash,
                peakAlpha = 0.44f
            )
            drawAmbientEllipse(
                center = Offset(
                    x = size.width * (0.28f + driftTertiary * 0.16f),
                    y = size.height * (0.78f - driftSecondary * 0.10f)
                ),
                radius = maxDim * 0.60f,
                scaleX = 1.90f,
                scaleY = 0.82f,
                rotationDeg = -36f + driftPrimary * 10f,
                color = bridgeWash,
                peakAlpha = 0.42f
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAmbientEllipse(
    center: Offset,
    radius: Float,
    scaleX: Float,
    scaleY: Float,
    rotationDeg: Float,
    color: Color,
    peakAlpha: Float
) {
    withTransform({
        translate(left = center.x, top = center.y)
        rotate(degrees = rotationDeg, pivot = Offset.Zero)
        scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to color.copy(alpha = peakAlpha),
                    0.45f to color.copy(alpha = peakAlpha * 0.42f),
                    1.0f to Color.Transparent
                ),
                center = Offset.Zero,
                radius = radius
            ),
            radius = radius,
            center = Offset.Zero
        )
    }
}

@Composable
private fun PlayerMetadataSection(song: com.lostf1sh.pixelplayeross.desktop.library.DesktopSong?, colors: ColorScheme) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val nameWidth = Modifier.widthIn(max = 680.dp)
        BouncingText(
            song?.title ?: "Pick something to play",
            MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = colors.onBackground),
            nameWidth,
            centered = true
        )
        BouncingText(
            song?.let { ArtistNames.split(it.artist).joinToString(" · ") }?.ifBlank { "Unknown artist" } ?: "",
            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, color = colors.onBackground),
            nameWidth,
            centered = true
        )
        BouncingText(
            song?.album.orEmpty(),
            MaterialTheme.typography.bodyMedium.copy(color = colors.onSurfaceVariant),
            nameWidth,
            centered = true
        )
    }
}

@Composable
private fun PlayerScrubberAndControls(
    audio: AudioController,
    state: com.lostf1sh.pixelplayeross.desktop.playback.PlaybackState,
    preview: Float?,
    liked: Boolean,
    editable: Boolean,
    onPreviewChange: (Float?) -> Unit,
    onLike: () -> Unit,
    onAdd: () -> Unit,
    onQueue: () -> Unit,
    onSleepTimer: () -> Unit,
    sleepTimerState: com.lostf1sh.pixelplayeross.desktop.system.SleepTimerState?,
    colors: ColorScheme
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                trackClock(preview?.let { (it * state.duration).toLong() } ?: state.time),
                Modifier.width(44.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onBackground,
                textAlign = TextAlign.End
            )
            key(state.song?.id) {
                CompactProgress(
                    if (state.duration > 0) (state.time.toDouble() / state.duration).toFloat() else 0f,
                    state.ready && state.seekable && state.duration > 0,
                    "Playback position",
                    Modifier.weight(1f),
                    onPreview = onPreviewChange,
                    onCommit = audio::seek
                )
            }
            Text(
                trackClock(state.duration),
                Modifier.width(44.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onBackground
            )
        }

        Box(Modifier.fillMaxWidth().height(72.dp)) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                FullPlayerButton("shuffle", if (state.shuffled) "Shuffle on" else "Shuffle off", state.ready, audio::toggleShuffle, active = state.shuffled)
                FullPlayerButton(if (state.repeat == RepeatMode.ONE) "repeat_one" else "repeat", "Repeat ${state.repeat}", state.ready, audio::cycleRepeat, active = state.repeat != RepeatMode.OFF)
                FullPlayerButton(if (liked) "heart" else "liked", if (liked) "Unlike" else "Like", editable && state.song != null, onLike)
            }
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                FullPlayerButton("previous", "Previous track", state.previous && state.ready, audio::previous)
                FilledIconButton(
                    onClick = audio::toggle,
                    enabled = state.ready && state.song != null,
                    shape = CircleShape,
                    modifier = Modifier.padding(horizontal = 16.dp).size(64.dp).semantics {
                        contentDescription = if (state.playing) "Pause" else "Play"
                    }
                ) {
                    Glyph(if (state.playing) "pause" else "play", LocalContentColor.current, Modifier.size(32.dp))
                }
                FullPlayerButton("next", "Next track", state.next && state.ready, audio::next)
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                FullPlayerButton("playlist_add", "Add to playlist", editable && state.song != null, onAdd)
                if (sleepTimerState?.isActive == true) {
                    Surface(
                        onClick = onSleepTimer,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Glyph("timer", MaterialTheme.colorScheme.primary, Modifier.size(16.dp))
                            Text(
                                com.lostf1sh.pixelplayeross.desktop.system.SleepTimer.formatRemaining(sleepTimerState.remainingSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    FullPlayerButton("timer", "Sleep timer", true, onSleepTimer)
                }
                FullPlayerButton("queue", "Playback queue", true, onQueue)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
                Glyph(if (state.volume == 0) "muted" else "volume", colors.onSurfaceVariant, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            CompactProgress(
                state.volume / 100f,
                state.ready,
                "Volume",
                Modifier.width(140.dp),
                onCommit = { audio.volume((it * 100).toInt()) }
            )
            Spacer(Modifier.width(10.dp))
            Text("${state.volume}%", Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = colors.onBackground)
        }

        state.error?.let {
            Text(
                it,
                color = colors.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun LyricsPaneContent(
    lyrics: SpicyLyrics,
    currentTimeMs: Long,
    isPlaying: Boolean,
    playbackRate: Float,
    isLoading: Boolean,
    fadeColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    onSeek: (Long) -> Unit,
    onOpenSettings: (() -> Unit)?
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = accentColor)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Fetching Spicy Lyrics...",
                    style = MaterialTheme.typography.bodySmall,
                    color = onBackgroundColor.copy(alpha = 0.72f)
                )
            }
        }
    } else {
        SpicyLyricsView(
            lyrics = lyrics,
            currentTimeMs = currentTimeMs,
            isPlaying = isPlaying,
            playbackRate = playbackRate,
            fadeColor = fadeColor,
            onBackgroundColor = onBackgroundColor,
            accentColor = accentColor,
            onSeek = onSeek,
            onOpenSettings = onOpenSettings
        )
    }
}

@Composable
private fun FullPlayerButton(
    icon: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false
) {
    val contentColor = MaterialTheme.colorScheme.onBackground
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = 0.38f)
        ),
        modifier = Modifier.size(48.dp).semantics { contentDescription = label }
    ) {
        val color = if ((icon == "heart" || active) && enabled) MaterialTheme.colorScheme.primary else LocalContentColor.current
        if (icon == "shuffle" || icon == "repeat" || icon == "repeat_one") ActiveControlGlyph(icon, color, active)
        else Glyph(icon, color)
    }
}

internal fun isDarkFullPlayerBackground(ambientColors: FullPlayerAmbientColors): Boolean {
    return ambientColors.deepBackground.luminance() < 0.5f
}

internal fun fullPlayerContrastRatio(foreground: Color, background: Color): Float {
    val composited = if (foreground.alpha < 1f) foreground.compositeOver(background) else foreground
    val l1 = composited.luminance()
    val l2 = background.luminance()
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal fun ensureFullPlayerContrast(
    foreground: Color,
    backgrounds: List<Color>,
    fallback: Color,
    minRatio: Float = 4.5f
): Color {
    if (backgrounds.all { fullPlayerContrastRatio(foreground, it) >= minRatio }) {
        return foreground
    }
    for (step in 1..20) {
        val candidate = lerp(foreground, fallback, step / 20f)
        if (backgrounds.all { fullPlayerContrastRatio(candidate, it) >= minRatio }) {
            return candidate
        }
    }
    return fallback
}

internal fun resolveFullPlayerContrastColors(
    baseScheme: ColorScheme,
    ambientColors: FullPlayerAmbientColors
): ColorScheme {
    val isDarkBg = isDarkFullPlayerBackground(ambientColors)
    val backgrounds = listOf(
        ambientColors.deepBackground,
        lerp(ambientColors.glowBase, ambientColors.glowAccent, 0.38f)
    )
    val idealOnBg = if (isDarkBg) Color(0xFFF5F7FA) else Color(0xFF1A1C1E)
    val safeOnBackground = ensureFullPlayerContrast(baseScheme.onBackground, backgrounds, idealOnBg, minRatio = 4.5f)
    val safeOnSurface = ensureFullPlayerContrast(baseScheme.onSurface, backgrounds, safeOnBackground, minRatio = 4.5f)
    val safeOnSurfaceVariant = ensureFullPlayerContrast(baseScheme.onSurfaceVariant, backgrounds, safeOnBackground, minRatio = 4.5f)
    val safePrimary = ensureFullPlayerContrast(baseScheme.primary, backgrounds, safeOnBackground, minRatio = 4.5f)
    val safeOnPrimary = contrastingText(safePrimary)
    val safeOnPrimaryContainer = ensureFullPlayerContrast(
        baseScheme.onPrimaryContainer,
        listOf(baseScheme.primaryContainer),
        contrastingText(baseScheme.primaryContainer),
        minRatio = 4.5f
    )
    val safeError = ensureFullPlayerContrast(
        baseScheme.error,
        backgrounds,
        if (isDarkBg) Color(0xFFFFEDE9) else Color(0xFF8C1D18),
        minRatio = 4.5f
    )
    return baseScheme.copy(
        primary = safePrimary,
        onPrimary = safeOnPrimary,
        onPrimaryContainer = safeOnPrimaryContainer,
        onBackground = safeOnBackground,
        onSurface = safeOnSurface,
        onSurfaceVariant = safeOnSurfaceVariant,
        error = safeError
    )
}

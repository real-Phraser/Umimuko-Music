package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lostf1sh.pixelplayeross.desktop.library.ArtistNames
import com.lostf1sh.pixelplayeross.desktop.lyrics.LrcLyrics
import com.lostf1sh.pixelplayeross.desktop.lyrics.LyricsService
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import com.lostf1sh.pixelplayeross.desktop.playback.RepeatMode
import com.lostf1sh.pixelplayeross.desktop.theme.FullPlayerAmbientColors
import com.lostf1sh.pixelplayeross.desktop.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onExit: () -> Unit
) {
    val state by audio.state.collectAsState()
    var preview by remember(state.song?.id) { mutableStateOf<Float?>(null) }
    var showLyrics by remember { mutableStateOf(false) }

    // Lyrics state
    var lyrics by remember(state.song?.id) { mutableStateOf(LrcLyrics.EMPTY) }
    var isLoadingLyrics by remember(state.song?.id) { mutableStateOf(false) }

    LaunchedEffect(state.song?.id) {
        val song = state.song
        if (song != null) {
            isLoadingLyrics = true
            val fetched = withContext(Dispatchers.IO) {
                LyricsService.getLyrics(song)
            }
            lyrics = fetched
            isLoadingLyrics = false
        } else {
            lyrics = LrcLyrics.EMPTY
        }
    }

    val currentPreset by ThemeManager.currentPreset.collectAsState()
    val ambientColors = remember(currentPreset, state.song?.accentArgb) {
        ThemeManager.resolveFullPlayerAmbientColors(state.song?.accentArgb, currentPreset)
    }

    // Dynamic ambient glow animation
    val infiniteTransition = rememberInfiniteTransition()
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )

    val backgroundGradient = remember(ambientColors, ambientPulse) {
        val primaryGlow = lerp(ambientColors.glowBase, ambientColors.glowAccent, ambientPulse)
        Brush.radialGradient(
            colors = listOf(primaryGlow, ambientColors.deepBackground),
            radius = 1600f
        )
    }

    val fullColors = remember(currentPreset, state.song?.accentArgb, ambientColors) {
        resolveFullPlayerContrastColors(
            baseScheme = ThemeManager.resolveFullPlayerColorScheme(state.song?.accentArgb),
            ambientColors = ambientColors
        )
    }
    val isDarkBg = remember(ambientColors) { isDarkFullPlayerBackground(ambientColors) }
    val lyricsContainerColor = if (isDarkBg) Color.Black.copy(alpha = 0.24f) else fullColors.surface.copy(alpha = 0.72f)

    MaterialTheme(colorScheme = fullColors, typography = MaterialTheme.typography) {
        val colors = MaterialTheme.colorScheme
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(backgroundGradient)
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

                            // Right Pane: Synced Karaoke Lyrics View
                            Box(
                                Modifier
                                    .weight(1.3f)
                                    .fillMaxHeight()
                                    .background(lyricsContainerColor, RoundedCornerShape(20.dp))
                                    .padding(20.dp)
                            ) {
                                SyncedLyricsList(
                                    lyrics = lyrics,
                                    currentTimeMs = state.time,
                                    isLoading = isLoadingLyrics,
                                    fadeColor = ambientColors.deepBackground,
                                    onSeekTo = { seekMs ->
                                        if (state.duration > 0) {
                                            audio.seek(seekMs.toFloat() / state.duration.toFloat())
                                        }
                                    }
                                )
                            }
                        }
                    } else if (showLyrics && !isWide) {
                        // Narrow screen with lyrics: show full lyrics view with overlay controls
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(lyricsContainerColor, RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            SyncedLyricsList(
                                lyrics = lyrics,
                                currentTimeMs = state.time,
                                isLoading = isLoadingLyrics,
                                fadeColor = ambientColors.deepBackground,
                                onSeekTo = { seekMs ->
                                    if (state.duration > 0) {
                                        audio.seek(seekMs.toFloat() / state.duration.toFloat())
                                    }
                                }
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
            }
        }
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
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onSleepTimer)
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
private fun SyncedLyricsList(
    lyrics: LrcLyrics,
    currentTimeMs: Long,
    isLoading: Boolean,
    fadeColor: Color = Color(0xFF0F1117),
    onSeekTo: (Long) -> Unit
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Spacer(Modifier.height(12.dp))
                Text("Searching synced lyrics online...", style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    if (lyrics.lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No synchronized lyrics available for this track.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val activeIndex = lyrics.findActiveLineIndex(currentTimeMs)
    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    var userScrolled by remember { mutableStateOf(false) }
    var lastUserScrollEpoch by remember { mutableStateOf(0L) }

    // Detect user manual interaction with scrollbar or mouse wheel
    LaunchedEffect(listState.isScrollInProgress, isDragged) {
        if (isDragged || (listState.isScrollInProgress && !isProgrammaticScroll)) {
            userScrolled = true
            lastUserScrollEpoch = System.currentTimeMillis()
        }
    }

    // Auto-resume smooth tracking 3.5 seconds after user stops manually scrolling
    LaunchedEffect(currentTimeMs) {
        if (userScrolled && System.currentTimeMillis() - lastUserScrollEpoch > 3500L) {
            userScrolled = false
        }
    }

    // Centered smooth scroll animation
    LaunchedEffect(activeIndex, userScrolled) {
        if (activeIndex >= 0 && !userScrolled) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportSize.height
            // Position the active item in the vertical center of the view
            val centerOffset = if (viewportHeight > 0) -(viewportHeight / 3) else -100
            isProgrammaticScroll = true
            try {
                listState.animateScrollToItem(activeIndex, centerOffset)
            } finally {
                kotlinx.coroutines.delay(60L)
                isProgrammaticScroll = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 180.dp, bottom = 260.dp)
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isActive = index == activeIndex
                val isPast = index < activeIndex

                val textColor by animateColorAsState(
                    targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                )

                val alpha by animateFloatAsState(
                    targetValue = if (isActive) 1.0f else if (isPast) 0.65f else 0.32f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                )

                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.12f else 1.0f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f) else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            userScrolled = false
                            onSeekTo(line.timestampMs)
                        }
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = line.text.ifBlank { "♪" },
                        fontSize = 20.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = textColor,
                        lineHeight = 28.sp
                    )
                }
            }
        }

        // Top edge soft fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(fadeColor.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
        )

        // Bottom edge soft fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, fadeColor.copy(alpha = 0.85f))
                    )
                )
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

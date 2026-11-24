package com.lostf1sh.pixelplayeross.desktop.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import com.lostf1sh.pixelplayeross.desktop.Glyph
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

private const val INTRO_DOTS_THRESHOLD_MS = 1500L
private const val INTERLUDE_GAP_THRESHOLD_MS = 5000L
private const val MANUAL_SCROLL_RESUME_MS = 4000L
private const val CLOCK_SNAP_DRIFT_MS = 350.0
private const val CLOCK_MAX_AHEAD_MS = 600.0

/**
 * Spicy Lyrics inspired karaoke view supporting syllable-by-syllable sweep,
 * line-synced, and static lyrics with motion, interludes, duets, and attribution.
 */
@Composable
fun SpicyLyricsView(
    lyrics: SpicyLyrics,
    currentTimeMs: Long,
    fadeColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    onSeek: (Long) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    isPlaying: Boolean = true,
    playbackRate: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    if (lyrics.lines.isEmpty()) {
        SpicyLyricsEmptyState(
            hasApiKey = SpicyLyricsService.hasApiKey(),
            onBackgroundColor = onBackgroundColor,
            accentColor = accentColor,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
        return
    }

    val isStatic = lyrics.type == SpicyLyricsType.STATIC
    val latestPositionMs by rememberUpdatedState(currentTimeMs)
    val latestRate by rememberUpdatedState(playbackRate.coerceIn(0.25f, 4.0f))
    val interpolatedTimeMsState = remember(lyrics) { mutableLongStateOf(currentTimeMs) }

    // Snap immediately when paused (e.g. user seeks while paused)
    LaunchedEffect(currentTimeMs, isPlaying, lyrics) {
        if (!isPlaying || isStatic) {
            interpolatedTimeMsState.longValue = currentTimeMs
        }
    }

    // 60fps/120fps smooth playback clock interpolation between coarse ~100ms audio position ticks
    LaunchedEffect(isPlaying, isStatic, lyrics) {
        if (!isPlaying || isStatic) return@LaunchedEffect
        var lastFrameNanos = 0L
        var lastSeenAudioMs = latestPositionMs
        var smoothTime = lastSeenAudioMs.toDouble()
        var pendingCorrectionMs = 0.0

        while (isActive) {
            withFrameNanos { frameNanos ->
                val dtMs = if (lastFrameNanos == 0L) {
                    0.0
                } else {
                    ((frameNanos - lastFrameNanos) / 1_000_000.0).coerceIn(0.0, 100.0) * latestRate
                }
                lastFrameNanos = frameNanos

                val audioMs = latestPositionMs
                if (audioMs != lastSeenAudioMs) {
                    val drift = audioMs.toDouble() - smoothTime
                    lastSeenAudioMs = audioMs
                    if (abs(drift) > CLOCK_SNAP_DRIFT_MS) {
                        smoothTime = audioMs.toDouble()
                        pendingCorrectionMs = 0.0
                    } else {
                        pendingCorrectionMs = drift
                    }
                }

                smoothTime += dtMs

                if (abs(pendingCorrectionMs) > 0.5) {
                    val step = pendingCorrectionMs * 0.25
                    smoothTime += step
                    pendingCorrectionMs -= step
                }

                // Prevent runaway if audio playback stalls/buffers without updating position
                if (abs(smoothTime - audioMs) > CLOCK_MAX_AHEAD_MS) {
                    smoothTime = audioMs.toDouble()
                    pendingCorrectionMs = 0.0
                }

                interpolatedTimeMsState.longValue = smoothTime.toLong().coerceAtLeast(0L)
            }
        }
    }

    val timeProvider = remember(interpolatedTimeMsState) { { interpolatedTimeMsState.longValue } }

    val activeIndex by remember(lyrics, isStatic) {
        derivedStateOf {
            if (isStatic) -1 else lyrics.findActiveLineIndex(interpolatedTimeMsState.longValue)
        }
    }

    val firstLine = lyrics.lines.firstOrNull()
    val showIntroDots by remember(lyrics, isStatic, firstLine) {
        derivedStateOf {
            !isStatic &&
                firstLine != null &&
                interpolatedTimeMsState.longValue < (firstLine.startTimeMs - INTRO_DOTS_THRESHOLD_MS)
        }
    }

    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var userScrolled by remember { mutableStateOf(false) }
    var lastUserScrollEpoch by remember { mutableStateOf(0L) }

    // Detect manual user scroll (drag, mouse wheel, or scrollbar)
    LaunchedEffect(listState.isScrollInProgress, isDragged) {
        if (!isStatic && (isDragged || (listState.isScrollInProgress && !isProgrammaticScroll))) {
            userScrolled = true
            lastUserScrollEpoch = System.currentTimeMillis()
        }
    }

    // Auto-resume sync after user stops scrolling
    LaunchedEffect(userScrolled, lastUserScrollEpoch) {
        if (userScrolled) {
            delay(MANUAL_SCROLL_RESUME_MS)
            userScrolled = false
        }
    }

    // Smooth auto-scroll positioning the active line in the upper-middle sweet spot (~32% from top)
    LaunchedEffect(activeIndex, showIntroDots, userScrolled, isStatic) {
        if (isStatic || userScrolled) return@LaunchedEffect

        val targetItemIndex = when {
            activeIndex >= 0 -> activeIndex + 1 // item 0 is the intro dots slot
            showIntroDots -> 0
            else -> return@LaunchedEffect
        }

        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height
        val sweetSpotFromTop = if (viewportHeight > 0) (viewportHeight * 0.32f).toInt() else 160
        val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetItemIndex }

        isProgrammaticScroll = true
        try {
            if (visibleItem != null) {
                val desiredOffset = layoutInfo.viewportStartOffset + sweetSpotFromTop
                val delta = (visibleItem.offset - desiredOffset).toFloat()
                if (abs(delta) > 1f) {
                    listState.animateScrollBy(
                        value = delta,
                        animationSpec = spring(
                            dampingRatio = 0.9f,
                            stiffness = 120f
                        )
                    )
                }
            } else {
                listState.animateScrollToItem(targetItemIndex, -sweetSpotFromTop)
            }
        } finally {
            delay(80L)
            isProgrammaticScroll = false
        }
    }

    var hasEntered by remember(lyrics) { mutableStateOf(false) }
    LaunchedEffect(lyrics) {
        hasEntered = true
    }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
    )
    val entranceOffsetY by animateDpAsState(
        targetValue = if (hasEntered) 0.dp else 18.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 280f)
    )
    val entranceDensity = LocalDensity.current

    val showRomanized = SpicyLyricsService.showRomanized
    val introGapStart = remember(lyrics, firstLine) {
        if (firstLine == null) 0L else lyrics.startTimeMs.coerceAtMost(firstLine.startTimeMs)
    }
    val introProgressProvider = remember(firstLine, introGapStart, timeProvider) {
        {
            if (firstLine != null) {
                ((timeProvider() - introGapStart).toFloat() /
                    (firstLine.startTimeMs - introGapStart).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = entranceAlpha
                    translationY = with(entranceDensity) { entranceOffsetY.toPx() }
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.10f to Color.Black,
                            0.85f to Color.Black,
                            1.0f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            verticalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(top = 120.dp, bottom = 260.dp)
        ) {
            // Item 0: Intro instrumental dots before the first line
            item(key = "intro-dots") {
                AnimatedVisibility(
                    visible = showIntroDots,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(250))
                ) {
                    InterludeDotsRow(
                        progressProvider = introProgressProvider,
                        accentColor = accentColor,
                        onBackgroundColor = onBackgroundColor,
                        oppositeAligned = firstLine?.oppositeAligned == true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            itemsIndexed(
                items = lyrics.lines,
                key = { index, line -> "${index}_${line.startTimeMs}_${line.text.hashCode()}" }
            ) { index, line ->
                val nextLine = lyrics.lines.getOrNull(index + 1)
                val hasLongGapAfter = !isStatic &&
                    nextLine != null &&
                    (nextLine.startTimeMs - line.endTimeMs) > INTERLUDE_GAP_THRESHOLD_MS
                val showGapDots by remember(hasLongGapAfter, line, nextLine) {
                    derivedStateOf {
                        if (!hasLongGapAfter) {
                            false
                        } else {
                            val now = timeProvider()
                            now >= line.endTimeMs && now < nextLine!!.startTimeMs
                        }
                    }
                }
                val gapProgressProvider = remember(hasLongGapAfter, line, nextLine, timeProvider) {
                    {
                        if (hasLongGapAfter) {
                            ((timeProvider() - line.endTimeMs).toFloat() /
                                (nextLine!!.startTimeMs - line.endTimeMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    SpicyLyricLineItem(
                        line = line,
                        index = index,
                        activeIndex = activeIndex,
                        lyricsType = lyrics.type,
                        timeProvider = timeProvider,
                        showRomanized = showRomanized,
                        isUserScrolling = userScrolled,
                        onBackgroundColor = onBackgroundColor,
                        accentColor = accentColor,
                        onClick = {
                            userScrolled = false
                            onSeek(line.startTimeMs)
                        }
                    )

                    if (hasLongGapAfter) {
                        AnimatedVisibility(
                            visible = showGapDots,
                            enter = fadeIn(tween(300)),
                            exit = fadeOut(tween(250))
                        ) {
                            InterludeDotsRow(
                                progressProvider = gapProgressProvider,
                                accentColor = accentColor,
                                onBackgroundColor = onBackgroundColor,
                                oppositeAligned = nextLine?.oppositeAligned == true,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 18.dp,
                                    bottom = 6.dp
                                )
                            )
                        }
                    }
                }
            }

            // Attribution footer
            if (lyrics.songWriters.isNotEmpty() || !lyrics.uploaderName.isNullOrBlank() || !lyrics.source.isNullOrBlank()) {
                item(key = "attribution-footer") {
                    SpicyLyricsFooter(
                        songWriters = lyrics.songWriters,
                        uploaderName = lyrics.uploaderName,
                        source = lyrics.source,
                        onBackgroundColor = onBackgroundColor
                    )
                }
            }
        }

        // "Resume Sync" pill when user manually scrolled
        AnimatedVisibility(
            visible = userScrolled && !isStatic && activeIndex >= 0,
            enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
        ) {
            Surface(
                onClick = { userScrolled = false },
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.92f),
                contentColor = if (luminanceOf(accentColor) > 0.5f) Color(0xFF141619) else Color.White,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Resume Sync",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SpicyLyricLineItem(
    line: SpicyLine,
    index: Int,
    activeIndex: Int,
    lyricsType: SpicyLyricsType,
    timeProvider: () -> Long,
    showRomanized: Boolean,
    isUserScrolling: Boolean,
    onBackgroundColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    val isStatic = lyricsType == SpicyLyricsType.STATIC
    val isActive = !isStatic && index == activeIndex
    val distance = if (activeIndex < 0) 0 else abs(index - activeIndex)

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val distanceAlpha = when {
        isStatic -> 0.92f
        activeIndex < 0 -> 0.55f
        else -> when (distance) {
            0 -> 1.0f
            1 -> 0.42f
            2 -> 0.30f
            else -> 0.22f
        }
    }
    val targetAlpha = when {
        isStatic -> 0.92f
        isHovered -> 0.94f
        isUserScrolling -> maxOf(0.65f, distanceAlpha)
        else -> distanceAlpha
    }
    val targetScale = when {
        isStatic || activeIndex < 0 -> 1.0f
        isActive -> 1.04f
        isHovered -> 1.0f
        else -> when (distance) {
            1 -> 0.97f
            2 -> 0.94f
            else -> 0.92f
        }
    }
    val targetBlur = when {
        isStatic || isActive || isHovered || isUserScrolling || activeIndex < 0 -> 0.dp
        distance == 1 -> 1.dp
        distance == 2 -> 2.dp
        else -> 3.dp
    }
    val targetOffsetX = when {
        isStatic || activeIndex < 0 -> 0.dp
        isActive -> if (line.oppositeAligned) (-4).dp else 4.dp
        else -> 0.dp
    }
    val targetOffsetY = when {
        isStatic || activeIndex < 0 -> 0.dp
        isActive -> (-2).dp
        index < activeIndex -> (-3).dp
        else -> 3.dp
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 180f
        )
    )
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 180f
        )
    )
    val animatedBlur by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 180f
        )
    )
    val animatedOffsetX by animateDpAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 180f
        )
    )
    val animatedOffsetY by animateDpAsState(
        targetValue = targetOffsetY,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 180f
        )
    )

    val density = LocalDensity.current
    val horizontalAlignment = if (line.oppositeAligned) Alignment.End else Alignment.Start
    val textAlign = if (line.oppositeAligned) TextAlign.End else TextAlign.Start
    val transformOrigin = TransformOrigin(if (line.oppositeAligned) 1f else 0f, 0.5f)

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (animatedBlur > 0.1.dp) {
                    Modifier.blur(animatedBlur, BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
                translationX = with(density) { animatedOffsetX.toPx() }
                translationY = with(density) { animatedOffsetY.toPx() }
                this.transformOrigin = transformOrigin
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (lyricsType == SpicyLyricsType.SYLLABLE && line.syllables.isNotEmpty()) {
            SyllableSweepLine(
                syllables = line.syllables,
                isLineActive = isActive,
                isLinePast = !isStatic && activeIndex >= 0 && index < activeIndex,
                timeProvider = timeProvider,
                oppositeAligned = line.oppositeAligned,
                onBackgroundColor = onBackgroundColor,
                accentColor = accentColor,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                baseWeight = FontWeight.ExtraBold
            )
        } else {
            val lineColor by animateColorAsState(
                targetValue = when {
                    isStatic -> onBackgroundColor
                    isActive -> lerp(onBackgroundColor, accentColor, 0.16f)
                    else -> onBackgroundColor
                },
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 180f
                )
            )
            val shadow = if (isActive) {
                Shadow(
                    color = accentColor.copy(alpha = 0.42f),
                    offset = Offset.Zero,
                    blurRadius = 16f
                )
            } else {
                null
            }
            Text(
                text = line.text.ifBlank { "♪" },
                style = TextStyle(
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.3).sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                    color = lineColor,
                    textAlign = textAlign,
                    shadow = shadow
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Optional romanized sub-line
        if (showRomanized && !line.romanizedText.isNullOrBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = line.romanizedText!!,
                style = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    color = onBackgroundColor.copy(alpha = if (isActive) 0.78f else 0.56f),
                    textAlign = textAlign
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Optional background vocals sub-lines
        if (line.backgroundVocals.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                line.backgroundVocals.forEach { bgLine ->
                    if (lyricsType == SpicyLyricsType.SYLLABLE && bgLine.syllables.isNotEmpty()) {
                        val bgActive by remember(bgLine, timeProvider) {
                            derivedStateOf { timeProvider() in bgLine.startTimeMs..bgLine.endTimeMs }
                        }
                        val bgPast by remember(bgLine, timeProvider) {
                            derivedStateOf { timeProvider() > bgLine.endTimeMs }
                        }
                        SyllableSweepLine(
                            syllables = bgLine.syllables,
                            isLineActive = bgActive || isActive,
                            isLinePast = bgPast,
                            timeProvider = timeProvider,
                            oppositeAligned = line.oppositeAligned,
                            onBackgroundColor = onBackgroundColor.copy(alpha = 0.72f),
                            accentColor = accentColor,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            baseWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = bgLine.text,
                            style = TextStyle(
                                fontSize = 17.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = onBackgroundColor.copy(alpha = 0.58f),
                                textAlign = textAlign
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (showRomanized && !bgLine.romanizedText.isNullOrBlank()) {
                        Text(
                            text = bgLine.romanizedText!!,
                            style = TextStyle(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontStyle = FontStyle.Italic,
                                color = onBackgroundColor.copy(alpha = 0.48f),
                                textAlign = textAlign
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private data class WordGroup(
    val syllables: List<SpicySyllable>,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isSplitWord: Boolean
)

private fun List<SpicySyllable>.toWordGroup(): WordGroup = WordGroup(
    syllables = toList(),
    startTimeMs = minOf { it.startTimeMs },
    endTimeMs = maxOf { it.endTimeMs },
    isSplitWord = size > 1 || firstOrNull()?.isPartOfWord == true
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyllableSweepLine(
    syllables: List<SpicySyllable>,
    isLineActive: Boolean,
    isLinePast: Boolean,
    timeProvider: () -> Long,
    oppositeAligned: Boolean,
    onBackgroundColor: Color,
    accentColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    baseWeight: FontWeight = FontWeight.ExtraBold
) {
    // Group syllables into words so split words have zero extra space, never wrap mid-word,
    // and lift/scale as a single unified word.
    val words = remember(syllables) {
        buildList {
            var currentWord = mutableListOf<SpicySyllable>()
            for (syl in syllables) {
                currentWord.add(syl)
                if (!syl.isPartOfWord) {
                    add(currentWord.toWordGroup())
                    currentWord = mutableListOf()
                }
            }
            if (currentWord.isNotEmpty()) {
                add(currentWord.toWordGroup())
            }
        }
    }

    val density = LocalDensity.current
    val wholeLiftPx = with(density) { (-3.0f).dp.toPx() }
    val splitLiftPx = with(density) { (-2.2f).dp.toPx() }
    val litColor = onBackgroundColor
    val unlitColor = onBackgroundColor.copy(alpha = if (isLineActive) 0.34f else 0.72f)
    val glowCoreColor = remember(accentColor) { lerp(accentColor, Color(0xFFFFE4B5), 0.28f) }

    // Static TextStyle with no per-frame Brush or Shadow so Skia builds TextParagraph and caches glyphs once.
    val staticTextStyle = remember(fontSize, lineHeight, baseWeight) {
        TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = (-0.3).sp,
            fontWeight = baseWeight,
            color = Color.White
        )
    }
    val bloomTextStyle = remember(staticTextStyle, glowCoreColor) {
        staticTextStyle.copy(color = glowCoreColor)
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 7.dp,
            alignment = if (oppositeAligned) Alignment.End else Alignment.Start
        ),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        words.forEach { word ->
            val wordLiftPx = if (word.isSplitWord) splitLiftPx else wholeLiftPx
            val wordScaleBoost = if (word.isSplitWord) 0.03f else 0.04f
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer {
                    if (isLineActive && !isLinePast) {
                        val env = singingEnvelope(word.startTimeMs, word.endTimeMs, timeProvider())
                        translationY = wordLiftPx * env
                        val s = 1f + wordScaleBoost * env
                        scaleX = s
                        scaleY = s
                    }
                }
            ) {
                word.syllables.forEach { syl ->
                    Box {
                        if (isLineActive && !isLinePast) {
                            Text(
                                text = syl.text,
                                style = bloomTextStyle,
                                modifier = Modifier
                                    .blur(5.dp, BlurredEdgeTreatment.Unbounded)
                                    .graphicsLayer {
                                        val env = singingEnvelope(syl.startTimeMs, syl.endTimeMs, timeProvider())
                                        alpha = (env * 0.22f).coerceIn(0f, 0.22f)
                                    }
                            )
                        }
                        Text(
                            text = syl.text,
                            style = staticTextStyle,
                            modifier = Modifier
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    val progress = when {
                                        isLinePast -> 1f
                                        !isLineActive -> 0f
                                        else -> syllableProgress(syl, timeProvider())
                                    }
                                    when {
                                        progress <= 0.001f -> {
                                            drawRect(color = unlitColor, blendMode = BlendMode.SrcIn)
                                        }
                                        progress >= 0.999f -> {
                                            drawRect(color = litColor, blendMode = BlendMode.SrcIn)
                                        }
                                        else -> {
                                            val feather = 0.20f
                                            val startFade = (progress * (1f + feather) - feather).coerceIn(0f, 1f)
                                            val endFade = (progress * (1f + feather)).coerceIn(0f, 1f)
                                            drawRect(
                                                brush = Brush.horizontalGradient(
                                                    0f to litColor,
                                                    startFade to litColor,
                                                    endFade to unlitColor,
                                                    1f to unlitColor,
                                                    startX = 0f,
                                                    endX = size.width.coerceAtLeast(1f)
                                                ),
                                                blendMode = BlendMode.SrcIn
                                            )
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

private const val ENVELOPE_ANTICIPATION_MS = 100L
private const val ENVELOPE_ATTACK_TOTAL_MS = 140f
private const val ENVELOPE_RELEASE_MS = 220f

private fun smootherStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

/**
 * Smooth anticipation-and-tail envelope for word lift/scale and syllable bloom.
 * Ramps in ~100ms before [startTimeMs] and settles over ~220ms after [endTimeMs].
 */
private fun singingEnvelope(startTimeMs: Long, endTimeMs: Long, nowMs: Long): Float {
    val attackStart = startTimeMs - ENVELOPE_ANTICIPATION_MS
    if (nowMs <= attackStart) return 0f
    if (nowMs >= endTimeMs + ENVELOPE_RELEASE_MS.toLong()) return 0f

    val attack = smootherStep((nowMs - attackStart) / ENVELOPE_ATTACK_TOTAL_MS)
    val release = if (nowMs <= endTimeMs) {
        1f
    } else {
        1f - smootherStep((nowMs - endTimeMs) / ENVELOPE_RELEASE_MS)
    }
    return minOf(attack, release)
}

private fun syllableProgress(syl: SpicySyllable, nowMs: Long): Float = when {
    nowMs <= syl.startTimeMs -> 0f
    nowMs >= syl.endTimeMs -> 1f
    syl.endTimeMs <= syl.startTimeMs -> 1f
    else -> ((nowMs - syl.startTimeMs).toFloat() /
        (syl.endTimeMs - syl.startTimeMs).toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun InterludeDotsRow(
    progressProvider: () -> Float,
    accentColor: Color,
    onBackgroundColor: Color,
    oppositeAligned: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val dotBaseColor = onBackgroundColor.copy(alpha = 0.28f)
    val dotLitColor = lerp(onBackgroundColor, accentColor, 0.35f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 10.dp,
            alignment = if (oppositeAligned) Alignment.End else Alignment.Start
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { dotIndex ->
            val dotStart = dotIndex / 3f
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .graphicsLayer {
                        val progress = progressProvider()
                        val dotFill = ((progress - dotStart) * 3f).coerceIn(0f, 1f)
                        val isCurrentlyFilling = dotFill > 0f && dotFill < 1f
                        val pulseBoost = if (isCurrentlyFilling) breathe * 0.14f else if (progress > 0.92f) breathe * 0.10f else 0f
                        val effectiveFill = (dotFill + pulseBoost).coerceIn(0f, 1f)
                        val scale = 0.85f + (0.30f * effectiveFill)
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.32f + (0.68f * effectiveFill)
                    }
                    .clip(CircleShape)
                    .drawBehind {
                        val progress = progressProvider()
                        val dotFill = ((progress - dotStart) * 3f).coerceIn(0f, 1f)
                        val isCurrentlyFilling = dotFill > 0f && dotFill < 1f
                        val pulseBoost = if (isCurrentlyFilling) breathe * 0.14f else if (progress > 0.92f) breathe * 0.10f else 0f
                        val effectiveFill = (dotFill + pulseBoost).coerceIn(0f, 1f)
                        drawRect(lerp(dotBaseColor, dotLitColor, effectiveFill))
                    }
            )
        }
    }
}

@Composable
private fun SpicyLyricsFooter(
    songWriters: List<String>,
    uploaderName: String?,
    source: String?,
    onBackgroundColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (songWriters.isNotEmpty()) {
            Text(
                text = "Written by ${songWriters.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = onBackgroundColor.copy(alpha = 0.52f)
            )
        }
        if (!uploaderName.isNullOrBlank()) {
            Text(
                text = "Lyrics synced by $uploaderName",
                style = MaterialTheme.typography.bodySmall,
                color = onBackgroundColor.copy(alpha = 0.44f)
            )
        }
        if (!source.isNullOrBlank()) {
            Text(
                text = "Source: $source",
                style = MaterialTheme.typography.labelSmall,
                color = onBackgroundColor.copy(alpha = 0.36f)
            )
        }
    }
}

@Composable
private fun SpicyLyricsEmptyState(
    hasApiKey: Boolean,
    onBackgroundColor: Color,
    accentColor: Color,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Glyph(
                kind = "lyrics",
                color = onBackgroundColor.copy(alpha = 0.45f),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "No lyrics available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onBackgroundColor.copy(alpha = 0.85f)
            )
            Text(
                text = if (!hasApiKey) {
                    "Add a Spicy Lyrics API key in Settings to unlock word-by-word synced lyrics."
                } else {
                    "No synchronized or static lyrics were found for this track."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = onBackgroundColor.copy(alpha = 0.58f),
                textAlign = TextAlign.Center
            )
            if (!hasApiKey && onOpenSettings != null) {
                Spacer(Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = accentColor.copy(alpha = 0.18f),
                        contentColor = onBackgroundColor
                    )
                ) {
                    Glyph("settings", onBackgroundColor, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Settings")
                }
            }
        }
    }
}

private fun luminanceOf(color: Color): Float {
    return 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
}

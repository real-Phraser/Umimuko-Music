package com.lostf1sh.pixelplayeross.desktop.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lostf1sh.pixelplayeross.desktop.Glyph
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val INTRO_DOTS_THRESHOLD_MS = 1500L
private const val INTERLUDE_GAP_THRESHOLD_MS = 5000L
private const val MANUAL_SCROLL_RESUME_MS = 4000L

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
    val activeIndex = remember(lyrics, currentTimeMs, isStatic) {
        if (isStatic) -1 else lyrics.findActiveLineIndex(currentTimeMs)
    }

    val firstLine = lyrics.lines.firstOrNull()
    val showIntroDots = !isStatic &&
        firstLine != null &&
        currentTimeMs < (firstLine.startTimeMs - INTRO_DOTS_THRESHOLD_MS)

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
    LaunchedEffect(currentTimeMs, userScrolled) {
        if (userScrolled && System.currentTimeMillis() - lastUserScrollEpoch > MANUAL_SCROLL_RESUME_MS) {
            userScrolled = false
        }
    }

    // Smooth auto-scroll centering the active line (or intro dots before first line)
    LaunchedEffect(activeIndex, showIntroDots, userScrolled, isStatic) {
        if (isStatic || userScrolled) return@LaunchedEffect

        val targetItemIndex = when {
            activeIndex >= 0 -> activeIndex + 1 // item 0 is the intro dots slot
            showIntroDots -> 0
            else -> return@LaunchedEffect
        }

        val viewportHeight = listState.layoutInfo.viewportSize.height
        val centerOffset = if (viewportHeight > 0) -(viewportHeight / 3) else -120
        isProgrammaticScroll = true
        try {
            listState.animateScrollToItem(targetItemIndex, centerOffset)
        } finally {
            delay(80L)
            isProgrammaticScroll = false
        }
    }

    val showRomanized = SpicyLyricsService.showRomanized

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 140.dp, bottom = 220.dp)
        ) {
            // Item 0: Intro instrumental dots before the first line
            item(key = "intro-dots") {
                AnimatedVisibility(
                    visible = showIntroDots,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(250))
                ) {
                    InterludeDotsRow(
                        accentColor = accentColor,
                        onBackgroundColor = onBackgroundColor,
                        oppositeAligned = firstLine?.oppositeAligned == true,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
                val showGapDots = hasLongGapAfter &&
                    currentTimeMs >= line.endTimeMs &&
                    currentTimeMs < nextLine!!.startTimeMs

                Column(modifier = Modifier.fillMaxWidth()) {
                    SpicyLyricLineItem(
                        line = line,
                        index = index,
                        activeIndex = activeIndex,
                        lyricsType = lyrics.type,
                        currentTimeMs = currentTimeMs,
                        showRomanized = showRomanized,
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
                                accentColor = accentColor,
                                onBackgroundColor = onBackgroundColor,
                                oppositeAligned = nextLine?.oppositeAligned == true,
                                modifier = Modifier.padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 12.dp,
                                    bottom = 4.dp
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

        // Top soft edge fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(fadeColor.copy(alpha = 0.88f), Color.Transparent)
                    )
                )
        )

        // Bottom soft edge fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, fadeColor.copy(alpha = 0.88f))
                    )
                )
        )

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
    currentTimeMs: Long,
    showRomanized: Boolean,
    onBackgroundColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    val isStatic = lyricsType == SpicyLyricsType.STATIC
    val isActive = !isStatic && index == activeIndex

    val targetAlpha = when {
        isStatic -> 0.90f
        activeIndex < 0 -> 0.52f
        else -> when (abs(index - activeIndex)) {
            0 -> 1.0f
            1 -> 0.56f
            2 -> 0.36f
            3 -> 0.24f
            else -> 0.16f
        }
    }
    val targetScale = when {
        isStatic || activeIndex < 0 -> 1.0f
        else -> when (abs(index - activeIndex)) {
            0 -> 1.0f
            1 -> 0.96f
            2 -> 0.93f
            else -> 0.90f
        }
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(360, easing = FastOutSlowInEasing)
    )
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    val horizontalAlignment = if (line.oppositeAligned) Alignment.End else Alignment.Start
    val textAlign = if (line.oppositeAligned) TextAlign.End else TextAlign.Start
    val transformOrigin = TransformOrigin(if (line.oppositeAligned) 1f else 0f, 0.5f)

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
                this.transformOrigin = transformOrigin
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) accentColor.copy(alpha = 0.08f) else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (lyricsType == SpicyLyricsType.SYLLABLE && line.syllables.isNotEmpty()) {
            SyllableSweepLine(
                syllables = line.syllables,
                isLineActive = isActive,
                isLinePast = !isStatic && activeIndex >= 0 && index < activeIndex,
                currentTimeMs = currentTimeMs,
                oppositeAligned = line.oppositeAligned,
                onBackgroundColor = onBackgroundColor,
                accentColor = accentColor,
                fontSize = 22.sp,
                lineHeight = 30.sp
            )
        } else {
            val lineColor by animateColorAsState(
                targetValue = when {
                    isStatic -> onBackgroundColor
                    isActive -> lerp(onBackgroundColor, accentColor, 0.2f)
                    else -> onBackgroundColor
                },
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            )
            val shadow = if (isActive) {
                Shadow(
                    color = accentColor.copy(alpha = 0.45f),
                    offset = Offset.Zero,
                    blurRadius = 14f
                )
            } else {
                null
            }
            Text(
                text = line.text.ifBlank { "♪" },
                style = TextStyle(
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = lineColor,
                    textAlign = textAlign,
                    shadow = shadow
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Optional romanized sub-line
        if (showRomanized && !line.romanizedText.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = line.romanizedText!!,
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    color = onBackgroundColor.copy(alpha = if (isActive) 0.78f else 0.58f),
                    textAlign = textAlign
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Optional background vocals sub-lines
        if (line.backgroundVocals.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                line.backgroundVocals.forEach { bgLine ->
                    if (lyricsType == SpicyLyricsType.SYLLABLE && bgLine.syllables.isNotEmpty()) {
                        val bgActive = currentTimeMs in bgLine.startTimeMs..bgLine.endTimeMs
                        SyllableSweepLine(
                            syllables = bgLine.syllables,
                            isLineActive = bgActive || isActive,
                            isLinePast = currentTimeMs > bgLine.endTimeMs,
                            currentTimeMs = currentTimeMs,
                            oppositeAligned = line.oppositeAligned,
                            onBackgroundColor = onBackgroundColor.copy(alpha = 0.75f),
                            accentColor = accentColor,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            baseWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = bgLine.text,
                            style = TextStyle(
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.Medium,
                                color = onBackgroundColor.copy(alpha = 0.62f),
                                textAlign = textAlign
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (showRomanized && !bgLine.romanizedText.isNullOrBlank()) {
                        Text(
                            text = bgLine.romanizedText!!,
                            style = TextStyle(
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontStyle = FontStyle.Italic,
                                color = onBackgroundColor.copy(alpha = 0.50f),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyllableSweepLine(
    syllables: List<SpicySyllable>,
    isLineActive: Boolean,
    isLinePast: Boolean,
    currentTimeMs: Long,
    oppositeAligned: Boolean,
    onBackgroundColor: Color,
    accentColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    baseWeight: FontWeight = FontWeight.Bold
) {
    // Group syllables into words so split words have zero extra space and never wrap mid-word.
    val words = remember(syllables) {
        buildList {
            var currentWord = mutableListOf<SpicySyllable>()
            for (syl in syllables) {
                currentWord.add(syl)
                if (!syl.isPartOfWord) {
                    add(currentWord.toList())
                    currentWord = mutableListOf()
                }
            }
            if (currentWord.isNotEmpty()) {
                add(currentWord.toList())
            }
        }
    }

    val density = LocalDensity.current
    val liftPx = with(density) { (-2.5).dp.toPx() }
    val litColor = onBackgroundColor
    val unlitColor = onBackgroundColor.copy(alpha = if (isLineActive) 0.36f else 0.72f)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 6.dp,
            alignment = if (oppositeAligned) Alignment.End else Alignment.Start
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        words.forEach { wordSyllables ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                wordSyllables.forEach { syl ->
                    val progress = when {
                        isLinePast -> 1f
                        !isLineActive -> 0f
                        currentTimeMs <= syl.startTimeMs -> 0f
                        currentTimeMs >= syl.endTimeMs -> 1f
                        syl.endTimeMs <= syl.startTimeMs -> 1f
                        else -> ((currentTimeMs - syl.startTimeMs).toFloat() /
                            (syl.endTimeMs - syl.startTimeMs).toFloat()).coerceIn(0f, 1f)
                    }

                    val isSyllableSinging = isLineActive && progress > 0f && progress < 1f
                    val animatedLift by animateFloatAsState(
                        targetValue = if (isSyllableSinging) liftPx else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )

                    val glowShadow = if (isSyllableSinging) {
                        Shadow(
                            color = accentColor.copy(alpha = 0.65f),
                            offset = Offset.Zero,
                            blurRadius = 16f
                        )
                    } else if (isLineActive && progress >= 1f) {
                        Shadow(
                            color = accentColor.copy(alpha = 0.25f),
                            offset = Offset.Zero,
                            blurRadius = 8f
                        )
                    } else {
                        null
                    }

                    val textStyle = if (progress > 0.001f && progress < 0.999f) {
                        val edge = 0.08f
                        val startFade = (progress - edge).coerceIn(0f, 1f)
                        val endFade = (progress + edge).coerceIn(0f, 1f)
                        TextStyle(
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            fontWeight = baseWeight,
                            brush = Brush.horizontalGradient(
                                0f to litColor,
                                startFade to litColor,
                                endFade to unlitColor,
                                1f to unlitColor
                            ),
                            shadow = glowShadow
                        )
                    } else {
                        TextStyle(
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            fontWeight = baseWeight,
                            color = if (progress >= 0.999f) litColor else unlitColor,
                            shadow = glowShadow
                        )
                    }

                    Text(
                        text = syl.text,
                        style = textStyle,
                        modifier = Modifier.graphicsLayer {
                            translationY = animatedLift
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InterludeDotsRow(
    accentColor: Color,
    onBackgroundColor: Color,
    oppositeAligned: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = if (oppositeAligned) Alignment.End else Alignment.Start
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { dotIndex ->
            val distance = abs(phase - dotIndex)
            val pulse = (1f - (distance / 1.4f)).coerceIn(0f, 1f)
            val dotColor = lerp(onBackgroundColor.copy(alpha = 0.30f), accentColor, pulse * 0.85f)
            val scale = 0.82f + (0.34f * pulse)

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.45f + (0.55f * pulse)
                    }
                    .clip(CircleShape)
                    .background(dotColor)
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

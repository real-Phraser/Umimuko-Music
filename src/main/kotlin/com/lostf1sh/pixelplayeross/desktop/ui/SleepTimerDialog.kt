package com.lostf1sh.pixelplayeross.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lostf1sh.pixelplayeross.desktop.Glyph
import com.lostf1sh.pixelplayeross.desktop.system.SleepTimer
import com.lostf1sh.pixelplayeross.desktop.system.SleepTimerState
import com.lostf1sh.pixelplayeross.desktop.system.SleepTimerTarget

@Composable
fun SleepTimerDialog(
    sleepTimer: SleepTimer,
    onDismissRequest: () -> Unit
) {
    val state by sleepTimer.state.collectAsState()

    var selectedPresetMinutes by remember { mutableStateOf<Int?>(30) }
    var isEndOfTrack by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf(30f) }
    var fadeoutEnabled by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 420.dp, max = 480.dp)
                .wrapContentHeight()
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Glyph("timer", MaterialTheme.colorScheme.onPrimaryContainer, Modifier.size(20.dp))
                            }
                        }
                        Text(
                            text = "Sleep Timer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismissRequest) {
                        Glyph("close", MaterialTheme.colorScheme.onSurface)
                    }
                }

                HorizontalDivider()

                if (state.isActive) {
                    // Active State View
                    ActiveTimerView(
                        state = state,
                        onExtend = { sleepTimer.extend(it) },
                        onCancel = {
                            sleepTimer.cancel()
                        }
                    )
                } else {
                    // Setup Timer View
                    SetupTimerView(
                        selectedPresetMinutes = selectedPresetMinutes,
                        isEndOfTrack = isEndOfTrack,
                        customMinutes = customMinutes,
                        fadeoutEnabled = fadeoutEnabled,
                        onSelectPreset = { mins ->
                            selectedPresetMinutes = mins
                            isEndOfTrack = false
                            if (mins != null) customMinutes = mins.toFloat()
                        },
                        onSelectEndOfTrack = {
                            isEndOfTrack = true
                            selectedPresetMinutes = null
                        },
                        onCustomMinutesChange = {
                            customMinutes = it
                            selectedPresetMinutes = null
                            isEndOfTrack = false
                        },
                        onFadeoutToggle = { fadeoutEnabled = it },
                        onStart = {
                            val target = if (isEndOfTrack) {
                                SleepTimerTarget.EndOfTrack
                            } else {
                                val mins = selectedPresetMinutes ?: customMinutes.toInt()
                                SleepTimerTarget.Duration(mins)
                            }
                            sleepTimer.start(target, fadeout = fadeoutEnabled)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveTimerView(
    state: SleepTimerState,
    onExtend: (Int) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Large countdown display
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(130.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = SleepTimer.formatRemaining(state.remainingSeconds),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (state.isFading) "Fading Out..." else "Remaining",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.isFading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.fadeoutEnabled) {
            Text(
                text = if (state.isFading) "Playback volume is fading to 0" else "Volume smoothly fades out during final 30s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Quick extensions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { onExtend(5) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+5 min", style = MaterialTheme.typography.labelMedium)
            }
            FilledTonalButton(
                onClick = { onExtend(15) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+15 min", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Cancel
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Cancel Timer", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SetupTimerView(
    selectedPresetMinutes: Int?,
    isEndOfTrack: Boolean,
    customMinutes: Float,
    fadeoutEnabled: Boolean,
    onSelectPreset: (Int?) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onCustomMinutesChange: (Float) -> Unit,
    onFadeoutToggle: (Boolean) -> Unit,
    onStart: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "PRESETS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Preset Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(15, 30, 45, 60).forEach { mins ->
                val selected = selectedPresetMinutes == mins && !isEndOfTrack
                FilterChip(
                    selected = selected,
                    onClick = { onSelectPreset(mins) },
                    modifier = Modifier.height(FilterChipDefaults.Height),
                    label = {
                        Text(
                            text = "${mins}m",
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }
            FilterChip(
                selected = isEndOfTrack,
                onClick = onSelectEndOfTrack,
                modifier = Modifier.height(FilterChipDefaults.Height),
                label = {
                    Text(
                        text = "End of Track",
                        maxLines = 1,
                        softWrap = false
                    )
                },
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Custom Slider
        if (!isEndOfTrack) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Duration", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${customMinutes.toInt()} minutes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = customMinutes,
                    onValueChange = onCustomMinutesChange,
                    valueRange = 5f..120f,
                    steps = 22
                )
            }
        }

        HorizontalDivider()

        // Fadeout switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Smooth 30s Volume Fade-Out", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("Gently dissolves audio before stopping", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = fadeoutEnabled,
                onCheckedChange = onFadeoutToggle
            )
        }

        Spacer(Modifier.height(4.dp))

        // Start Button
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Start Sleep Timer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

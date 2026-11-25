package com.lostf1sh.pixelplayeross.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lostf1sh.pixelplayeross.desktop.dsp.CrossfadeCurve
import com.lostf1sh.pixelplayeross.desktop.dsp.EqualizerDefaults
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import kotlin.math.roundToInt

@Composable
fun EqualizerDialog(
    audio: AudioController,
    onDismissRequest: () -> Unit
) {
    val settings by audio.audioSettings.collectAsState()
    val eq = settings.equalizer
    val crossfade = settings.crossfade
    val rate = settings.playbackRate

    var savePresetDialogOpen by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var presetDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 680.dp, max = 840.dp)
                .fillMaxHeight(0.9f)
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Audio DSP & Equalizer",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "10-band hardware EQ, playback speed, and crossfade engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (eq.enabled) "EQ On" else "EQ Off",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (eq.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = eq.enabled,
                            onCheckedChange = { audio.setEqualizerEnabled(it) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Presets bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Preset:", style = MaterialTheme.typography.labelLarge)

                    Box {
                        OutlinedButton(
                            onClick = { presetDropdownExpanded = true },
                            enabled = eq.enabled
                        ) {
                            Text(eq.activePreset ?: "Custom")
                        }

                        DropdownMenu(
                            expanded = presetDropdownExpanded,
                            onDismissRequest = { presetDropdownExpanded = false }
                        ) {
                            val allPresets = audio.getAllPresets()
                            allPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = preset.name + if (preset.isBuiltIn) " (Built-in)" else " (Custom)",
                                            fontWeight = if (preset.name == eq.activePreset) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        audio.applyPreset(preset)
                                        presetDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            newPresetName = ""
                            savePresetDialogOpen = true
                        },
                        enabled = eq.enabled
                    ) {
                        Text("Save Preset")
                    }

                    if (eq.activePreset != null && !EqualizerDefaults.BUILT_IN_PRESETS.any { it.name.equals(eq.activePreset, ignoreCase = true) }) {
                        OutlinedButton(
                            onClick = {
                                eq.activePreset?.let { audio.deletePreset(it) }
                                audio.applyPreset(EqualizerDefaults.BUILT_IN_PRESETS.first())
                            },
                            enabled = eq.enabled,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete Preset")
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 10-Band Sliders
                Text(
                    text = "Frequency Bands (-12dB to +12dB)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(12.dp))

                // Bands container
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preamp slider
                    BandSlider(
                        label = "Preamp",
                        value = eq.preamp,
                        range = EqualizerDefaults.MIN_PREAMP_DB..EqualizerDefaults.MAX_PREAMP_DB,
                        enabled = eq.enabled,
                        onValueChange = { audio.setEqualizerPreamp(it) }
                    )

                    VerticalDivider(modifier = Modifier.height(180.dp).padding(horizontal = 4.dp))

                    // 10 bands
                    EqualizerDefaults.BAND_LABELS.forEachIndexed { index, label ->
                        val gain = eq.bands.getOrElse(index) { 0.0f }
                        BandSlider(
                            label = label,
                            value = gain,
                            range = EqualizerDefaults.MIN_GAIN_DB..EqualizerDefaults.MAX_GAIN_DB,
                            enabled = eq.enabled,
                            onValueChange = { audio.setEqualizerBandGain(index, it) }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Playback Speed / Rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Playback Speed: ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "%.2fx".format(rate),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = rate,
                            onValueChange = { audio.setPlaybackRate(it) },
                            valueRange = EqualizerDefaults.MIN_RATE..EqualizerDefaults.MAX_RATE,
                            steps = 14
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(onClick = { audio.setPlaybackRate(1.0f) }) {
                        Text("Reset 1.0x")
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Crossfade & Gapless
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Crossfade Duration: ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (crossfade.isGapless) "Gapless (0s)" else "%.1fs".format(crossfade.durationSeconds),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Curve: ", style = MaterialTheme.typography.labelMedium)
                            TransitionCurveChips(
                                selectedCurve = crossfade.curve,
                                onSelectCurve = { audio.setCrossfadeCurve(it) }
                            )
                        }
                    }

                    Slider(
                        value = crossfade.durationSeconds,
                        onValueChange = { audio.setCrossfadeDuration(it) },
                        valueRange = 0.0f..EqualizerDefaults.MAX_CROSSFADE_SECONDS,
                        steps = 23
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Close Button
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    FilledTonalButton(onClick = onDismissRequest) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (savePresetDialogOpen) {
        AlertDialog(
            onDismissRequest = { savePresetDialogOpen = false },
            title = { Text("Save Equalizer Preset") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            audio.saveCurrentPreset(newPresetName.trim())
                            savePresetDialogOpen = false
                        }
                    },
                    enabled = newPresetName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { savePresetDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BandSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(52.dp)
    ) {
        val rounded = (value * 10).roundToInt() / 10f
        Text(
            text = "%+.1f".format(rounded),
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Box(
            modifier = Modifier
                .height(130.dp)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                enabled = enabled,
                modifier = Modifier
                    .width(130.dp)
                    .rotateSlider()
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

private fun Modifier.rotateSlider(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        // Swap width & height to rotate slider 270 degrees vertically
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.placeRelativeWithLayer(0, 0) {
                rotationZ = 270f
                translationX = (placeable.height - placeable.width) / 2f
                translationY = (placeable.width - placeable.height) / 2f
            }
        }
    }
)

/**
 * Clipped pill chips for selecting the crossfade transition curve without Material3 Desktop
 * FilterChip's unclipped inner label hover artifact.
 */
@Composable
internal fun TransitionCurveChips(
    selectedCurve: CrossfadeCurve,
    onSelectCurve: (CrossfadeCurve) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransitionCurveChip(
            label = "Equal-Power",
            selected = selectedCurve == CrossfadeCurve.EQUAL_POWER,
            onClick = { onSelectCurve(CrossfadeCurve.EQUAL_POWER) }
        )
        TransitionCurveChip(
            label = "Linear",
            selected = selectedCurve == CrossfadeCurve.LINEAR,
            onClick = { onSelectCurve(CrossfadeCurve.LINEAR) }
        )
    }
}

@Composable
private fun TransitionCurveChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(32.dp).semantics { this.selected = selected },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

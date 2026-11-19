package com.lostf1sh.pixelplayeross.desktop.ui

import androidx.compose.foundation.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lostf1sh.pixelplayeross.desktop.AppIcon
import com.lostf1sh.pixelplayeross.desktop.Glyph
import com.lostf1sh.pixelplayeross.desktop.StartupPrefs
import com.lostf1sh.pixelplayeross.desktop.dsp.CrossfadeCurve
import com.lostf1sh.pixelplayeross.desktop.dsp.EqualizerDefaults
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import com.lostf1sh.pixelplayeross.desktop.stats.ListeningStatsStore
import com.lostf1sh.pixelplayeross.desktop.system.KeybindingManager
import com.lostf1sh.pixelplayeross.desktop.theme.ThemeManager
import com.lostf1sh.pixelplayeross.desktop.theme.ThemePreset

enum class SettingsTab(val title: String, val icon: String) {
    AUDIO_ENGINE("Audio Engine", "levels"),
    LIBRARY("Library & Folders", "folder"),
    APPEARANCE("Appearance & System", "settings"),
    KEYBINDINGS("Keybindings", "keyboard"),
    STATS("Listening Stats", "queue"),
    ABOUT("About & Credits", "info")
}

@Composable
fun SettingsDialog(
    audio: AudioController,
    onDismissRequest: () -> Unit,
    onOpenDuplicates: () -> Unit = {},
    folderRoots: List<String> = emptyList(),
    onRemoveFolder: (String) -> Unit = {},
    onAddFolder: () -> Unit = {},
    totalTracksCount: Int = 0,
    keybindingManager: KeybindingManager = remember { KeybindingManager() }
) {
    var activeTab by remember { mutableStateOf(SettingsTab.AUDIO_ENGINE) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 780.dp, max = 960.dp)
                .fillMaxHeight(0.88f)
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar Navigation
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "SETTINGS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    SettingsTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeTab = tab }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Glyph(
                                    tab.icon,
                                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = "Umimuko Music v0.25.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                VerticalDivider()

                // Tab Content Pane
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(28.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = activeTab.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismissRequest) {
                            Glyph("close", MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    // Body
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (activeTab) {
                            SettingsTab.AUDIO_ENGINE -> AudioEngineTab(audio)
                            SettingsTab.LIBRARY -> LibraryTabContent(
                                folderRoots = folderRoots,
                                onRemoveFolder = onRemoveFolder,
                                onAddFolder = onAddFolder,
                                onOpenDuplicates = {
                                    onDismissRequest()
                                    onOpenDuplicates()
                                },
                                totalTracksCount = totalTracksCount
                            )
                            SettingsTab.APPEARANCE -> AppearanceTab()
                            SettingsTab.KEYBINDINGS -> KeybindingsTab(keybindingManager)
                            SettingsTab.STATS -> StatsTab()
                            SettingsTab.ABOUT -> AboutTab()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioEngineTab(audio: AudioController) {
    val settings by audio.audioSettings.collectAsState()
    val eq = settings.equalizer
    val crossfade = settings.crossfade
    val rate = settings.playbackRate

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Equalizer Quick Control
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("10-Band Equalizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Active preset: ${eq.activePreset ?: "Custom"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = eq.enabled, onCheckedChange = { audio.setEqualizerEnabled(it) })
                }
            }
        }

        // Playback Rate
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Playback Speed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("%.2fx".format(rate), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = rate,
                    onValueChange = { audio.setPlaybackRate(it) },
                    valueRange = EqualizerDefaults.MIN_RATE..EqualizerDefaults.MAX_RATE,
                    steps = 14
                )
            }
        }

        // Crossfade & Gapless
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Crossfade & Gapless Transitions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (crossfade.isGapless) "Gapless Lookahead" else "%.1fs".format(crossfade.durationSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = crossfade.durationSeconds,
                    onValueChange = { audio.setCrossfadeDuration(it) },
                    valueRange = 0.0f..EqualizerDefaults.MAX_CROSSFADE_SECONDS,
                    steps = 23
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Transition curve:", style = MaterialTheme.typography.bodySmall)
                    FilterChip(
                        selected = crossfade.curve == CrossfadeCurve.EQUAL_POWER,
                        onClick = { audio.setCrossfadeCurve(CrossfadeCurve.EQUAL_POWER) },
                        label = { Text("Equal-Power") }
                    )
                    FilterChip(
                        selected = crossfade.curve == CrossfadeCurve.LINEAR,
                        onClick = { audio.setCrossfadeCurve(CrossfadeCurve.LINEAR) },
                        label = { Text("Linear") }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTabContent(
    folderRoots: List<String>,
    onRemoveFolder: (String) -> Unit,
    onAddFolder: () -> Unit,
    onOpenDuplicates: () -> Unit,
    totalTracksCount: Int
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Stats summary
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Indexed Audio Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$totalTracksCount total songs in library", style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = onOpenDuplicates) {
                    Glyph("search", LocalContentColor.current, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Find Duplicates")
                }
            }
        }

        // Folders list
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Monitored Music Folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(onClick = onAddFolder) {
                Glyph("add", LocalContentColor.current, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Folder")
            }
        }

        if (folderRoots.isEmpty()) {
            Text("No folders configured. Click 'Add Folder' to add your music.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            folderRoots.forEach { folder ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(folder, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        IconButton(onClick = { onRemoveFolder(folder) }) {
                            Glyph("remove", MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceTab() {
    val currentPreset by ThemeManager.currentPreset.collectAsState()
    val followSystem by StartupPrefs.followSystemThemeFlow.collectAsState()
    var introEnabled by remember { mutableStateOf(StartupPrefs.introEnabled) }
    var minimizeToTray by remember { mutableStateOf(StartupPrefs.minimizeToTray) }
    var closeToTray by remember { mutableStateOf(StartupPrefs.closeToTray) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Curated Color Themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Palette Cards Grid (2 columns)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val presets = ThemePreset.entries
            for (i in presets.indices step 2) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val p1 = presets[i]
                    ThemeCard(
                        preset = p1,
                        isSelected = p1 == currentPreset,
                        onSelect = { ThemeManager.setPreset(p1) },
                        modifier = Modifier.weight(1f)
                    )
                    if (i + 1 < presets.size) {
                        val p2 = presets[i + 1]
                        ThemeCard(
                            preset = p2,
                            isSelected = p2 == currentPreset,
                            onSelect = { ThemeManager.setPreset(p2) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        // Additional Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Follow System Dark/Light Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("Adapts automatically to macOS/Windows system theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = followSystem,
                onCheckedChange = {
                    StartupPrefs.followSystemTheme = it
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Startup Intro Animation", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("Fluid greeting animation when the app launches", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = introEnabled,
                onCheckedChange = {
                    introEnabled = it
                    StartupPrefs.introEnabled = it
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Text("System Tray & Window", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Minimize to System Tray", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("Hides the window from dock/taskbar into the system tray when minimized", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = minimizeToTray,
                onCheckedChange = {
                    minimizeToTray = it
                    StartupPrefs.minimizeToTray = it
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Close to System Tray", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("Keeps the music player running in the background when the close button (X) is clicked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = closeToTray,
                onCheckedChange = {
                    closeToTray = it
                    StartupPrefs.closeToTray = it
                }
            )
        }
    }
}

@Composable
private fun ThemeCard(
    preset: ThemePreset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSelected) 0.6f else 0.25f),
        modifier = modifier.clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Preview Color Swatch
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(preset.previewBackground),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(preset.previewAccent)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                )
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StatsTab() {
    val store = remember { ListeningStatsStore.defaultStore() }
    val topTracks = remember { store.topPlayedTracks(5) }
    val topArtists = remember { store.topArtists(5) }
    val allStats = remember { store.load().values }

    val totalPlays = allStats.sumOf { it.playCount }
    val totalHours = allStats.sumOf { it.totalListenDurationMs } / 3600000.0

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Highlights Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Plays", style = MaterialTheme.typography.labelMedium)
                    Text("$totalPlays", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Time Listened", style = MaterialTheme.typography.labelMedium)
                    Text("%.1f hrs".format(totalHours), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Top Played Tracks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (topTracks.isEmpty()) {
            Text("No listening stats recorded yet. Play some tracks to see analytics!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            topTracks.forEachIndexed { index, track ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("#${index + 1}  ${track.title.ifBlank { "Unknown" }}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${track.playCount} plays", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Top Artists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        topArtists.forEachIndexed { index, artist ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("#${index + 1}  ${artist.artist}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${artist.totalPlayCount} plays", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun AboutTab() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        AppIcon(64.dp, tint = MaterialTheme.colorScheme.primary)
        Text("Umimuko Music", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Version 0.25.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(
            text = "An ultra-fast, local-first audio player built with Kotlin, JetBrains Compose Desktop, and LibVLC.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("Open Source Technologies", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("JetBrains Compose Multiplatform · VLCJ / LibVLC · JAudioTagger · Skiko", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))

        Text("Crafted with care by Phraser & Contributors", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

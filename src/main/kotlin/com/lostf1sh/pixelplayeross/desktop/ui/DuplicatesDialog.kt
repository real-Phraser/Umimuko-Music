package com.lostf1sh.pixelplayeross.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import com.lostf1sh.pixelplayeross.desktop.library.DuplicateDetector
import com.lostf1sh.pixelplayeross.desktop.library.DuplicateGroup
import com.lostf1sh.pixelplayeross.desktop.playback.AudioController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DuplicatesDialog(
    songs: List<DesktopSong>,
    onDismissRequest: () -> Unit,
    onDuplicatesDeleted: (Set<String>) -> Unit,
    audio: AudioController? = null
) {
    var isScanning by remember { mutableStateOf(false) }
    var scanCompleted by remember { mutableStateOf(false) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    val deletedSongIds = remember { mutableStateListOf<String>() }
    var activeTrackAlert by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val currentPlayingSong = audio?.state?.collectAsState()?.value?.song

    fun isCurrentlyPlaying(candidate: DesktopSong): Boolean {
        val cur = currentPlayingSong ?: return false
        return candidate.id == cur.id || candidate.path == cur.path
    }

    Dialog(
        onDismissRequest = {
            if (deletedSongIds.isNotEmpty()) {
                onDuplicatesDeleted(deletedSongIds.toSet())
            }
            onDismissRequest()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 680.dp, max = 840.dp)
                .fillMaxHeight(0.88f)
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Duplicate Track Detection",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Multi-tier scan: SHA-256 binary hash, duration heuristics, and normalized metadata",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isScanning = true
                                scanCompleted = false
                                val found = withContext(Dispatchers.IO) {
                                    DuplicateDetector.findDuplicates(songs)
                                }
                                duplicateGroups = found
                                isScanning = false
                                scanCompleted = true
                            }
                        },
                        enabled = !isScanning && songs.size >= 2
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (scanCompleted) "Rescan" else "Scan Library")
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                if (isScanning) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Scanning library for duplicate tracks...")
                        }
                    }
                } else if (!scanCompleted) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Library has ${songs.size} tracks.",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Click 'Scan Library' above to analyze for duplicates.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (duplicateGroups.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No duplicates found! Your library is clean.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // Results list
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Found ${duplicateGroups.size} duplicate group(s)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        TextButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val toDelete = duplicateGroups.flatMap { it.duplicateCandidates }
                                    var skippedCount = 0
                                    val deletedBatch = mutableListOf<String>()
                                    toDelete.forEach { candidate ->
                                        if (isCurrentlyPlaying(candidate)) {
                                            skippedCount++
                                            return@forEach
                                        }
                                        if (DuplicateDetector.deleteTrackFile(candidate)) {
                                            deletedSongIds.add(candidate.id)
                                            deletedBatch.add(candidate.id)
                                        }
                                    }
                                    if (deletedBatch.isNotEmpty()) {
                                        audio?.evictSongs(deletedBatch.toSet())
                                    }
                                    if (skippedCount > 0) {
                                        activeTrackAlert = "Skipped deleting $skippedCount duplicate file(s) currently being played."
                                    }
                                    duplicateGroups = duplicateGroups.mapNotNull { g ->
                                        val remaining = g.duplicateCandidates.filter { it.id !in deletedSongIds }
                                        if (remaining.isEmpty()) null else g.copy(duplicateCandidates = remaining)
                                    }
                                }
                            }
                        ) {
                            Text("Resolve All (Keep Best Quality)")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(duplicateGroups) { group ->
                            DuplicateGroupCard(
                                group = group,
                                deletedIds = deletedSongIds.toSet(),
                                isPlayingSong = ::isCurrentlyPlaying,
                                onDeleteCandidate = { candidate ->
                                    if (isCurrentlyPlaying(candidate)) {
                                        activeTrackAlert = "Cannot delete '${candidate.title}' because it is currently playing."
                                    } else {
                                        scope.launch(Dispatchers.IO) {
                                            if (DuplicateDetector.deleteTrackFile(candidate)) {
                                                deletedSongIds.add(candidate.id)
                                                audio?.evictSongs(setOf(candidate.id))
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Footer
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(onClick = {
                        if (deletedSongIds.isNotEmpty()) {
                            onDuplicatesDeleted(deletedSongIds.toSet())
                        }
                        onDismissRequest()
                    }) {
                        Text("Done")
                    }
                }
            }
        }

        if (activeTrackAlert != null) {
            AlertDialog(
                onDismissRequest = { activeTrackAlert = null },
                title = { Text("Active Track Protection") },
                text = { Text(activeTrackAlert ?: "") },
                confirmButton = {
                    TextButton(onClick = { activeTrackAlert = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    deletedIds: Set<String>,
    isPlayingSong: (DesktopSong) -> Boolean = { false },
    onDeleteCandidate: (DesktopSong) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = group.recommendedKeeper.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = group.matchReason.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // Recommended keeper
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "KEEP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${group.recommendedKeeper.artist} · ${group.recommendedKeeper.format} · ${formatSize(group.recommendedKeeper.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = group.recommendedKeeper.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Duplicates
            group.duplicateCandidates.forEach { candidate ->
                val isDeleted = candidate.id in deletedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${candidate.artist} · ${candidate.format} · ${formatSize(candidate.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDeleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = candidate.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDeleted) 0.4f else 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    if (isDeleted) {
                        Text(
                            text = "Deleted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (isPlayingSong(candidate)) {
                        Text(
                            text = "Playing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        OutlinedButton(
                            onClick = { onDeleteCandidate(candidate) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Delete File", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%.0f KB".format(kb)
}

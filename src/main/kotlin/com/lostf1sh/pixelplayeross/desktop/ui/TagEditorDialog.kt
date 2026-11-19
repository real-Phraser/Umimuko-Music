package com.lostf1sh.pixelplayeross.desktop.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lostf1sh.pixelplayeross.desktop.Artwork
import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import com.lostf1sh.pixelplayeross.desktop.metadata.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

@Composable
fun TagEditorDialog(
    song: DesktopSong,
    onDismissRequest: () -> Unit,
    onSongUpdated: (DesktopSong) -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var albumArtist by remember { mutableStateOf(song.albumArtist) }
    var yearStr by remember { mutableStateOf(if (song.year > 0) song.year.toString() else "") }
    var genre by remember { mutableStateOf(song.genre) }
    var trackNumStr by remember { mutableStateOf(if (song.trackNumber > 0) song.trackNumber.toString() else "") }
    var discNumStr by remember { mutableStateOf(if (song.discNumber > 0) song.discNumber.toString() else "") }
    var lyrics by remember { mutableStateOf(song.lyrics) }

    var currentCoverPath by remember { mutableStateOf(song.coverPath) }
    var fetchedCoverBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isFetchingCover by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 600.dp, max = 760.dp)
                .fillMaxHeight(0.92f)
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Edit Track Tags",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = song.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = song.format,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                HorizontalDivider()

                // Cover Art & Fetch Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(modifier = Modifier.size(100.dp)) {
                        Artwork(size = 100.dp, active = true, coverPath = currentCoverPath)
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Cover Artwork",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isFetchingCover = true
                                        statusMessage = "Searching MusicBrainz & Cover Art Archive..."
                                        isErrorMessage = false
                                        val bytes = withContext(Dispatchers.IO) {
                                            CoverArtDownloader.searchAndDownloadCover(artist, album, title)
                                        }
                                        if (bytes != null) {
                                            fetchedCoverBytes = bytes
                                            val cached = CoverArtDownloader.cacheCoverImage(bytes, "${artist}_${album}_${System.currentTimeMillis()}")
                                            if (cached != null) {
                                                currentCoverPath = cached.toString()
                                            }
                                            statusMessage = "High-res artwork downloaded from Cover Art Archive!"
                                            isErrorMessage = false
                                        } else {
                                            statusMessage = "No cover art found on MusicBrainz for this track."
                                            isErrorMessage = true
                                        }
                                        isFetchingCover = false
                                    }
                                },
                                enabled = !isFetchingCover && !isSaving
                            ) {
                                if (isFetchingCover) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Auto-Fetch Artwork")
                            }
                        }
                    }
                }

                // Status banner
                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isErrorMessage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider()

                // Form Fields
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("Artist") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = albumArtist,
                        onValueChange = { albumArtist = it },
                        label = { Text("Album Artist") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = yearStr,
                        onValueChange = { yearStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Release Year") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Genre") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = trackNumStr,
                        onValueChange = { trackNumStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Track #") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = discNumStr,
                        onValueChange = { discNumStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Disc #") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = { Text("Lyrics (Plain or LRC)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 160.dp),
                    maxLines = 6
                )

                Spacer(Modifier.height(8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest, enabled = !isSaving) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                statusMessage = "Writing ID3 / metadata tags safely..."
                                isErrorMessage = false

                                val updated = withContext(Dispatchers.IO) {
                                    val tags = EditableTags(
                                        title = title.trim(),
                                        artist = artist.trim(),
                                        album = album.trim(),
                                        albumArtist = albumArtist.trim(),
                                        year = yearStr.toIntOrNull() ?: 0,
                                        genre = genre.trim(),
                                        trackNumber = trackNumStr.toIntOrNull() ?: 0,
                                        discNumber = discNumStr.toIntOrNull() ?: 0,
                                        lyrics = lyrics.trim()
                                    )
                                    val path = Path.of(song.path)
                                    val result = TrackTagService.writeTags(path, tags)

                                    if (result is TagWriteResult.Success) {
                                        fetchedCoverBytes?.let { bytes ->
                                            TrackTagService.embedArtwork(path, bytes)
                                            val parentFolder = path.parent
                                            if (parentFolder != null) {
                                                CoverArtDownloader.saveAsFolderCover(parentFolder, bytes)
                                            }
                                        }

                                        song.copy(
                                            title = tags.title,
                                            artist = tags.artist,
                                            album = tags.album,
                                            albumArtist = tags.albumArtist,
                                            year = tags.year,
                                            genre = tags.genre,
                                            trackNumber = tags.trackNumber,
                                            discNumber = tags.discNumber,
                                            lyrics = tags.lyrics,
                                            coverPath = currentCoverPath
                                        )
                                    } else {
                                        null
                                    }
                                }

                                if (updated != null) {
                                    onSongUpdated(updated)
                                    onDismissRequest()
                                } else {
                                    statusMessage = "Failed writing tags to file. Please check file permissions."
                                    isErrorMessage = true
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving && title.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

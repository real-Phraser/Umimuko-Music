package com.lostf1sh.pixelplayeross.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lostf1sh.pixelplayeross.desktop.library.DesktopSong
import com.lostf1sh.pixelplayeross.desktop.lyrics.SpicyLyrics
import java.io.File
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
internal fun LyricsCardDialog(
    song: DesktopSong,
    lyrics: SpicyLyrics,
    onDismiss: () -> Unit
) {
    val selectedLines = remember(lyrics) { mutableStateListOf<Int>() }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val selectedText = selectedLines.sorted().mapNotNull { lyrics.lines.getOrNull(it)?.text }
        .filter(String::isNotBlank)
        .joinToString("\n")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Select lyrics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${song.title} · ${song.artist}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Choose the lines to include in your card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val checked = index in selectedLines
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedLines.remove(index) else selectedLines.add(index)
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selected ->
                                    if (selected) selectedLines.add(index) else selectedLines.remove(index)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                line.text,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(64.dp, active = true, coverPath = song.coverPath)
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(
                                selectedText.ifBlank { "Selected lyrics will appear here" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                statusMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("Saved to")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = selectedText.isNotBlank(),
                        onClick = {
                            val chooser = JFileChooser().apply {
                                dialogTitle = "Export lyric card"
                                fileFilter = FileNameExtensionFilter("PNG image", "png")
                                selectedFile = File("${safeFileName(song.title)}-lyric-card.png")
                            }
                            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                val chosen = chooser.selectedFile
                                val destination = if (chosen.extension.equals("png", ignoreCase = true)) {
                                    chosen
                                } else {
                                    File(chosen.parentFile, "${chosen.name}.png")
                                }
                                if (destination.exists() &&
                                    JOptionPane.showConfirmDialog(
                                        null,
                                        "Replace ${destination.name}?",
                                        "Confirm export",
                                        JOptionPane.YES_NO_OPTION,
                                        JOptionPane.WARNING_MESSAGE
                                    ) != JOptionPane.YES_OPTION
                                ) {
                                    return@Button
                                }
                                try {
                                    LyricsCardExporter.export(song, selectedText, destination)
                                    statusMessage = "Saved to ${destination.absolutePath}"
                                } catch (error: IOException) {
                                    statusMessage = "Could not export card: ${error.message ?: "I/O error"}"
                                } catch (error: SecurityException) {
                                    statusMessage = "Could not export card: ${error.message ?: "access denied"}"
                                } catch (error: IllegalArgumentException) {
                                    statusMessage = "Could not export card: ${error.message ?: "invalid selection"}"
                                }
                            }
                        }
                    ) {
                        Glyph("add", MaterialTheme.colorScheme.onPrimary, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export PNG")
                    }
                }
            }
        }
    }
}

private fun safeFileName(title: String): String =
    title.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_').ifBlank { "song" }

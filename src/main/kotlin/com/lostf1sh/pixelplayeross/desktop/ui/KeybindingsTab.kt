package com.lostf1sh.pixelplayeross.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.desktop.Glyph
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import com.lostf1sh.pixelplayeross.desktop.system.KeyCombination
import com.lostf1sh.pixelplayeross.desktop.system.KeybindingManager
import com.lostf1sh.pixelplayeross.desktop.system.ShortcutAction
import java.awt.event.KeyEvent as AwtKeyEvent

@Composable
fun KeybindingsTab(
    keybindingManager: KeybindingManager
) {
    val bindings by keybindingManager.bindings.collectAsState()
    var recordingAction by remember { mutableStateOf<ShortcutAction?>(null) }
    var conflictMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(recordingAction) {
        if (recordingAction != null) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Keyboard Shortcuts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Customize application hotkeys. Press Esc during recording to cancel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = {
                    keybindingManager.resetAll()
                    conflictMessage = null
                    recordingAction = null
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reset All to Defaults", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Conflict alert banner
        conflictMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    IconButton(
                        onClick = { conflictMessage = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Glyph("close", MaterialTheme.colorScheme.onErrorContainer, Modifier.size(16.dp))
                    }
                }
            }
        }

        // Group actions by category
        val categories = listOf("Playback", "Audio", "View")
        categories.forEach { category ->
            Text(
                text = category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val categoryActions = ShortcutAction.entries.filter { it.category == category }
                    categoryActions.forEachIndexed { index, action ->
                        val combo = bindings[action]
                        val isRecording = recordingAction == action

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isRecording) {
                                    // Recording state button
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .focusRequester(focusRequester)
                                            .focusable()
                                            .onPreviewKeyEvent { event ->
                                                if (event.type == KeyEventType.KeyDown) {
                                                    val code = KeyCombination.extractAwtKeyCode(event)
                                                    if (code == AwtKeyEvent.VK_ESCAPE) {
                                                        recordingAction = null
                                                        return@onPreviewKeyEvent true
                                                    }
                                                    if (KeybindingManager.isModifierOnly(code)) {
                                                        return@onPreviewKeyEvent true
                                                    }
                                                    val newCombo = KeyCombination.fromEvent(event)
                                                    val conflict = keybindingManager.findConflict(newCombo, excludingAction = action)
                                                    if (conflict != null) {
                                                        conflictMessage = "⚠️ Shortcut replaced from '${conflict.title}'"
                                                    } else {
                                                        conflictMessage = null
                                                    }
                                                    keybindingManager.setBinding(action, newCombo)
                                                    recordingAction = null
                                                    true
                                                } else false
                                            }
                                    ) {
                                        Text(
                                            text = "Press keys... (Esc)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                } else {
                                    // Normal badge with rebind click
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .wrapContentSize()
                                    ) {
                                        Text(
                                            text = combo?.displayLabel() ?: "None",
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            conflictMessage = null
                                            recordingAction = action
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Rebind", style = MaterialTheme.typography.labelSmall)
                                    }

                                    IconButton(
                                        onClick = {
                                            keybindingManager.resetAction(action)
                                            conflictMessage = null
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Glyph("restore", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (index < categoryActions.size - 1) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

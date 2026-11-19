package com.lostf1sh.pixelplayeross.desktop.system

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import com.lostf1sh.pixelplayeross.desktop.library.PlatformPaths
import java.awt.event.KeyEvent as AwtKeyEvent

enum class ShortcutAction(val id: String, val title: String, val category: String) {
    PLAY_PAUSE("play_pause", "Play / Pause", "Playback"),
    NEXT_TRACK("next_track", "Next Track", "Playback"),
    PREV_TRACK("prev_track", "Previous Track", "Playback"),
    SEEK_FORWARD("seek_forward", "Seek Forward (+5s)", "Playback"),
    SEEK_BACKWARD("seek_backward", "Seek Backward (-5s)", "Playback"),
    VOLUME_UP("volume_up", "Volume Up (+5%)", "Audio"),
    VOLUME_DOWN("volume_down", "Volume Down (-5%)", "Audio"),
    MUTE("mute", "Mute / Unmute", "Audio"),
    TOGGLE_FULLSCREEN("toggle_fullscreen", "Toggle Full Screen Window", "View"),
    TOGGLE_FULL_PLAYER("toggle_full_player", "Toggle Fullscreen Player", "View"),
    TOGGLE_LYRICS("toggle_lyrics", "Toggle Synced Lyrics", "View");

    companion object {
        fun fromId(id: String): ShortcutAction? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

data class KeyCombination(
    val awtKeyCode: Int,
    val keyText: String,
    val isCtrl: Boolean = false,
    val isMeta: Boolean = false,
    val isAlt: Boolean = false,
    val isShift: Boolean = false
) {
    fun matches(event: KeyEvent): Boolean {
        val code = extractAwtKeyCode(event)
        if (code != awtKeyCode) return false
        if (event.isCtrlPressed != isCtrl) return false
        if (event.isMetaPressed != isMeta) return false
        if (event.isAltPressed != isAlt) return false
        if (event.isShiftPressed != isShift) return false
        return true
    }

    fun hasModifiers(): Boolean = isCtrl || isMeta || isAlt || isShift

    fun displayLabel(isMac: Boolean = PlatformPaths.isMac()): String {
        val parts = mutableListOf<String>()
        if (isMac) {
            if (isCtrl) parts.add("⌃")
            if (isAlt) parts.add("⌥")
            if (isShift) parts.add("⇧")
            if (isMeta) parts.add("⌘")
        } else {
            if (isCtrl) parts.add("Ctrl")
            if (isAlt) parts.add("Alt")
            if (isShift) parts.add("Shift")
            if (isMeta) parts.add("Win")
        }

        val symbol = when (awtKeyCode) {
            AwtKeyEvent.VK_SPACE -> if (isMac) "␣ Space" else "Space"
            AwtKeyEvent.VK_RIGHT -> "→"
            AwtKeyEvent.VK_LEFT -> "←"
            AwtKeyEvent.VK_UP -> "↑"
            AwtKeyEvent.VK_DOWN -> "↓"
            AwtKeyEvent.VK_ENTER -> "↵ Enter"
            AwtKeyEvent.VK_ESCAPE -> "Esc"
            else -> keyText.ifBlank { AwtKeyEvent.getKeyText(awtKeyCode) }
        }
        parts.add(symbol)
        return if (isMac) parts.joinToString(" ") else parts.joinToString(" + ")
    }

    companion object {
        fun extractAwtKeyCode(event: KeyEvent): Int {
            val native = event.nativeKeyEvent
            if (native is AwtKeyEvent) return native.keyCode
            return (event.key.keyCode and 0xFFFFFFFFL).toInt()
        }

        fun fromEvent(event: KeyEvent): KeyCombination {
            val code = extractAwtKeyCode(event)
            val text = AwtKeyEvent.getKeyText(code)
            return KeyCombination(
                awtKeyCode = code,
                keyText = text,
                isCtrl = event.isCtrlPressed,
                isMeta = event.isMetaPressed,
                isAlt = event.isAltPressed,
                isShift = event.isShiftPressed
            )
        }
    }
}
